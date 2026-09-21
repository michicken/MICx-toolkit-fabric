package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * PacketLog（封包日志）：把"你做了一件事之后客户端发了什么、服务端回了什么"记成可读文本。
 *
 * <p>用途很具体——<b>手动买一次子弹</b>，然后看日志判断买弹到底走哪条通道：
 * 右键方块（UseItemOn）？右键实体（Interact）？右键空气（UseItem）？点界面格子（ContainerClick）？
 * 还是自定义插件通道（CustomPayload）？以及服务端是开界面、发聊天，还是直接扣钱。
 * 结论出来之前不猜、不改任何游戏行为。
 *
 * <p>实现：{@link dev.micx.micxfabric.mixin.MixinConnectionPacketLog} 只在<b>玩家自己这条连接</b>上旁观，
 * 出站丢噪声、入站只记关键几类（见 {@link PacketLogRules}）；行先排队、由客户端 tick 落盘，不占网络线程。
 * 默认关；开关与快捷键在面板。
 */
public final class PacketLogModule implements Module {
    private static final PacketLogModule INSTANCE = new PacketLogModule();
    /** M = mark，打标记线用。 */
    private static final int DEFAULT_KEY = GLFW.GLFW_KEY_M;
    private static final int RING_CAPACITY = 300;
    private static final int MAX_RENDER = 160;

    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<String> recent = new ArrayDeque<>();
    private boolean enabled;
    private boolean configLoaded;
    /** true = 连移动包也记（排查用，日志会很大）。 */
    private boolean logAll;
    private InputBinding binding = new InputBinding(DEFAULT_KEY);
    private BufferedWriter writer;
    private Path logFile;
    private long sessionStartMs = -1L;
    private long writtenLines;

    private PacketLogModule() {
    }

    public static PacketLogModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "packet_log";
    }

    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean value) {
        loadConfig();
        if (value == enabled) return;
        enabled = value;
        if (value) {
            openSession();
        } else {
            mark("关闭日志");
            flushPending();
            closeSession();
        }
        ModuleStateStore.put(id(), value);
    }

    @Override
    public InputBinding primaryBinding() {
        loadConfig();
        return binding;
    }

    /** 快捷键=插一条标记线（买弹前后各按一下，长日志里好切段）。 */
    @Override
    public void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        loadConfig();
        mark("手动标记");
        if (client != null && client.player != null && enabled) {
            client.player.sendSystemMessage(ChatMessageStyles.notice("PacketLog 标记已插入"));
        }
    }

    @Override
    public void tick(Minecraft client) {
        flushPending();
    }

    @Override
    public void resetState() {
        if (enabled) mark("换世界/断线");
    }

    /** 由 Connection 的 mixin 调用（可能在网络线程上），只排队不落盘。 */
    public void capture(boolean outbound, Packet<?> packet) {
        if (!enabled || packet == null) return;
        String simpleName = packet.getClass().getSimpleName();
        if (!PacketLogRules.shouldLog(outbound, simpleName, logAll)) return;
        enqueue(PacketLogRules.line(sessionStartMs < 0L ? System.currentTimeMillis() : sessionStartMs,
                System.currentTimeMillis(), outbound, describe(packet)));
    }

    public void mark(String label) {
        if (!enabled) return;
        long now = System.currentTimeMillis();
        enqueue(PacketLogRules.marker(sessionStartMs < 0L ? now : sessionStartMs, now, label));
    }

    /** 面板/命令用：把最近这些行读出来看看。 */
    public List<String> recent() {
        synchronized (recent) {
            return new ArrayList<>(recent);
        }
    }

    public void clearRecent() {
        synchronized (recent) {
            recent.clear();
        }
    }

    public long writtenLines() {
        return writtenLines;
    }

    public String logFilePath() {
        return logFile == null ? "（还没开过日志）" : logFile.toString();
    }

    public boolean logAll() {
        loadConfig();
        return logAll;
    }

    public void setLogAll(boolean value) {
        loadConfig();
        logAll = value;
        saveConfig();
    }

    public int bindingCode() {
        loadConfig();
        return binding.code();
    }

    public void setBindingCode(int code) {
        loadConfig();
        binding = new InputBinding(code);
        saveConfig();
    }

    // ---- 落盘 ----

    private void openSession() {
        sessionStartMs = System.currentTimeMillis();
        writtenLines = 0L;
        clearRecent();
        try {
            Path dir = FabricRuntime.configPath().resolve("logs");
            Files.createDirectories(dir);
            logFile = dir.resolve(PacketLogRules.fileName(sessionStartMs));
            writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            String version = net.minecraft.SharedConstants.getCurrentVersion().name();
            writeLine(PacketLogRules.header(sessionStartMs, version));
            mark("会话开始");
            flushPending();
        } catch (IOException | RuntimeException exception) {
            MicxFabric.LOGGER.warn("Unable to open packet log", exception);
            writer = null;
            logFile = null;
        }
    }

    private void closeSession() {
        try {
            if (writer != null) writer.close();
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to close packet log", exception);
        }
        writer = null;
    }

    private void enqueue(String line) {
        pending.add(line);
        synchronized (recent) {
            recent.addLast(line);
            while (recent.size() > RING_CAPACITY) recent.removeFirst();
        }
    }

    private void flushPending() {
        String line;
        while ((line = pending.poll()) != null) {
            writeLine(line);
        }
    }

    private void writeLine(String line) {
        try {
            if (writer == null) return;
            writer.write(line);
            writer.newLine();
            writer.flush();
            writtenLines++;
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to write packet log", exception);
            closeSession();
        }
    }

    // ---- 渲染 ----

    /** Shared readable packet metadata for PacketLog and POI demonstration recordings. */
    public static String describe(Packet<?> packet) {
        if (packet instanceof ServerboundUseItemOnPacket p) {
            return "UseItemOn（右键方块） " + block(p.getHitResult()) + " 手=" + p.getHand();
        }
        if (packet instanceof ServerboundUseItemPacket p) {
            return "UseItem（右键空气/用物品） 手=" + p.getHand();
        }
        if (packet instanceof ServerboundInteractPacket p) {
            return "Interact（右键/打实体） " + PacketLogRules.truncate(p.toString(), MAX_RENDER);
        }
        if (packet instanceof ServerboundContainerClickPacket p) {
            return "ContainerClick（点界面格子） " + PacketLogRules.truncate(p.toString(), MAX_RENDER);
        }
        if (packet instanceof ServerboundChatPacket p) {
            return "发言「" + PacketLogRules.truncate(p.message(), 80) + "」";
        }
        if (packet instanceof ServerboundCustomPayloadPacket p) {
            return "自定义通道(出) " + payload(p.payload());
        }
        if (packet instanceof ClientboundCustomPayloadPacket p) {
            return "自定义通道(入) " + payload(p.payload());
        }
        if (packet instanceof ClientboundOpenScreenPacket p) {
            return "开界面 容器=" + p.getContainerId() + " 类型=" + p.getType()
                    + " 标题「" + text(p.getTitle()) + "」";
        }
        if (packet instanceof ClientboundContainerSetContentPacket p) {
            return "界面内容 容器=" + p.containerId() + " 非空格子=" + nonEmpty(p.items());
        }
        if (packet instanceof ClientboundContainerClosePacket p) {
            return "界面被关 容器=" + p.getContainerId();
        }
        if (packet instanceof ClientboundSystemChatPacket p) {
            return (p.overlay() ? "动作栏「" : "聊天「") + text(p.content()) + "」";
        }
        if (packet instanceof ClientboundSetTitleTextPacket p) {
            return "标题「" + text(p.text()) + "」";
        }
        if (packet instanceof ClientboundSetSubtitleTextPacket p) {
            return "副标题「" + text(p.text()) + "」";
        }
        if (packet instanceof ClientboundSetActionBarTextPacket p) {
            return "动作栏「" + text(p.text()) + "」";
        }
        if (packet instanceof ClientboundPlayerPositionPacket p) {
            return "服务端拉回 id=" + p.id() + " " + PacketLogRules.truncate(p.change().toString(), MAX_RENDER);
        }
        if (packet instanceof ClientboundAddEntityPacket p) {
            return "实体出现 id=" + p.getId() + " 类型=" + p.getType() + " @ "
                    + vec(p.getX(), p.getY(), p.getZ());
        }
        if (packet instanceof ClientboundSetScorePacket p) {
            return "计分 " + p.owner() + " / " + p.objectiveName() + " = " + p.score();
        }
        if (packet instanceof ClientboundSetDisplayObjectivePacket p) {
            return "侧栏目标 " + p.getObjectiveName() + " 槽=" + p.getSlot();
        }
        if (packet instanceof ClientboundSoundPacket p) {
            return "音效 " + p.getSound() + " 源=" + p.getSource()
                    + " @ " + vec(p.getX(), p.getY(), p.getZ());
        }
        if (packet instanceof ClientboundDisconnectPacket p) {
            return "断线/被踢 " + PacketLogRules.truncate(p.toString(), MAX_RENDER);
        }
        return packet.getClass().getSimpleName();
    }

    private static String payload(CustomPacketPayload payload) {
        if (payload == null) return "(空)";
        return PacketLogRules.truncate(payload.type().id() + " " + payload, MAX_RENDER);
    }

    private static String block(BlockHitResult hit) {
        if (hit == null) return "(无命中)";
        return hit.getBlockPos().toShortString() + " 面=" + hit.getDirection()
                + " 命中点=" + vec(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
    }

    private static long nonEmpty(List<ItemStack> items) {
        if (items == null) return 0L;
        long count = 0L;
        for (ItemStack stack : items) {
            if (stack != null && !stack.isEmpty()) count++;
        }
        return count;
    }

    private static String text(Component component) {
        return component == null ? "" : PacketLogRules.truncate(component.getString(), 90);
    }

    private static String vec(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z);
    }

    // ---- 配置 ----

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("packet-log.properties");
        Properties properties = ConfigProperties.load(current, null);
        logAll = ConfigProperties.bool(properties, "logAll", false);
        binding = new InputBinding(ConfigProperties.integer(properties, "keyCode", DEFAULT_KEY,
                -108, GLFW.GLFW_KEY_LAST));
    }

    private void saveConfig() {
        loadConfig();
        Properties properties = new Properties();
        properties.setProperty("logAll", Boolean.toString(logAll));
        properties.setProperty("keyCode", Integer.toString(binding.code()));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("packet-log.properties"), properties,
                    "MICx PacketLog configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save PacketLog configuration", exception);
        }
    }
}
