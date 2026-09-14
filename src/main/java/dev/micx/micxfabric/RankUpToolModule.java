package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 定时把「&lt;选中的 Rank&gt; pls」发到当前频道。
 *
 * <p>用户定稿 2026-09-15：间隔默认 3 秒（面板可调 1–600 秒）、大厅与局内都发、文本固定不做模板。
 *
 * <p>安全边界：任何 Screen 打开时都不发（含本模块面板和聊天栏输入），世界未加载或暂停时不发；
 * 关闭模块会清掉计时，重新打开立刻发第一条。文本与节奏全在 {@link RankUpToolRules} 里，
 * 这里只管客户端状态。
 */
public final class RankUpToolModule implements Module {
    private static final RankUpToolModule INSTANCE = new RankUpToolModule();
    private final RankUpToolDeck deck =
            new RankUpToolDeck(RankUpToolMessages.size(), new java.util.Random());
    private String rank = RankUpToolRules.DEFAULT_RANK;
    private int intervalSeconds = RankUpToolRules.DEFAULT_INTERVAL_SECONDS;
    /** 面板开关：true 时只发固定的「&lt;rank&gt; pls」，默认走变形句库。 */
    private boolean fixedText;
    private boolean enabled;
    private boolean configLoaded;
    /** 上次发送时间；< 0 表示本次激活还没发过。 */
    private long lastSentMs = -1L;
    private int sentCount;
    private String lastSentText = "";

    private RankUpToolModule() {
    }

    public static RankUpToolModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "rank_up_tool";
    }

    /** 默认关：自动往聊天里发消息不该跟着游戏一起开。 */
    @Override
    public boolean defaultEnabled() {
        return false;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (enabled) {
            // 重新打开立刻发第一条，不用干等一个间隔。
            lastSentMs = -1L;
            sentCount = 0;
        }
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void tick(Minecraft client) {
        loadConfig();
        if (!enabled) return;
        if (client == null || client.player == null || client.level == null) return;
        if (client.isPaused() || client.gui.screen() != null) return;
        long now = System.currentTimeMillis();
        if (!RankUpToolRules.due(now, lastSentMs, RankUpToolRules.intervalMillis(intervalSeconds))) return;
        send(client, now, nextMessage());
    }

    /** 换世界/断线时重新计时，避免带着上一局的发条进新服。 */
    @Override
    public void resetState() {
        lastSentMs = -1L;
        sentCount = 0;
        deck.reshuffle();
    }

    /** 面板「立即发一条」：只补发一条，不改变自动节奏。 */
    public boolean sendNow(Minecraft client) {
        loadConfig();
        if (client == null || client.player == null || client.level == null) return false;
        long now = System.currentTimeMillis();
        if (!send(client, now, nextMessage())) return false;
        // 补发算一次落点，避免紧接着又自动发一条。
        lastSentMs = now;
        return true;
    }

    /**
     * 下一条要发的文本：默认从变形句库里取（洗牌袋，一轮内不重复），
     * 面板开了固定文本或句库为空时回落「&lt;档位&gt; pls」。
     */
    private String nextMessage() {
        if (fixedText) return RankUpToolRules.message(rank);
        int index = deck.next();
        if (index < 0) return RankUpToolRules.message(rank);
        String message = RankUpToolMessages.fill(RankUpToolMessages.template(index), rank);
        return message.isEmpty() ? RankUpToolRules.message(rank) : message;
    }

    private boolean send(Minecraft client, long nowMs, String message) {
        if (message.isBlank()) return false;
        client.player.connection.sendChat(message);
        lastSentMs = nowMs;
        sentCount++;
        lastSentText = message;
        return true;
    }

    public String rank() {
        loadConfig();
        return rank;
    }

    public void setRank(String rank) {
        loadConfig();
        this.rank = RankUpToolRules.normalizeRank(rank);
        saveConfig();
    }

    public int intervalSeconds() {
        loadConfig();
        return intervalSeconds;
    }

    public void setIntervalSeconds(int seconds) {
        loadConfig();
        this.intervalSeconds = RankUpToolRules.clampIntervalSeconds(seconds);
        saveConfig();
    }

    public int sentCount() {
        return sentCount;
    }

    /** 上一条实际发出去的文本（面板显示用；还没发过为空串）。 */
    public String lastSentText() {
        return lastSentText;
    }

    /** 变形句库总句数。 */
    public int messageCount() {
        return RankUpToolMessages.size();
    }

    /** 本轮句库里还剩几句没发。 */
    public int remainingMessages() {
        return deck.remaining();
    }

    /** 面板开关：固定发「&lt;rank&gt; pls」，不开则走变形句库。 */
    public boolean fixedText() {
        loadConfig();
        return fixedText;
    }

    public void setFixedText(boolean value) {
        loadConfig();
        fixedText = value;
        saveConfig();
    }

    /** 面板「重洗句库」：下一句开始重新洗一轮。 */
    public void reshuffleMessages() {
        deck.reshuffle();
    }

    /** 距下一条还有多少毫秒（面板倒计时）。 */
    public long remainingMillis() {
        loadConfig();
        if (!enabled) return 0L;
        return RankUpToolRules.remainingMillis(System.currentTimeMillis(), lastSentMs,
                RankUpToolRules.intervalMillis(intervalSeconds));
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path current = FabricRuntime.configPath().resolve("rank-up-tool.properties");
        Path legacy = FabricRuntime.configPath().getParent().resolve("MICxToolkit_RankUpTool.cfg");
        Properties properties = ConfigProperties.load(current, legacy);
        rank = RankUpToolRules.normalizeRank(ConfigProperties.string(properties, "rank",
                RankUpToolRules.DEFAULT_RANK));
        intervalSeconds = RankUpToolRules.clampIntervalSeconds(ConfigProperties.integer(properties,
                "intervalSeconds", RankUpToolRules.DEFAULT_INTERVAL_SECONDS,
                RankUpToolRules.MIN_INTERVAL_SECONDS, RankUpToolRules.MAX_INTERVAL_SECONDS));
        fixedText = ConfigProperties.bool(properties, "fixedText", false);
    }

    private void saveConfig() {
        loadConfig();
        Properties properties = new Properties();
        properties.setProperty("rank", rank);
        properties.setProperty("intervalSeconds", Integer.toString(intervalSeconds));
        properties.setProperty("fixedText", Boolean.toString(fixedText));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("rank-up-tool.properties"), properties,
                    "MICx RankUpTool configuration");
        } catch (IOException exception) {
            MicxFabric.LOGGER.warn("Unable to save RankUpTool configuration", exception);
        }
    }
}
