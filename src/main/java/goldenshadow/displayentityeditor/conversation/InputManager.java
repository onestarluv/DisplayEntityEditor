package goldenshadow.displayentityeditor.conversation;

import goldenshadow.displayentityeditor.DisplayEntityEditor;
import goldenshadow.displayentityeditor.Utilities;
import goldenshadow.displayentityeditor.enums.InputType;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 값 입력을 Paper 다이얼로그로 받는다. 입력 칸에는 현재 값이 채워져 있고,
 * "확인"은 검증 후 적용(실패하면 오류 문구와 함께 다시 연다), "취소"는 그냥 닫는다.
 */
public class InputManager {

    private static final String KEY = "value";
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    /** 입력 값이 해석되는 방식. */
    private enum Kind { TEXT, INTEGER, BYTE, FLOAT }

    private static Kind kindOf(InputType type) {
        return switch (type) {
            case LINE_WIDTH -> Kind.INTEGER;
            case TEXT_OPACITY, BACKGROUND_OPACITY -> Kind.BYTE;
            case VIEW_RANGE, DISPLAY_HEIGHT, DISPLAY_WIDTH, SHADOW_RADIUS, SHADOW_STRENGTH -> Kind.FLOAT;
            case NAME, TEXT, TEXT_APPEND, BACKGROUND_COLOR, GLOW_COLOR, BLOCK_STATE -> Kind.TEXT;
        };
    }

    /**
     * 값 입력 다이얼로그를 연다.
     * @param player 입력할 플레이어
     * @param hint 다이얼로그 본문에 보일 안내 문구
     * @param data 편집 대상과 항목
     */
    public static void openInput(Player player, String hint, InputData data) {
        show(player, hint, data, initialValue(data), null);
    }

    private static void show(Player player, String hint, InputData data, String initial, @Nullable String error) {
        var once = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();
        ActionButton confirm = ActionButton.builder(Component.text("확인")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(() -> {
                    if (!player.isOnline()) return;
                    String raw = Objects.requireNonNullElse(response.getText(KEY), "");
                    submit(player, hint, data, raw);
                }), once)).build();
        ActionButton cancel = ActionButton.builder(Component.text("취소")).width(100)
                .action(DialogAction.customClick((response, audience) -> onMain(() -> {
                    if (player.isOnline()) player.closeDialog();
                }), once)).build();

        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(AMPERSAND.deserialize(hint), 300));
        if (error != null) body.add(DialogBody.plainMessage(Component.text(ChatColor.stripColor(error), NamedTextColor.RED), 300));

        boolean longText = data.inputType() == InputType.TEXT || data.inputType() == InputType.TEXT_APPEND;
        TextDialogInput.Builder input = DialogInput.text(KEY, Component.text(data.inputType().label()))
                .initial(initial)
                .maxLength(longText ? 4096 : 256)
                .width(300);
        if (longText) input.multiline(TextDialogInput.MultilineOptions.create(null, 100));

        player.showDialog(Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text(data.inputType().label() + " 설정"))
                        .canCloseWithEscape(true).pause(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(input.build()))
                        .build())
                .type(DialogType.confirmation(confirm, cancel))));
    }

    /** 검증 후 적용. 검증에 실패하면 입력한 값을 그대로 둔 채 다이얼로그를 다시 연다. */
    private static void submit(Player player, String hint, InputData data, String raw) {
        if (!data.entity().isValid()) {
            player.closeDialog();
            player.sendRawMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("input_entity_gone")));
            return;
        }
        String error = validate(data, raw);
        if (error != null) {
            show(player, hint, data, raw, error);
            return;
        }
        player.closeDialog();
        String value = raw.strip();
        switch (kindOf(data.inputType())) {
            case INTEGER -> successfulIntegerInput(data, Integer.parseInt(value), player);
            case BYTE -> successfulByteInput(data, Integer.parseInt(value), player);
            case FLOAT -> successfulFloatInput(data, Float.parseFloat(value), player);
            case TEXT -> successfulTextInput(data, raw, player);
        }
    }

    /** 기존 채팅 입력과 같은 규칙으로 검증한다. 통과하면 null, 아니면 오류 문구. */
    private static @Nullable String validate(InputData data, String raw) {
        String value = raw.strip();
        return switch (kindOf(data.inputType())) {
            case INTEGER -> isInteger(value) ? null : message("integer_fail");
            case BYTE -> isByte(value) ? null : message("integer_fail");
            case FLOAT -> {
                if (!isFloat(value)) yield message("float_fail");
                if (data.inputType() == InputType.SHADOW_STRENGTH) {
                    float f = Float.parseFloat(value);
                    if (f < 0 || f > 1) yield message("float_shadow_strength_fail");
                }
                yield null;
            }
            case TEXT -> switch (data.inputType()) {
                case BACKGROUND_COLOR, GLOW_COLOR -> parseStringToRGB(value) == null ? message("generic_color_fail") : null;
                case BLOCK_STATE -> {
                    if (data.blockMaterial() == null) yield null;
                    try {
                        Bukkit.createBlockData(data.blockMaterial(), value);
                        yield null;
                    } catch (IllegalArgumentException e) {
                        yield message("block_state_fail");
                    }
                }
                default -> null;
            };
        };
    }

    private static String message(String key) {
        return Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString(key));
    }

    /** 입력 칸에 미리 채울 현재 값. */
    @SuppressWarnings("deprecation")
    private static String initialValue(InputData data) {
        Display entity = data.entity();
        TextDisplay text = entity instanceof TextDisplay t ? t : null;
        return switch (data.inputType()) {
            case NAME -> entity.customName() == null ? "" : serialize(entity.customName());
            case TEXT -> text == null ? "" : serialize(text.text());
            case TEXT_APPEND -> "";
            case VIEW_RANGE -> number(entity.getViewRange());
            case DISPLAY_WIDTH -> number(entity.getDisplayWidth());
            case DISPLAY_HEIGHT -> number(entity.getDisplayHeight());
            case SHADOW_RADIUS -> number(entity.getShadowRadius());
            case SHADOW_STRENGTH -> number(entity.getShadowStrength());
            case LINE_WIDTH -> text == null ? "" : String.valueOf(text.getLineWidth());
            case TEXT_OPACITY -> text == null ? "" : String.valueOf(text.getTextOpacity() & 0xFF);
            case BACKGROUND_OPACITY -> text == null || text.getBackgroundColor() == null ? "" : String.valueOf(text.getBackgroundColor().getAlpha());
            case BACKGROUND_COLOR -> text == null ? "" : rgb(text.getBackgroundColor());
            case GLOW_COLOR -> rgb(entity.getGlowColorOverride());
            case BLOCK_STATE -> {
                if (!(entity instanceof BlockDisplay block)) yield "";
                String state = block.getBlock().getAsString();
                int bracket = state.indexOf('[');
                yield bracket < 0 ? "" : state.substring(bracket);
            }
        };
    }

    private static String serialize(Component component) {
        return DisplayEntityEditor.useMiniMessageFormat
                ? DisplayEntityEditor.miniMessage.serialize(component)
                : AMPERSAND.serialize(component);
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) return "";
        return new BigDecimal(Float.toString(value)).stripTrailingZeros().toPlainString();
    }

    private static String rgb(@Nullable Color color) {
        return color == null ? "" : color.getRed() + ", " + color.getGreen() + ", " + color.getBlue();
    }

    private static void onMain(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(DisplayEntityEditor.getPlugin(), task);
    }

    public static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isFloat(String s) {
        try {
            return Float.isFinite(Float.parseFloat(s));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isByte(String s) {
        if (isInteger(s)) {
            int i = Integer.parseInt(s);
            return 0 <= i && i <= 255;
        }
        return false;
    }

    public static void successfulIntegerInput(InputData inputData, int i, Player player) {
        if (inputData.inputType() == InputType.LINE_WIDTH) {
            ((TextDisplay) inputData.entity()).setLineWidth(i);
            player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("line_width_success")));
        }
    }

    @SuppressWarnings("deprecation")
    public static void successfulByteInput(InputData inputData, int integer, Player player) {
        switch (inputData.inputType()) {
            case TEXT_OPACITY -> {
                byte b = (byte) integer;
                ((TextDisplay) inputData.entity()).setTextOpacity(b);
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("opacity_success")));
            }
            case BACKGROUND_OPACITY -> {

                TextDisplay t = (TextDisplay) inputData.entity();
                if (t.getBackgroundColor() != null) {
                    t.setBackgroundColor(Color.fromARGB(integer, t.getBackgroundColor().getRed(), t.getBackgroundColor().getGreen(), t.getBackgroundColor().getBlue()));
                } else {
                    t.setBackgroundColor(Color.fromARGB(integer,0,0,0));
                }
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("opacity_success")));
            }
        }
    }

    public static void successfulFloatInput(InputData inputData, float f, Player player) {
        switch (inputData.inputType()) {
            case VIEW_RANGE -> {
                inputData.entity().setViewRange(f);
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("view_range_success")));
            }
            case DISPLAY_WIDTH -> {
                inputData.entity().setDisplayWidth(f);
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("display_width_success")));
            }
            case DISPLAY_HEIGHT -> {
                inputData.entity().setDisplayHeight(f);
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("display_height_success")));
            }
            case SHADOW_RADIUS -> {
                inputData.entity().setShadowRadius(f);
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("shadow_radius_success")));
            }
            case SHADOW_STRENGTH -> {
                if (0 <= f && f <= 1) {
                    inputData.entity().setShadowStrength(f);
                    player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("shadow_strength_success")));
                } else {
                    player.sendRawMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("shadow_strength_fail")));
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    public static void successfulTextInput(InputData inputData, String s ,Player player) {
        switch (inputData.inputType()) {
            case NAME -> {
                if (s.isBlank()) {
                    // 비우고 확인하면 이름을 지운다(GUI 우클릭과 같음).
                    inputData.entity().setCustomNameVisible(false);
                    inputData.entity().customName(null);
                    player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("name_success")));
                    return;
                }

                inputData.entity().setCustomNameVisible(true);

                if (DisplayEntityEditor.useMiniMessageFormat) {
                    Component c = DisplayEntityEditor.miniMessage.deserialize(s);
                    inputData.entity().customName(c);
                } else {
                    inputData.entity().setCustomName(ChatColor.translateAlternateColorCodes('&', s));
                }
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("name_success")));
            }
            case TEXT -> {
                String message = s;

                if (DisplayEntityEditor.useMiniMessageFormat) {
                    Component c = DisplayEntityEditor.miniMessage.deserialize(s);
                    ((TextDisplay) inputData.entity()).text(c);
                } else {
                    message = message.replace("\\n", "\n");
                    ((TextDisplay) inputData.entity()).setText(ChatColor.translateAlternateColorCodes('&', message));
                }

                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("text_success")));
            }
            case TEXT_APPEND -> {
                if (s.isEmpty()) return;
                String message = s;
                if (DisplayEntityEditor.useMiniMessageFormat) {
                    Component c = DisplayEntityEditor.miniMessage.deserialize(s);
                    TextDisplay display = (TextDisplay) inputData.entity();
                    display.text(display.text().append(c));
                } else {
                    message = message.replace("\\n", "\n");
                    ((TextDisplay) inputData.entity()).setText(ChatColor.translateAlternateColorCodes('&', ((TextDisplay) inputData.entity()).getText() + message));
                }
                player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("text_append_success")));
            }
            case BACKGROUND_COLOR -> {
                int[] array = parseStringToRGB(s);
                if (array != null) {
                    TextDisplay t = (TextDisplay) inputData.entity();
                    if (t.getBackgroundColor() != null) {
                        t.setBackgroundColor(Color.fromARGB(t.getBackgroundColor().getAlpha(), array[0], array[1], array[2]));
                    } else {
                        t.setBackgroundColor(Color.fromARGB(255,array[0],array[1],array[2]));
                    }
                    player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("background_color_success")));
                } else {
                    player.sendRawMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("generic_color_fail")));
                }
            }
            case GLOW_COLOR -> {
                int[] array = parseStringToRGB(s);
                if (array != null) {

                    BlockData blockData;
                    if (inputData.entity() instanceof BlockDisplay) blockData = ((BlockDisplay) inputData.entity()).getBlock();
                    else {
                        blockData = null;
                    }

                    inputData.entity().setGlowColorOverride(Color.fromRGB(array[0], array[1], array[2]));

                    if (inputData.entity() instanceof BlockDisplay) {
                        Bukkit.getScheduler().scheduleSyncDelayedTask(DisplayEntityEditor.getPlugin(), () -> ((BlockDisplay) inputData.entity()).setBlock(blockData), 1L);
                    }

                    player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("glow_color_success")));
                } else {
                    player.sendRawMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("generic_color_fail")));
                }
            }
            case BLOCK_STATE -> {
                if (inputData.blockMaterial() != null) {
                    try {
                        BlockData blockData = Bukkit.createBlockData(inputData.blockMaterial(), s.strip());
                        ((BlockDisplay) inputData.entity()).setBlock(blockData);
                        player.sendRawMessage(Utilities.getInfoMessageFormat(DisplayEntityEditor.messageManager.getString("block_state_success")));
                    } catch (IllegalArgumentException e) {
                        player.sendRawMessage(Utilities.getErrorMessageFormat(DisplayEntityEditor.messageManager.getString("block_state_fail")));
                    }
                }
            }
        }
    }

    /**
     * Used to parse a text input for an RBG value into an array of those values
     * @param input The text input
     * @return An array where index 0 is red, 1 is green and 2 is blue, or null if it is not three values between 0 and 255
     */
    private static int[] parseStringToRGB(String input) {
        String[] parts = input.split(",");
        if (parts.length != 3) {
            return null;
        }

        try {
            int[] values = new int[3];
            for (int i = 0; i < 3; i++) {
                values[i] = Integer.parseInt(parts[i].trim());
                if (values[i] < 0 || values[i] > 255) return null;
            }
            return values;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
