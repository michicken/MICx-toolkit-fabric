package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RankUpTool 面板：档位选择 + 发送间隔 + 运行状态。
 *
 * <p>档位点击即刻生效并落盘；间隔在「保存并返回」时解析（越界会停在面板里报错，和前几个模块同一口径）。
 */
public final class RankUpToolConfigScreen extends ModuleConfigScreen {
    private static final int RANK_H = 18;
    private static final int RANK_GAP = 4;
    private static final int INTERVAL_BOX_W = 56;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox intervalBox;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public RankUpToolConfigScreen(Screen parent) {
        super(parent, UiText.shown("求 Rank", "RankUpTool"), UiText.shown("RankUpTool · 定时发送", "求 Rank · 定时发送"));
    }

    @Override
    protected void rebuildWidgets() {
        RankUpToolModule module = RankUpToolModule.instance();
        if (intervalBox == null) {
            intervalBox = new EditBox(font, 0, 0, INTERVAL_BOX_W, 18, Component.literal("seconds"));
            intervalBox.setMaxLength(4);
            intervalBox.setBordered(true);
            intervalBox.setValue(Integer.toString(module.intervalSeconds()));
            addRenderableWidget(intervalBox);
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        hits.clear();
        RankUpToolModule module = RankUpToolModule.instance();
        String rank = module.rank();
        // 命中判定要加回滚动量：本屏记录的是绘制坐标（已含滚动偏移）。
        int pointerY = mouseY + scrollOffset();

        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, "启用 / Enable", contentLeft(), y + 4, TEXT);
        boolean toggleHovered = isInside(mouseX, pointerY, contentRight() - 44, y + 1, 44, 16);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(), toggleHovered);
        hits.add(new Hit(contentRight() - 44, y + 1, 44, 16, () -> module.setEnabled(!module.enabled())));
        y += 24;
        String status = module.enabled()
                ? "运行中 · 已发送 " + module.sentCount() + " 条 · 距下一条 "
                        + String.format(java.util.Locale.ROOT, "%.1fs", module.remainingMillis() / 1000.0)
                : "已关闭";
        graphics.text(font, status, contentLeft(), y, module.enabled() ? ON : TEXT_DIM);
        y += 14;
        y = wrapped(graphics, UiText.shown("只要有界面开着就不会发送（包括正在打字和开着这个面板）。换世界会重新计时。", "任何界面打开时都不发送（含聊天栏输入和本面板）；换世界会重新计时。")
                + "模块开启时切到别的应用不会自动弹暂停界面，会一直照发。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("书本提醒", "BOOK ALERT / 书本警报"), y);
        y += 20;
        y = wrapped(graphics, "打开成书 / 书与笔 / 签名页任一书本界面后，每 0.5 秒「叮」一声（音符盒铃铛音），"
                + "最长响 1 分钟；书本界面一关立即安静，重新打开重新计时。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("档位", "RANK / 档位"), y);
        y += 20;
        int width = rankButtonWidth();
        List<String> ranks = RankUpToolRules.RANKS;
        for (int i = 0; i < ranks.size(); i++) {
            String label = ranks.get(i);
            int x = contentLeft() + i * (width + RANK_GAP);
            boolean selected = label.equals(rank);
            boolean hovered = isInside(mouseX, pointerY, x, y, width, RANK_H);
            drawButton(graphics, label, x, y, width, RANK_H, hovered);
            if (selected) {
                graphics.fill(x, y, x + width, y + 1, AMBER);
                graphics.fill(x, y + RANK_H - 2, x + width, y + RANK_H, AMBER);
            }
            hits.add(new Hit(x, y, width, RANK_H, () -> module.setRank(label)));
        }
        y += RANK_H + 8;
        graphics.text(font, "选中：" + rank, contentLeft(), y, TEXT_DIM);
        y += 18;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("话术", "MESSAGE / 话术"), y);
        y += 20;
        graphics.text(font, "固定文本 / Fixed", contentLeft(), y + 4, TEXT);
        boolean fixedHovered = isInside(mouseX, pointerY, contentRight() - 44, y + 1, 44, 16);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.fixedText(), fixedHovered);
        hits.add(new Hit(contentRight() - 44, y + 1, 44, 16,
                () -> module.setFixedText(!module.fixedText())));
        y += 24;
        String preview = module.fixedText()
                ? "固定文本：\"" + RankUpToolRules.message(rank) + "\""
                : "变形句库：" + module.messageCount() + " 句，本轮还剩 " + module.remainingMessages()
                        + " 句（一轮内不重复）";
        graphics.text(font, preview, contentLeft(), y, module.fixedText() ? TEXT_DIM : ON);
        y += 14;
        if (!module.lastSentText().isEmpty()) {
            y = wrapped(graphics, "上一条：「" + module.lastSentText() + "」",
                    contentLeft(), y, TEXT_FAINT, contentWidth()) + 4;
        }
        int shuffleW = 92;
        boolean shuffleHovered = isInside(mouseX, pointerY, contentLeft(), y, shuffleW, 18);
        drawButton(graphics, "重洗句库", contentLeft(), y, shuffleW, 18, shuffleHovered);
        hits.add(new Hit(contentLeft(), y, shuffleW, 18, module::reshuffleMessages));
        y += 26;
        y = wrapped(graphics, "关掉「固定文本」走变形句库：一轮 " + module.messageCount()
                + " 句里每句只用一次，跨轮也不会让同一句连着出现两次；换世界会重新洗牌。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("节奏", "TIMING / 节奏"), y);
        y += 20;
        graphics.text(font, "发送间隔 / Interval", contentLeft(), y + 4, TEXT);
        intervalBox.setX(contentRight() - INTERVAL_BOX_W - 16);
        intervalBox.setY(y);
        graphics.text(font, "秒", contentRight() - 12, y + 4, TEXT_FAINT);
        y += 26;
        y = wrapped(graphics, "默认 3 秒。变形句只避开「同一句重复」这一类判定，拦不住「发送频率」本身——"
                + "间隔越短越容易被 Hypixel 判成刷屏并 mute，1–2 秒属于高风险区；"
                + "局内高频发送也容易挨队友骂，关掉模块或用 [模块快捷键] 一键停最省事。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        int sendW = 132;
        boolean sendHovered = isInside(mouseX, pointerY, contentLeft(), y, sendW, 18);
        drawButton(graphics, "立即发一条（测试）", contentLeft(), y, sendW, 18, sendHovered);
        hits.add(new Hit(contentLeft(), y, sendW, 18,
                () -> module.sendNow(Minecraft.getInstance())));
        y += 26;
        y = wrapped(graphics, UiText.shown("「立即发一条」按一次只补一条，并重置自动计时，不会连着多发。", "「立即发一条」按一次只补发一条，并重置自动计时，不会连着再发一条。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    /** 五个档位按内容宽度均分，窄屏也不会挤在一起或溢出卡片。 */
    private int rankButtonWidth() {
        int available = contentWidth() - RANK_GAP * (RankUpToolRules.RANKS.size() - 1);
        return Math.max(46, Math.min(76, available / RankUpToolRules.RANKS.size()));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double pointerY = event.y() + scrollOffset();
            for (Hit hit : hits) {
                if (isInside(event.x(), pointerY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        try {
            RankUpToolModule.instance().setIntervalSeconds(parseInterval());
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage(exception.getMessage() == null ? "数值格式不正确" : exception.getMessage());
        }
    }

    private int parseInterval() {
        int value;
        try {
            value = Integer.parseInt(intervalBox.getValue().trim());
        } catch (NumberFormatException exception) {
            throw new NumberFormatException("发送间隔必须是数字");
        }
        if (value < RankUpToolRules.MIN_INTERVAL_SECONDS || value > RankUpToolRules.MAX_INTERVAL_SECONDS) {
            throw new NumberFormatException("发送间隔超出范围 "
                    + RankUpToolRules.MIN_INTERVAL_SECONDS + "–" + RankUpToolRules.MAX_INTERVAL_SECONDS + " 秒");
        }
        return value;
    }
}
