package goldenshadow.displayentityeditor;

import goldenshadow.displayentityeditor.commands.Command;
import goldenshadow.displayentityeditor.commands.TabComplete;
import goldenshadow.displayentityeditor.events.*;
import goldenshadow.displayentityeditor.inventories.InventoryFactory;
import goldenshadow.displayentityeditor.items.GUIItems;
import goldenshadow.displayentityeditor.items.InventoryItems;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;


import java.util.*;
import java.util.function.Consumer;

public final class DisplayEntityEditor extends JavaPlugin {

    private static DisplayEntityEditor plugin;
    public static ConversationFactory conversationFactory;
    public static InventoryFactory inventoryFactory;
    public static HashMap<UUID, Display> currentEditMap = new HashMap<>();
    // 내장 설정: 채팅 입력, MiniMessage 사용, 내장 한국어 메시지 사용.
    public static final boolean alternateTextInput = false;
    public static final boolean useMiniMessageFormat = true;
    public static final boolean useMessagesFile = false;
    public static final boolean sendUpdateMessageOnJoin = false;
    public static MiniMessage miniMessage = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.font())
                    .resolver(StandardTags.newline())
                    .resolver(StandardTags.keybind())
                    .resolver(StandardTags.nbt())
                    .resolver(StandardTags.score())
                    .resolver(StandardTags.transition())
                    .build()
            )
            .build();
    public static MessageManager messageManager;

    public static NamespacedKey toolSelectionModeKey;
    public static NamespacedKey toolSelectionRangeKey;
    public static NamespacedKey toolSelectionMultipleKey;
    public static NamespacedKey toolSelectionSearchModeKey;
    public static NamespacedKey toolPrecisionKey;
    public static NamespacedKey toolKey;

    private EditingHandler editingHandler;

    @Override public void onLoad() {
        try { LegacyPluginData.migrate(getDataFolder().toPath(), "DisplayEntityEditor"); }
        catch (IOException failure) { throw new IllegalStateException("기존 디스플레이 플러그인 데이터 이전 실패", failure); }
    }

    /** Keep existing tool and entity tags readable after renaming the plugin. */
    public static NamespacedKey legacyKey(String key) {
        return Objects.requireNonNull(NamespacedKey.fromString("displayentityeditor:" + key.toLowerCase(Locale.ROOT)));
    }

    /**
     * Used for when the plugin starts up
     */
    @Override
    public void onEnable() {
        plugin = this;
        if (!Bukkit.getMinecraftVersion().equals("26.3")) {
            getLogger().severe("이 플러그인은 마인크래프트 26.3 전용입니다.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            checkForMessageFile();
        } catch (IOException | RuntimeException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "내장 메시지를 불러오지 못해 플러그인을 비활성화합니다.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.editingHandler = new EditingHandler();

        conversationFactory = new ConversationFactory(plugin);
        inventoryFactory = new InventoryFactory(new GUIItems(), new InventoryItems());
        Objects.requireNonNull(getCommand("디스플레이")).setExecutor(new Command());
        Objects.requireNonNull(getCommand("디스플레이")).setTabCompleter(new TabComplete());
        Bukkit.getPluginManager().registerEvents(new Interact(editingHandler), plugin);
        Bukkit.getPluginManager().registerEvents(new OffhandSwap(editingHandler), plugin);
        Bukkit.getPluginManager().registerEvents(new InventoryClick(), plugin);
        Bukkit.getPluginManager().registerEvents(new InventoryClose(), plugin);
        Bukkit.getPluginManager().registerEvents(new PlayerJoin(), plugin);
        Bukkit.getPluginManager().registerEvents(new PlayerLeave(), plugin);
        
        toolSelectionModeKey = legacyKey("toolSelectionMode");
        toolSelectionRangeKey = legacyKey("toolSelectionRange");
        toolSelectionMultipleKey = legacyKey("toolSelectionMultiple");
        toolSelectionSearchModeKey = legacyKey("toolSelectionSearchMode");
        toolPrecisionKey = legacyKey("toolPrecision");
        toolKey = legacyKey("tool");

        new Metrics(plugin, 18672);



        if (useMessagesFile) {
            if (!getDescription().getVersion().equals(messageManager.getString("file_version"))) {
                getLogger().warning(messageManager.getString("messages_file_outdated_version"));
            }
            if (!messageManager.isMessageMapComplete()) {
                getLogger().warning(messageManager.getString("messages_file_incomplete"));
            }
        }

    }
    
    @Override
    public void onDisable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Command.returnInventory(player);
        }
    }

    /**
     * Getter for the plugin instance
     * @return The plugin
     */
    public static DisplayEntityEditor getPlugin() {
        return plugin;
    }

    /**
     * Used to get the newest version of the plugin available on spigot
     * @param consumer The consumer
     */
    public static void getVersion(final Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=110267").openStream(); Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    consumer.accept(scanner.next());
                }
            } catch (IOException exception) {
                plugin.getLogger().warning(messageManager.getString("version_check_error").formatted(exception.getMessage()));
            }
        });
    }

    public static void checkForMessageFile() throws IOException {
        messageManager = new MessageManager();
    }

    public EditingHandler getEditingHandler() {
        return editingHandler;
    }
}
