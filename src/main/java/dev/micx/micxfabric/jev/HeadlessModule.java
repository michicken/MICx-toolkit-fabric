package dev.micx.micxfabric.jev;

import com.mojang.blaze3d.platform.Window;
import dev.micx.micxfabric.MicxFabric;
import dev.micx.micxfabric.Module;
import dev.micx.micxfabric.ModuleStateStore;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 真无头客户端：隐藏窗口 + 整帧跳过渲染（见 MixinGameRendererHeadless），
 * 并且把瞄准旋转从渲染事件改到客户端 tick 消费（见 AimbotModule.tick 的 headless 分支）。
 *
 * <p>为什么不是「关掉窗口」：GL 上下文是挂在 GLFW 窗口上的，窗口没了客户端起不来。
 * 所以这里是「有窗口但不显示、不渲染」——对外表现和 headless 一样：不占屏幕、不抢焦点、GPU 基本空转。
 *
 * <p>依赖渲染事件的模块（ESP/HUD/粒子）在本模式下静默失效，玩法不依赖它们。
 */
public final class HeadlessModule implements Module {
    private static final HeadlessModule INSTANCE = new HeadlessModule();

    private boolean enabled;
    private boolean hideWindow = true;
    private boolean skipRender = true;
    private boolean rotationOnTick = true;
    private boolean windowHidden;
    private boolean configLoaded;

    private HeadlessModule() {
    }

    public static HeadlessModule instance() {
        return INSTANCE;
    }

    /** 无头时瞄准旋转改由客户端 tick 消费（渲染事件不再触发）。 */
    public static boolean rotationOnTickActive() {
        return INSTANCE.enabled && INSTANCE.rotationOnTick;
    }

    /** 无头时整帧跳过渲染。 */
    public static boolean skipRenderActive() {
        return INSTANCE.enabled && INSTANCE.skipRender;
    }

    /** 窗口当前是否真的被我们藏起来了。 */
    public static boolean windowHidden() {
        return INSTANCE.windowHidden;
    }

    @Override
    public String id() {
        return "headless";
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        if (enabled == value) return;
        enabled = value;
        ModuleStateStore.put(id(), value);
        applyWindowState();
        MicxFabric.LOGGER.info("[micx-headless] {} (hideWindow={}, skipRender={}, rotationOnTick={})",
                value ? "ON" : "OFF", hideWindow, skipRender, rotationOnTick);
    }

    @Override
    public void tick(Minecraft client) {
        // 世界加载/窗口重建后补一次：窗口句柄在早期可能是 0。
        if (enabled && hideWindow && !windowHidden) {
            applyWindowState();
        }
    }

    private void applyWindowState() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        try {
            Window window = client.getWindow();
            if (window == null) return;
            long handle = window.handle();
            if (handle == 0L) return;
            if (enabled && hideWindow) {
                GLFW.glfwHideWindow(handle);
                windowHidden = true;
            } else if (windowHidden) {
                GLFW.glfwShowWindow(handle);
                windowHidden = false;
            }
        } catch (Throwable t) {
            MicxFabric.LOGGER.warn("[micx-headless] window toggle failed", t);
        }
    }

    public boolean hideWindow() {
        loadConfig();
        return hideWindow;
    }

    public void setHideWindow(boolean value) {
        loadConfig();
        hideWindow = value;
        saveConfig();
        applyWindowState();
    }

    public boolean skipRender() {
        loadConfig();
        return skipRender;
    }

    public void setSkipRender(boolean value) {
        loadConfig();
        skipRender = value;
        saveConfig();
    }

    public boolean rotationOnTick() {
        loadConfig();
        return rotationOnTick;
    }

    public void setRotationOnTick(boolean value) {
        loadConfig();
        rotationOnTick = value;
        saveConfig();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Properties properties = new Properties();
        Path file = dev.micx.micxfabric.FabricRuntime.configPath().resolve("headless.properties");
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                MicxFabric.LOGGER.warn("unable to read headless.properties", e);
            }
        }
        hideWindow = readBool(properties, "hideWindow", true);
        skipRender = readBool(properties, "skipRender", true);
        rotationOnTick = readBool(properties, "rotationOnTick", true);
    }

    private void saveConfig() {
        Path file = dev.micx.micxfabric.FabricRuntime.configPath().resolve("headless.properties");
        Properties properties = new Properties();
        properties.setProperty("hideWindow", Boolean.toString(hideWindow));
        properties.setProperty("skipRender", Boolean.toString(skipRender));
        properties.setProperty("rotationOnTick", Boolean.toString(rotationOnTick));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                properties.store(out, "MICx Headless");
            }
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("unable to save headless.properties", e);
        }
    }

    private static boolean readBool(Properties properties, String key, boolean fallback) {
        String raw = properties.getProperty(key);
        return raw == null ? fallback : Boolean.parseBoolean(raw.trim());
    }
}
