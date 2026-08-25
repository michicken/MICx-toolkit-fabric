package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文本地化 Alien Arcadium 开局前警告：MICx 的英文聊天/actionbar 解析在
 * 中文局不生效，提示玩家点击切换英文模式（Forge ChineseAaLanguagePrompt 移植）。
 *
 * <p>独立读侧栏而非复用 ZombiesTracker.frame()：中文局侧栏常无英文 "Zombies"
 * 标题，tracker 会判非 Zombies 而停在空帧，检测不到"外星游乐园"。</p>
 */
public final class ChineseAaPrompt {

    private static final String CHINESE_AA_MAP = "外星游乐园";

    private static boolean shownForSession;
    private static int absentChecks;
    private static int ticks;

    private ChineseAaPrompt() {
    }

    public static void tick(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            shownForSession = false;
            absentChecks = 0;
            ticks = 0;
            return;
        }
        if (++ticks % 10 != 0) return;

        if (!isChineseAlienArcadium(client.level.getScoreboard())) {
            // 连续 20 次（约 10s）未见中文局才重臂，防止侧栏瞬时缺失导致重复刷屏
            if (++absentChecks >= 20) shownForSession = false;
            return;
        }
        absentChecks = 0;
        if (shownForSession) return;
        shownForSession = true;
        showLanguagePrompt(client);
    }

    /** 地图名或侧栏标题/任一行含"外星游乐园"即判定（Forge ZbSidebar 同语义）。 */
    private static boolean isChineseAlienArcadium(Scoreboard scoreboard) {
        if (scoreboard == null) return false;
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return false;
        if (contains(objective.getDisplayName().getString())) return true;
        List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
        for (PlayerScoreEntry entry : entries) {
            String owner = entry.owner();
            Component display = entry.display();
            String entryText = display == null ? owner : display.getString();
            PlayerTeam team = scoreboard.getPlayersTeam(owner);
            String rendered = renderLine(team, entryText, owner);
            if (contains(rendered)) return true;
        }
        return false;
    }

    private static boolean contains(String value) {
        return value != null && value.contains(CHINESE_AA_MAP);
    }

    private static String renderLine(PlayerTeam team, String entryText, String owner) {
        if (team == null) return entryText == null ? owner : entryText;
        try {
            return team.getFormattedName(Component.literal(entryText == null ? owner : entryText)).getString();
        } catch (RuntimeException ignored) {
            String prefix = team.getPlayerPrefix() == null ? "" : team.getPlayerPrefix().getString();
            String suffix = team.getPlayerSuffix() == null ? "" : team.getPlayerSuffix().getString();
            return prefix + (entryText == null ? owner : entryText) + suffix;
        }
    }

    private static void showLanguagePrompt(Minecraft client) {
        MutableComponent message = Component.literal(
                "§8[§bMICx§8] §e您当前游戏为中文模式！§cMICx-Toolkit 不在中文模式下起效，§f请点击切换为英文模式 ");
        MutableComponent action = Component.literal("§a§l[切换英文]")
                .withStyle(style -> style
                        .withClickEvent(new ClickEvent.RunCommand("/lang english"))
                        .withHoverEvent(new HoverEvent.ShowText(
                                Component.literal("§e点击执行 /lang english"))));
        message.append(action);
        client.player.sendSystemMessage(message);
    }
}
