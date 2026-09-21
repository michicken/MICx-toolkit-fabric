package dev.micx.micxfabric;

import java.io.IOException;
import java.util.Properties;

/**
 * 面板文案的双语义开关。
 *
 * <p>一次全面改写会让「这句话到底还是不是原来的意思」失去参照，所以每段被改写的文案都
 * 同时保留新版与改写前的旧版，面板顶部的按钮切换显示哪一版。未改写的文案用
 * {@link #fixed(String)} 包一下，两版显示同一句，便于对照时一眼看出哪些真的动过。
 *
 * <p>取用分两条路：面板描述符在类加载期构造，那时还取不到当前模式，所以描述符存
 * {@link Txt} 本体、渲染时用 {@link #of(Txt)} 解析；页面内的文案都在渲染或构造路径上，
 * 直接 {@link #pick(String, String)} 即可。
 */
public final class UiText {
    /** 一段双语义文案：{@code now} 是改写后，{@code was} 是改写前。 */
    public record Txt(String now, String was) {
        public Txt {
            if (now == null) throw new IllegalArgumentException("Txt.now");
            if (was == null) throw new IllegalArgumentException("Txt.was");
        }
    }

    private static final String FILE_NAME = "micx-panel-ui.properties";
    private static final String KEY = "semantics";
    private static final String CURRENT = "current";
    private static final String LEGACY = "legacy";

    /** true = 显示改写前的旧文案。 */
    private static volatile boolean legacy;
    /** 落盘入口，由 {@link #initialize()} 装上；为空时（测试 / 静态分析）只改内存。 */
    private static volatile Runnable persister;

    private UiText() {
    }

    /** 游戏启动时调用一次：读回上次选择，并装上落盘入口。 */
    public static void initialize() {
        Properties properties = ConfigProperties.load(
                FabricRuntime.configPath().resolve(FILE_NAME), null);
        legacy = LEGACY.equalsIgnoreCase(properties.getProperty(KEY, CURRENT).trim());
        persister = UiText::persist;
    }

    /** 打一条双语义文案，供静态表（如模块注册表）存放。 */
    public static Txt revised(String now, String was) {
        return new Txt(now, was);
    }

    /** 未被改写的文案：两版显示同一句。 */
    public static Txt unchanged(String text) {
        return new Txt(text, text);
    }

    /** 实时解析：渲染与构造路径上用这个。 */
    public static String shown(String now, String was) {
        return legacy ? was : now;
    }

    /** 解析描述符里存下来的双语义文案。 */
    public static String of(Txt text) {
        if (text == null) return "";
        return legacy ? text.was() : text.now();
    }

    public static boolean legacy() {
        return legacy;
    }

    public static void setLegacy(boolean value) {
        if (legacy == value) return;
        legacy = value;
        Runnable sink = persister;
        if (sink != null) sink.run();
    }

    public static void toggle() {
        setLegacy(!legacy);
    }

    /** 当前模式的短标签（面板按钮用）。 */
    public static String modeLabel() {
        return legacy ? "旧版" : "新版";
    }

    /** 切到的那个模式的短标签（面板按钮的悬停提示用）。 */
    public static String nextModeLabel() {
        return legacy ? "新版" : "旧版";
    }

    private static void persist() {
        Properties properties = new Properties();
        properties.setProperty(KEY, legacy ? LEGACY : CURRENT);
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve(FILE_NAME), properties,
                    "MICx panel text semantics");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save panel text semantics", exception);
        }
    }
}
