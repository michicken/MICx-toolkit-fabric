package dev.micx.micxfabric.jev;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Baritone 是否在运行期存在。
 *
 * <p>本类刻意不引用任何 baritone 类型：没装 Baritone 时（例如玩家自己的普通局）模组照样加载。
 * 真正碰 Baritone 的代码全在 {@link BaritoneBridge}，只在 {@link #present()} 为真时才会被类加载，
 * 所以「缺 Baritone」不可能变成 NoClassDefFoundError。
 */
public final class BaritoneSupport {
    private static final boolean PRESENT = detect();
    private static final String VERSION = detectVersion();

    private BaritoneSupport() {
    }

    private static boolean detect() {
        try {
            Class.forName("baritone.api.BaritoneAPI", false, BaritoneSupport.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String detectVersion() {
        if (!PRESENT) return null;
        try {
            return FabricLoader.getInstance().getModContainer("baritone")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse("unknown");
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public static boolean present() {
        return PRESENT;
    }

    /** Baritone mod 版本号；未安装时为 null。 */
    public static String version() {
        return VERSION;
    }
}
