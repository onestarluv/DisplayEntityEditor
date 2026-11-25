package goldenshadow.displayentityeditor.furniture;

import com.nexomc.nexo.api.NexoFurniture;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureBreakEvent;
import com.nexomc.nexo.api.events.furniture.NexoFurnitureInteractEvent;
import com.nexomc.nexo.mechanics.furniture.FurnitureMechanic;
import com.nexomc.nexo.mechanics.furniture.BlockLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nexo 선반 가구(TooManyFurniture tmf_*_shelving / _long_shelving / _modern_shelving / _long_modern_shelving)의
 * 보관함 1~3번 칸을 선반 칸에 아이템 디스플레이로 보여준다. 1번 칸은 맨 위 칸, 3번 칸은 맨 아래 칸.
 *
 * <p>위치는 모델 좌표(px)로 정하고, Nexo가 히트박스 오프셋을 회전시키는 방식 그대로 월드 좌표로 바꾼다
 * (모델 x는 Nexo 오프셋 -x, 모델 z는 +z — Nexo 기본 가구로 확인한 규칙).</p>
 *
 * <p>디스플레이는 가구 기준 엔티티와 같은 위치에 스폰하고 translation 으로 옮겨서, 항상 같은 청크에
 * 함께 저장·로드된다. PDC에 주인(기준 엔티티 UUID)을 기록한다.</p>
 */
public final class ShelfDisplays implements Listener {

    private static final Pattern SHELF_ID = Pattern.compile("tmf_[a-z_]+?_((?:long_)?(?:modern_)?)shelving");
    /** 칸 바닥 윗면 높이(px). 1번 칸이 맨 위. 일반·모던 선반 모두 같다. */
    private static final double[] SHELF_FLOOR_Y = {24, 16, 8};
    /** 선반 판의 앞뒤 중심(px). 판은 z 6~16(모던 6.5~15.5). */
    private static final double SHELF_CENTER_Z = 11;
    /** 칸 높이가 6px(0.375블록)라 그보다 작게. FIXED 표시는 아이템 중심 기준. */
    private static final float ITEM_SCALE = 0.32f;

    private final Plugin plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey slotKey;
    /** 선반 보관함을 열었을 플레이어 → 기준 엔티티 UUID. */
    private final Map<UUID, UUID> viewing = new HashMap<>();

    public ShelfDisplays(Plugin plugin) {
        this.plugin = plugin;
        // 키 이름은 첫 버전(bureau_*)과 같게 둬서 그때 만든 디스플레이도 정리되게 한다.
        this.ownerKey = new NamespacedKey(plugin, "bureau_owner");
        this.slotKey = new NamespacedKey(plugin, "bureau_slot");
    }

    /** 선반이면 모델 가로 중심 x(px), 아니면 NaN. 1칸 선반은 0~16(중심 8), 긴 선반은 -16~16(중심 0). */
    static double shelfCenterX(FurnitureMechanic mechanic) {
        if (mechanic == null || mechanic.getStorage() == null) return Double.NaN;
        Matcher m = SHELF_ID.matcher(mechanic.getItemID());
        if (!m.matches()) return Double.NaN;
        return m.group(1).startsWith("long_") ? 0 : 8;
    }

    static boolean isShelf(FurnitureMechanic mechanic) {
        return !Double.isNaN(shelfCenterX(mechanic));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(NexoFurnitureInteractEvent event) {
        if (!isShelf(event.getMechanic())) return;
        UUID base = event.getBaseEntity().getUniqueId();
        viewing.put(event.getPlayer().getUniqueId(), base);
        Bukkit.getScheduler().runTask(plugin, () -> refresh(base));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        UUID base = viewing.remove(event.getPlayer().getUniqueId());
        if (base == null) return;
        // Nexo가 닫을 때 보관함을 저장하므로 한 틱 뒤에 읽는다.
        Bukkit.getScheduler().runTask(plugin, () -> refresh(base));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        viewing.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(NexoFurnitureBreakEvent event) {
        removeDisplays(event.getBaseEntity());
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        List<ItemDisplay> ours = new ArrayList<>();
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ItemDisplay display && owner(display) != null) ours.add(display);
        }
        if (ours.isEmpty()) return;
        // 기준 엔티티도 같은 청크라 이번에 함께 로드된다. 한 틱 뒤 주인이 없거나 선반이 아니면 정리.
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ItemDisplay display : ours) {
                if (!display.isValid()) continue;
                UUID owner = owner(display);
                Entity base = owner == null ? null : Bukkit.getEntity(owner);
                if (!(base instanceof ItemDisplay baseDisplay) || !isShelf(NexoFurniture.furnitureMechanic(baseDisplay))) {
                    display.remove();
                }
            }
        });
    }

    /** 기준 엔티티의 현재 보관함 내용으로 디스플레이를 다시 만든다. */
    public void refresh(UUID baseId) {
        if (!(Bukkit.getEntity(baseId) instanceof ItemDisplay base) || !base.isValid()) return;
        FurnitureMechanic mechanic = NexoFurniture.furnitureMechanic(base);
        double centerX = shelfCenterX(mechanic);
        removeDisplays(base);
        if (Double.isNaN(centerX)) return;
        List<ItemStack> contents = mechanic.getStorage().storageContents(mechanic, base);

        Frame frame = Frame.of(base);
        for (int slot = 0; slot < SHELF_FLOOR_Y.length && slot < contents.size(); slot++) {
            ItemStack item = contents.get(slot);
            if (item == null || item.getType().isAir()) continue;
            double y = SHELF_FLOOR_Y[slot] + ITEM_SCALE * 8 + 0.2;
            spawn(base, frame, frame.offset(centerX, y, SHELF_CENTER_Z), item, slot);
        }
    }

    private void spawn(ItemDisplay base, Frame frame, Vector offset, ItemStack item, int slot) {
        Location at = base.getLocation();
        at.setYaw(0);
        at.setPitch(0);
        // 앞쪽(모델 -z)을 보게 Y축으로 돌린다. 엔티티 회전은 0이라 translation 은 월드 기준.
        Vector front = frame.ez.clone().multiply(-1);
        float facing = (float) Math.atan2(front.getX(), front.getZ());
        base.getWorld().spawn(at, ItemDisplay.class, display -> {
            ItemStack shown = item.clone();
            shown.setAmount(1);
            display.setItemStack(shown);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new Transformation(
                    new Vector3f((float) offset.getX(), (float) offset.getY(), (float) offset.getZ()),
                    new Quaternionf().rotationY(facing),
                    new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE),
                    new Quaternionf()));
            display.setPersistent(true);
            PersistentDataContainer pdc = display.getPersistentDataContainer();
            pdc.set(ownerKey, PersistentDataType.STRING, base.getUniqueId().toString());
            pdc.set(slotKey, PersistentDataType.INTEGER, slot);
        });
    }

    /**
     * 가구의 Nexo 오프셋 축(+x, +z)을 월드 방향으로 구한 것. 히트박스 오프셋을 돌릴 때 Nexo가 쓰는
     * BlockLocation.groundRotate(yaw)로 단위 오프셋을 직접 돌리므로 Nexo의 회전 규칙과 항상 같다.
     */
    private record Frame(Vector ex, Vector ez) {

        static Frame of(ItemDisplay base) {
            float yaw = base.getLocation().getYaw();
            return new Frame(axis(1, 0, yaw), axis(0, 1, yaw));
        }

        private static Vector axis(int x, int z, float yaw) {
            BlockLocation rotated = new BlockLocation(x, 0, z).groundRotate(yaw);
            return new Vector(rotated.getX(), 0, rotated.getZ());
        }

        /** 모델 좌표(px)의 점 → 기준 엔티티 위치(모델 8,8,8)에서의 월드 오프셋. */
        Vector offset(double mx, double my, double mz) {
            return ex.clone().multiply(-(mx - 8) / 16)
                    .add(ez.clone().multiply((mz - 8) / 16))
                    .add(new Vector(0, (my - 8) / 16, 0));
        }
    }

    private void removeDisplays(ItemDisplay base) {
        String id = base.getUniqueId().toString();
        for (ItemDisplay display : base.getLocation().getNearbyEntitiesByType(ItemDisplay.class, 1.0)) {
            if (id.equals(display.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) display.remove();
        }
    }

    private UUID owner(ItemDisplay display) {
        String raw = display.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
