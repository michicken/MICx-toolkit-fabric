package dev.micx.micxfabric.jev;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.micx.micxfabric.FabricRuntime;
import dev.micx.micxfabric.MicxFabric;
import dev.micx.micxfabric.Module;
import dev.micx.micxfabric.ModuleRuntime;
import dev.micx.micxfabric.ModuleStateStore;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 外部 Jev 代理的本地接口（127.0.0.1 HTTP）。
 *
 * <p>状态在客户端线程每个 tick 重建一份 JSON 快照放进 volatile 字段，HTTP 线程只读快照；
 * 指令则反过来——HTTP 线程把动作丢回客户端线程执行并阻塞等结果，所以两边都不会跨线程读世界数据。
 *
 * <p>默认关闭：模块开着才起服务。端口写在本模块配置里。
 */
public final class JevBridgeModule implements Module {
    private static final JevBridgeModule INSTANCE = new JevBridgeModule();
    private static final int DEFAULT_PORT = 8793;

    private boolean enabled;
    private int port = DEFAULT_PORT;
    private JevBridgeServer server;
    private volatile String snapshotJson;
    private volatile long snapshotAt;
    private boolean configLoaded;

    private JevBridgeModule() {
    }

    public static JevBridgeModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "jev_bridge";
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
        if (value) {
            startServer();
        } else {
            stopServer();
        }
    }

    @Override
    public void tick(Minecraft client) {
        try {
            JevMoveFix.tick(client);
            snapshotJson = JevStateSnapshot.build(client).toString();
            snapshotAt = System.currentTimeMillis();
        } catch (Throwable t) {
            MicxFabric.LOGGER.warn("jev bridge snapshot failed", t);
        }
    }

    public String snapshotJson() {
        return snapshotJson;
    }

    public long snapshotAt() {
        return snapshotAt;
    }

    public int port() {
        loadConfig();
        return port;
    }

    public void setPort(int value) {
        port = Math.max(1024, Math.min(65535, value));
        saveConfig();
    }

    public boolean listening() {
        return server != null;
    }

    /** 关客户端时收摊（daemon 线程其实也会被 JVM 收走，这里只是干净点）。 */
    public void shutdown() {
        JevMoveFix.clear(Minecraft.getInstance());
        stopServer();
    }

    private void startServer() {
        try {
            server = JevBridgeServer.start(this, port);
            MicxFabric.LOGGER.info("jev bridge listening on 127.0.0.1:{}", port);
        } catch (IOException e) {
            server = null;
            enabled = false;
            ModuleStateStore.put(id(), false);
            MicxFabric.LOGGER.warn("jev bridge failed to bind 127.0.0.1:" + port, e);
        }
    }

    private void stopServer() {
        JevBridgeServer current = server;
        server = null;
        if (current != null) {
            current.stop();
            MicxFabric.LOGGER.info("jev bridge stopped");
        }
    }

    static void respond(JsonObject out, String key, Object value) {
        if (value == null) {
            out.addProperty(key, "");
        } else if (value instanceof Number number) {
            out.addProperty(key, number);
        } else if (value instanceof Boolean bool) {
            out.addProperty(key, bool);
        } else {
            out.addProperty(key, String.valueOf(value));
        }
    }

    static JsonArray moduleList() {
        JsonArray array = new JsonArray();
        for (Module module : ModuleRuntime.modules()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", module.id());
            o.addProperty("on", module.enabled());
            array.add(o);
        }
        return array;
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Properties properties = new Properties();
        Path file = FabricRuntime.configPath().resolve("jev-bridge.properties");
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                properties.load(in);
            } catch (IOException e) {
                MicxFabric.LOGGER.warn("unable to read jev-bridge.properties", e);
            }
        }
        try {
            port = Integer.parseInt(properties.getProperty("port", Integer.toString(DEFAULT_PORT)).trim());
        } catch (NumberFormatException ignored) {
            port = DEFAULT_PORT;
        }
        port = Math.max(1024, Math.min(65535, port));
    }

    private void saveConfig() {
        Path file = FabricRuntime.configPath().resolve("jev-bridge.properties");
        Properties properties = new Properties();
        properties.setProperty("port", Integer.toString(port));
        try {
            Files.createDirectories(file.getParent());
            try (var out = Files.newOutputStream(file)) {
                properties.store(out, "MICx JevBridge");
            }
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("unable to save jev-bridge.properties", e);
        }
    }
}
