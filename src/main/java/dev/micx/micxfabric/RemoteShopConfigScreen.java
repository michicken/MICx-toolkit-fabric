package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RemoteShop 面板：开关 + 附近全息扫描（真实距离真相）+ 关键词 + 「买一次」。
 *
 * <p>扫描列表是这版的重点：它会告诉你最近的目标到底几格、超没超服务端上限——
 * 站着猜「应该挺远」没有意义，这里给的是实测格数。
 */
public final class RemoteShopConfigScreen extends ModuleConfigScreen {
    private static final int KEYWORDS_BOX_W = 168;
    private static final int KEYWORDS_BOX_H = 18;
    private static final int MAX_LISTED = 8;
    private final List<Hit> hits = new ArrayList<>();
    private EditBox keywordsBox;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public RemoteShopConfigScreen(Screen parent) {
        super(parent, "RemoteShop", "远程商店 · 远程买弹");
    }

    @Override
    protected void rebuildWidgets() {
        RemoteShopModule module = RemoteShopModule.instance();
        if (keywordsBox == null) {
            keywordsBox = new EditBox(font, 0, 0, KEYWORDS_BOX_W, KEYWORDS_BOX_H, Component.literal("keywords"));
            keywordsBox.setMaxLength(120);
            keywordsBox.setBordered(true);
            keywordsBox.setValue(module.keywordsRaw());
            addRenderableWidget(keywordsBox);
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        hits.clear();
        RemoteShopModule module = RemoteShopModule.instance();
        Minecraft client = Minecraft.getInstance();
        // 命中判定要加回滚动量：本屏记录的是绘制坐标（已含滚动偏移）。
        int pointerY = mouseY + scrollOffset();

        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "启用 / Enable", contentLeft(), y + 4, TEXT);
        boolean toggleHovered = isInside(mouseX, pointerY, contentRight() - 44, y + 1, 44, 16);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(), toggleHovered);
        hits.add(new Hit(contentRight() - 44, y + 1, 44, 16, () -> module.setEnabled(!module.enabled())));
        y += 24;
        graphics.text(font, module.enabled()
                        ? "运行中 · 客户端射程已抬到 " + number(RemoteShopRules.CLIENT_RANGE) + " 格"
                        : "已关闭（射程是原版 3 / 4.5 格）",
                contentLeft(), y, module.enabled() ? ON : TEXT_DIM);
        y += 14;
        y = wrapped(graphics, "服务端硬上限：眼球到实体碰撞箱 6 格、到方块 5.5 格，超出直接丢包——"
                + "「远程」最多就这么远，跟客户端怎么改无关。快捷键 "
                + new InputBinding(module.bindingCode()).label() + " = 买一次。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "SCAN / 附近全息", y);
        y += 20;
        int scanW = 96;
        boolean scanHovered = isInside(mouseX, pointerY, contentLeft(), y, scanW, 18);
        drawButton(graphics, "重扫一次", contentLeft(), y, scanW, 18, scanHovered);
        hits.add(new Hit(contentLeft(), y, scanW, 18, () -> module.scan(client, true)));
        y += 24;
        List<RemoteShopModule.Candidate> candidates = module.scan(client, false);
        if (candidates.isEmpty()) {
            graphics.text(font, "附近 " + Math.round(RemoteShopRules.SCAN_RADIUS)
                    + " 格内没有带名字的实体（Hypixel 全息 = 带名字的盔甲架）", contentLeft(), y, TEXT_FAINT);
            y += 16;
        } else {
            RemoteShopModule.Candidate nearest = null;
            int shown = 0;
            for (RemoteShopModule.Candidate candidate : candidates) {
                if (candidate.matched() && nearest == null) nearest = candidate;
                if (shown >= MAX_LISTED) continue;
                boolean reachable = RemoteShopRules.withinTriggerRange(candidate.distance());
                int color = candidate.matched() ? (reachable ? ON : AMBER) : TEXT_FAINT;
                String row = (candidate.matched() ? "★ " : "· ")
                        + trim(candidate.name(), contentWidth() - 190) + " ｜ " + candidate.kindLabel()
                        + " ｜ " + number(candidate.distance()) + " 格 ｜ "
                        + RemoteShopRules.distanceVerdict(candidate.distance());
                graphics.text(font, row, contentLeft(), y, color);
                y += 13;
                shown++;
            }
            String summary = "共 " + candidates.size() + " 条带名字实体（★=命中关键词，只列最近 " + MAX_LISTED + " 条）";
            graphics.text(font, summary, contentLeft(), y, TEXT_FAINT);
            y += 16;
            if (nearest == null) {
                y = wrapped(graphics, "没有命中关键词的目标——把你看到的全息文字填进下面的关键词再重扫。",
                        contentLeft(), y, AMBER, contentWidth()) + 4;
            }
        }

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "KEYWORD / 关键词", y);
        y += 20;
        keywordsBox.setX(contentLeft());
        keywordsBox.setY(y);
        y += KEYWORDS_BOX_H + 6;
        y = wrapped(graphics, "逗号或空格分隔，名字里含任意一个就算目标（不分大小写）。"
                + "当前：" + RemoteShopRules.keywordsText(module.keywords()),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "ACTION / 买一次", y);
        y += 20;
        int buyW = 148;
        boolean buyHovered = isInside(mouseX, pointerY, contentLeft(), y, buyW, 18);
        drawButton(graphics, "买一次（最近目标）", contentLeft(), y, buyW, 18, buyHovered);
        hits.add(new Hit(contentLeft(), y, buyW, 18, () -> module.triggerNearest(client)));
        y += 24;
        y = wrapped(graphics, "上次：" + module.lastReport(), contentLeft(), y, TEXT, contentWidth()) + 6;
        y = wrapped(graphics, "只按一下发一次（1 秒冷却），不会自动连买。目标超出 5.5 格时只报告、不发包。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
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
        RemoteShopModule.instance().setKeywords(keywordsBox.getValue());
        super.saveAndClose();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
