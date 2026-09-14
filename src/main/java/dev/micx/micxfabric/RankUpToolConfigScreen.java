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
        super(parent, "RankUpTool", "求 Rank · 定时发送");
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

        section(graphics, "STATUS / 状态", y);
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
        y = wrapped(graphics, "任何界面打开时都不发送（含聊天栏输入和本面板）；换世界会重新计时。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "RANK / 档位", y);
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
        graphics.text(font, "选中：" + rank + "　将发送：\"" + RankUpToolRules.message(rank) + "\"",
                contentLeft(), y, TEXT_DIM);
        y += 18;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "TIMING / 节奏", y);
        y += 20;
        graphics.text(font, "发送间隔 / Interval", contentLeft(), y + 4, TEXT);
        intervalBox.setX(contentRight() - INTERVAL_BOX_W - 16);
        intervalBox.setY(y);
        graphics.text(font, "秒", contentRight() - 12, y + 4, TEXT_FAINT);
        y += 26;
        y = wrapped(graphics, "默认 3 秒。间隔越短越容易被 Hypixel 判成重复刷屏并 mute，1–2 秒属于高风险区；"
                + "局内高频发送也容易挨队友骂——关掉模块或用 [模块快捷键] 一键停最省事。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;
        int sendW = 132;
        boolean sendHovered = isInside(mouseX, pointerY, contentLeft(), y, sendW, 18);
        drawButton(graphics, "立即发一条（测试）", contentLeft(), y, sendW, 18, sendHovered);
        hits.add(new Hit(contentLeft(), y, sendW, 18,
                () -> module.sendNow(Minecraft.getInstance())));
        y += 26;
        y = wrapped(graphics, "「立即发一条」按一次只补发一条，并重置自动计时，不会连着再发一条。",
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
