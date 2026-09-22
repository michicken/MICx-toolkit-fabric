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
    /** 正在录制买弹快捷键的槽位（0=枪一 1=枪二 2=枪三；-1=不在录制）。 */
    private int listeningGun = -1;

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    public RemoteShopConfigScreen(Screen parent) {
        super(parent, UiText.shown("Reach买子弹", "远程商店"), UiText.shown("RemoteShop · 远程买弹", "远程商店 · 远程买弹"));
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

        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
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
        y = wrapped(graphics, "服务端闸门（1.8 引擎，Zombies 实际跑的那套）：右键实体 6 格有视野 / 3 格隔墙；"
                + "右键方块由服务端按朝眼重新 rayTrace 校验，只到 4.5 格。落在闸门外的包会被静默丢弃。"
                + "想知道买弹到底走哪条通道，开 PacketLog 抓一次。"
                + "快捷键 " + new InputBinding(module.bindingCode()).label() + " = 买一次。",
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("扫描附近全息", "SCAN / 附近全息"), y);
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
                y = wrapped(graphics, UiText.shown("没有扫到符合关键词的目标。把你看到的全息文字填进下面的关键词，再扫一次。", "没有命中关键词的目标——把你看到的全息文字填进下面的关键词再重扫。"),
                        contentLeft(), y, AMBER, contentWidth()) + 4;
            }
        }

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("关键词", "KEYWORD / 关键词"), y);
        y += 20;
        keywordsBox.setX(contentLeft());
        keywordsBox.setY(y);
        y += KEYWORDS_BOX_H + 6;
        y = wrapped(graphics, UiText.shown("用逗号或空格分隔；名字里含其中任意一个就算目标，不分大小写。", "逗号或空格分隔，名字里含任意一个就算目标（不分大小写）。")
                + "当前：" + RemoteShopRules.keywordsText(module.keywords()),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("买一次", "ACTION / 买一次"), y);
        y += 20;
        int buyW = 148;
        boolean buyHovered = isInside(mouseX, pointerY, contentLeft(), y, buyW, 18);
        drawButton(graphics, "买一次（最近目标）", contentLeft(), y, buyW, 18, buyHovered);
        hits.add(new Hit(contentLeft(), y, buyW, 18, () -> module.triggerNearest(client)));
        y += 24;
        y = wrapped(graphics, "上次：" + module.lastReport(), contentLeft(), y, TEXT, contentWidth()) + 6;
        y = wrapped(graphics, UiText.shown("按一下只买一次，带 1 秒冷却，不会自动连买。目标超过 5.5 格时只报告、不发包。", "只按一下发一次（1 秒冷却），不会自动连买。目标超出 5.5 格时只报告、不发包。"),
                contentLeft(), y, TEXT_FAINT, contentWidth()) + 8;

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("三槽买弹快捷键（按下激活）", "GUN KEYS / 按键买弹"), y);
        y += 20;
        y = gunKeyRow(graphics, mouseX, mouseY, "枪一（槽位2）", 0, y);
        y = gunKeyRow(graphics, mouseX, mouseY, "枪二（槽位3）", 1, y);
        y = gunKeyRow(graphics, mouseX, mouseY, "枪三（槽位4）", 2, y);
        y = wrapped(graphics, UiText.shown(
                "范围内按下即买：切到这把枪 → 对商店发包 → 切回原枪，全程键盘连点被按住；键盘连点开着时交互包发出后 50 毫秒即放行（不等切回），关着时切回后 50 毫秒放行。"
                        + "没扫到目标/超程/冷却中只提示、不切槽。模块关着时无效。",
                "按下激活：切槽→发包→切回；键盘连点临时暂停，发包后 50ms 放行。"),
                contentLeft(), y, TEXT_DIM, contentWidth()) + 4;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    private int gunKeyRow(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String label, int gun, int y) {
        RemoteShopModule module = RemoteShopModule.instance();
        graphics.text(font, label, contentLeft(), y + 4, TEXT);
        int x = contentRight() - 174;
        int w = 174;
        boolean listening = listeningGun == gun;
        String text = listening ? "按键 · ESC取消" : KeyChord.keyName(module.buyKeyCode(gun));
        drawButton(graphics, text, x, y, w, 18, isInside(mouseX, mouseY, x, y, w, 18));
        hits.add(new Hit(x, y, w, 18, () -> listeningGun = listening ? -1 : gun));
        return y + 26;
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
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (listeningGun >= 0) {
            if (event.isEscape()) {
                listeningGun = -1;
                return true;
            }
            RemoteShopModule.instance().setBuyKeyCode(listeningGun, event.key());
            listeningGun = -1;
            return true;
        }
        return super.keyPressed(event);
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
