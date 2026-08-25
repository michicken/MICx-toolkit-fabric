package dev.micx.micxfabric;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.Properties;

/**
 * 组合键（chord）工具：最多 3 键同时按下触发。
 *
 * <p>数据模型：{@code int[3]}，0 = 空位，负数 = 鼠标键（-100+N）。序列化为
 * 逗号串 {@code "k1,k2,k3"}，兼容旧版单键 int 配置。触发语义：全部键同时按住
 * （all-down）的上升沿触发一次；GUI 打开/未进世界时不触发。</p>
 */
public final class KeyChord {

    public static final int MAX_KEYS = 3;
    public static final int[] EMPTY = new int[MAX_KEYS];

    private KeyChord() {
    }

    public static int[] single(int keyCode) {
        int[] codes = new int[MAX_KEYS];
        codes[0] = keyCode;
        return codes;
    }

    /** 归一化：去重（保留首次出现）、剔除 0、末尾补 0。入参不被修改。 */
    public static int[] normalize(int[] codes) {
        int[] out = new int[MAX_KEYS];
        if (codes == null) return out;
        int count = 0;
        for (int code : codes) {
            if (code == 0) continue;
            boolean duplicate = false;
            for (int i = 0; i < count; i++) {
                if (out[i] == code) { duplicate = true; break; }
            }
            if (!duplicate && count < MAX_KEYS) out[count++] = code;
        }
        return out;
    }

    /** 解析逗号串（兼容旧版单 int）。非法输入 → 空组合。 */
    public static int[] parse(String value) {
        int[] out = new int[MAX_KEYS];
        if (value == null) return out;
        int count = 0;
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                int code = Integer.parseInt(trimmed);
                if (code == 0) continue;
                boolean duplicate = false;
                for (int i = 0; i < count; i++) {
                    if (out[i] == code) { duplicate = true; break; }
                }
                if (!duplicate && count < MAX_KEYS) out[count++] = code;
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /** 序列化：非 0 部分逗号连接（空组合 → ""）。 */
    public static String format(int[] codes) {
        StringBuilder builder = new StringBuilder();
        if (codes != null) {
            for (int code : codes) {
                if (code == 0) continue;
                if (builder.length() > 0) builder.append(',');
                builder.append(code);
            }
        }
        return builder.toString();
    }

    public static boolean isEmpty(int[] codes) {
        return codes == null || (codes[0] == 0 && codes[1] == 0 && codes[2] == 0);
    }

    public static boolean same(int[] a, int[] b) {
        return Arrays.equals(a, b);
    }

    /** 组合里第一个非 0 键（单键回落路径用）。 */
    public static int primary(int[] codes) {
        if (codes == null) return 0;
        for (int code : codes) {
            if (code != 0) return code;
        }
        return 0;
    }

    /** 组合是否全部物理按下（空组合恒 false）；GUI 打开时由调用方先行屏蔽。 */
    public static boolean isAllDown(int[] codes, net.minecraft.client.Minecraft client) {
        if (isEmpty(codes) || client == null || client.getWindow() == null) return false;
        long handle = client.getWindow().handle();
        try {
            for (int code : codes) {
                if (code == 0) break;
                boolean down = code < 0
                        ? GLFW.glfwGetMouseButton(handle, code + 100) == GLFW.GLFW_PRESS
                        : GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS;
                if (!down) return false;
            }
        } catch (RuntimeException ignored) {
            return false;
        }
        return true;
    }

    /* ---------------- 配置读写（优先新字段，兼容旧单键字段迁移） ---------------- */

    /** 读取组合键配置：优先新字段（逗号串）；为空且旧单键字段存在时迁移旧值。 */
    public static int[] readConfig(Properties properties, String chordKey, String legacyKey, int legacyDefault) {
        String formatted = properties.getProperty(chordKey, "");
        int[] codes = parse(formatted);
        if (isEmpty(codes)) {
            int legacy = ConfigProperties.integer(properties, legacyKey,
                    Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            if (legacy != Integer.MIN_VALUE && legacy != 0) return single(legacy);
            if (legacyDefault != 0 && legacy == Integer.MIN_VALUE) return single(legacyDefault);
            return EMPTY;
        }
        return codes;
    }

    public static void writeConfig(Properties properties, String chordKey, String legacyKey, int[] codes) {
        properties.setProperty(chordKey, format(codes));
        // 旧字段保留主键值，便于降级回旧版本时仍有绑定
        properties.setProperty(legacyKey, Integer.toString(primary(codes)));
    }

    /* ---------------- 显示 ---------------- */

    /** 单键 → 人类可读名（鼠标键 / 修饰键 / GLFW 本地名兜底）。 */
    public static String keyName(int code) {
        if (code == 0) return "未绑定";
        if (code < 0) {
            int button = code + 100;
            return switch (button) {
                case 0 -> "LMB";
                case 1 -> "RMB";
                case 2 -> "MMB";
                case 3 -> "MB4";
                case 4 -> "MB5";
                default -> "Mouse " + button;
            };
        }
        return switch (code) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> "Alt";
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> "Cmd";
            default -> {
                String name;
                try {
                    name = InputConstantsBridge.keyName(code);
                } catch (RuntimeException ignored) {
                    name = null;
                }
                if (name == null) yield "KEY_" + code;
                // InputConstants 返回 "key.keyboard.j"/"key.left.bracket" 这类全名，美化成单键样式
                String shortName = name.startsWith("key.keyboard.")
                        ? name.substring("key.keyboard.".length())
                        : name;
                yield shortName.length() == 1
                        ? shortName.toUpperCase(java.util.Locale.ROOT)
                        : Character.toUpperCase(shortName.charAt(0)) + shortName.substring(1);
            }
        };
    }

    /** 组合 → 显示串 {@code "A + B + C"}；空 → "未绑定"。 */
    public static String display(int[] codes) {
        if (isEmpty(codes)) return "未绑定";
        StringBuilder builder = new StringBuilder();
        for (int code : codes) {
            if (code == 0) break;
            if (builder.length() > 0) builder.append(" + ");
            builder.append(keyName(code));
        }
        return builder.toString();
    }

    /** KeyEvent → 捕获码（ESC 返回 0 由调用方处理提交语义）。 */
    public static int captureCode(KeyEvent event) {
        return event.key();
    }

    /** 仅内部使用：借 InputConstants 取 GLFW 键名。 */
    private static final class InputConstantsBridge {
        static String keyName(int code) {
            String name = com.mojang.blaze3d.platform.InputConstants.getKey(new KeyEvent(code, 0, 0)).getName();
            return name.isEmpty() ? null : name;
        }
    }
}
