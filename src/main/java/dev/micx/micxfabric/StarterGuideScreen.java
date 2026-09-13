package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 新手引导：第一次打开面板时弹出，直接把常用快捷键绑好。
 * 每行 = 模块功能 + 当前按键；点击按键框后按任意键完成绑定，ESC 清除。
 * AutoSwitch（自动切枪）给出开关/模式两个键，并把模式用途写清楚。
 */
public final class StarterGuideScreen extends Screen {
    private static final int BG_SCRIM = 0xE60C0E12;
    private static final int BG_PANEL = 0xFF14181E;
    private static final int BG_RAISED = 0xFF1B2028;
    private static final int BG_HOVER = 0xFF232A34;
    private static final int LINE = 0xFF2A313B;
    private static final int TEXT = 0xFFE7EAEE;
    private static final int TEXT_DIM = 0xFF98A0AB;
    private static final int TEXT_FAINT = 0xFF5C6572;
    private static final int AMBER = 0xFFE8A73E;
    private static final String AUTOSWITCH_ID = "keyboard_clicker";

    private record GuideRow(String id, String title, String description) {
    }

    private static final List<GuideRow> ROWS = List.of(
            new GuideRow(AUTOSWITCH_ID, "自动切枪 AutoSwitch",
                    "打完一枪自动帮你切下一把枪，还能防卡弹。下面两个键分别管："),
            new GuideRow("skill_cast", "快捷释放技能 SkillCast",
                    "按一下自动把技能快速连点放完。"),
            new GuideRow("right_clicker", "右键连点 RightClicker",
                    "按住右键时以稳定速率自动连点（开/关在面板里切）。"),
            new GuideRow("zoom_scope", "缩放 ZoomScope",
                    "按住临时拉近视角，看清远处情况，松开恢复。"),
            new GuideRow("player_visibility", "隐藏周围玩家 PlayerVisibility",
                    "一键隐藏或淡化周围其他玩家模型，画面更清爽。")
    );

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private final List<GuideRow> rows = new ArrayList<>();
    private final Screen parent;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int scroll;
    private int contentHeight;
    /** 监听中的目标行；target=false 绑开关键，true 绑 AutoSwitch 模式键。 */
    private int listeningIndex = -1;
    private boolean listeningModeKey;

    public StarterGuideScreen(Screen parent) {
        super(Component.literal("MICx Guide"));
        this.parent = parent;
        for (GuideRow row : ROWS) {
            ModulePanelDescriptor descriptor = ModulePanelRegistry.get(row.id());
            if (descriptor != null && descriptor.hasKeybind()) rows.add(row);
        }
    }

    @Override
    protected void init() {
        cardW = Math.min(440, width - 24);
        cardH = Math.min(300, height - 24);
        cardX = (width - cardW) / 2;
        cardY = (height - cardH) / 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        hits.clear();
        graphics.fill(0, 0, width, height, BG_SCRIM);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, BG_PANEL);

        int left = cardX + 16;
        int right = cardX + cardW - 16;
        int y = cardY + 12;

        graphics.text(font, "MICx 新手引导 · 快捷键", left, y, AMBER);
        graphics.text(font, "点击按键框 → 按想要的键；ESC 清除；鼠标侧键也能绑。", left, y + 12, TEXT_DIM);
        y += 32;
        graphics.fill(left, y, right, y + 1, LINE);
        y += 6;
        y -= scroll;
        int top = y;

        for (int i = 0; i < rows.size(); i++) {
            GuideRow row = rows.get(i);
            if (y > cardY + 30 && y < cardY + cardH - 44) {
                graphics.text(font, row.title(), left, y, TEXT);
                graphics.text(font, row.description(), left, y + 11, TEXT_DIM);
                if (row.id().equals(AUTOSWITCH_ID)) {
                    drawAutoswitchButtons(graphics, mouseX, mouseY, right, y + 24);
                    y += 48;
                } else {
                    drawBindButton(graphics, i, false, mouseX, mouseY, right, y - 1);
                    y += 30;
                }
            } else {
                y += row.id().equals(AUTOSWITCH_ID) ? 48 : 30;
            }
        }
        contentHeight = 0;
        for (GuideRow row : rows) contentHeight += row.id().equals(AUTOSWITCH_ID) ? 48 : 30;
        y += 4;

        graphics.fill(left, y, right, y + 1, LINE);
        y += 7;
        y = drawWrapped(graphics, "自动切枪的模式键（默认 V）循环切换管哪几格快捷栏：OFF 关闭 → 23 → 234 → 24 → 34"
                        + "（数字 = 自动切第 2/3/4 格）。开启后打完一枪立刻切到下一把枪，也能防卡弹。",
                left, y, TEXT_DIM, right - left);
        y += 3;
        drawWrapped(graphics, "其他模块绑定后按对应键即触发。以后随时在 /micx 面板里改，重看本引导输入 /micx guide。",
                left, y, TEXT_FAINT, right - left);

        int bottom = cardY + cardH - 26;
        graphics.text(font, "完成绑定后点击按钮进入面板（本引导不可跳过）", left, bottom + 5, TEXT_FAINT);
        int finishW = 132;
        int finishX = right - finishW;
        drawButton(graphics, "完成，打开面板", finishX, bottom, finishW, 18, false, mouseX, mouseY, finishX, bottom, finishW, 18, TEXT);
        hits.add(new Hit(finishX, bottom, finishW, 18, this::complete));
        clampScroll(top);
    }

    private void drawAutoswitchButtons(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       int right, int y) {
        int i = autoswitchIndex();
        int halfW = 118;
        int x1 = right - halfW;
        String toggleLabel = listeningIndex == i && !listeningModeKey
                ? "按任意键…" : "开关键: " + keyLabel(ModulePanelRegistry.get(AUTOSWITCH_ID));
        drawButton(graphics, toggleLabel, x1, y, halfW, 17, listeningIndex == i && !listeningModeKey,
                mouseX, mouseY, x1, y, halfW, 17, TEXT);
        final int index = i;
        hits.add(new Hit(x1, y, halfW, 17, () -> {
            listeningIndex = index;
            listeningModeKey = false;
        }));
        KeyboardClickerModule module = KeyboardClickerModule.instance();
        int x2 = x1 - 6 - halfW;
        String modeLabel = listeningIndex == i && listeningModeKey
                ? "按任意键…" : "模式键: " + new InputBinding(module.modeKey()).label() + " (" + module.modeName() + ")";
        drawButton(graphics, modeLabel, x2, y, halfW, 17, listeningIndex == i && listeningModeKey,
                mouseX, mouseY, x2, y, halfW, 17, TEXT);
        hits.add(new Hit(x2, y, halfW, 17, () -> {
            listeningIndex = index;
            listeningModeKey = true;
        }));
    }

    private int autoswitchIndex() {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id().equals(AUTOSWITCH_ID)) return i;
        }
        return -1;
    }

    private void drawBindButton(GuiGraphicsExtractor graphics, int index, boolean modeKey,
                                int mouseX, int mouseY, int right, int y) {
        GuideRow row = rows.get(index);
        int bindW = 92;
        int bindX = right - bindW;
        boolean listening = listeningIndex == index && listeningModeKey == modeKey;
        String label;
        if (listening) {
            label = "按任意键…";
        } else if (modeKey) {
            label = new InputBinding(KeyboardClickerModule.instance().modeKey()).label();
        } else {
            label = keyLabel(ModulePanelRegistry.get(row.id()));
        }
        drawButton(graphics, label, bindX, y, bindW, 17, listening,
                mouseX, mouseY, bindX, y, bindW, 17,
                label.equals("未绑定") ? TEXT_FAINT : TEXT);
        hits.add(new Hit(bindX, y, bindW, 17, () -> {
            listeningIndex = index;
            listeningModeKey = modeKey;
        }));
    }

    private static String keyLabel(ModulePanelDescriptor descriptor) {
        String label = descriptor.keybind().keyLabel();
        return label == null || label.isBlank() ? "未绑定" : label;
    }

    private int drawWrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        for (net.minecraft.util.FormattedCharSequence line : font.split(Component.literal(text), maxWidth)) {
            graphics.text(font, line, x, y, color);
            y += 11;
        }
        return y;
    }

    private void drawButton(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h,
                            boolean active, int mouseX, int mouseY, int hitX, int hitY, int hitW, int hitH,
                            int color) {
        boolean hovered = mouseX >= hitX && mouseX < hitX + hitW && mouseY >= hitY && mouseY < hitY + hitH;
        graphics.fill(x, y, x + w, y + h, active || hovered ? BG_HOVER : BG_RAISED);
        graphics.fill(x, y, x + w, y + 1, LINE);
        graphics.fill(x, y + h - 1, x + w, y + h, LINE);
        graphics.text(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listeningIndex >= 0) {
            GuideRow row = rows.get(listeningIndex);
            if (listeningModeKey) {
                // 模式键只收键盘键；鼠标点击取消监听
                listeningIndex = -1;
                return true;
            }
            ModulePanelDescriptor descriptor = ModulePanelRegistry.get(row.id());
            if (descriptor != null && descriptor.hasKeybind()) {
                descriptor.keybind().setKeyCode(-100 + event.button());
            }
            listeningIndex = -1;
            return true;
        }
        for (Hit hit : hits) {
            if (event.x() >= hit.x() && event.x() < hit.x() + hit.w()
                    && event.y() >= hit.y() && event.y() < hit.y() + hit.h()) {
                hit.action().run();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningIndex >= 0) {
            if (listeningModeKey) {
                if (!event.isEscape() && event.key() != 0) {
                    KeyboardClickerModule.instance().setModeKey(event.key());
                }
            } else {
                ModulePanelDescriptor descriptor = ModulePanelRegistry.get(rows.get(listeningIndex).id());
                if (descriptor != null && descriptor.hasKeybind()) {
                    if (event.isEscape()) {
                        descriptor.keybind().clear();
                    } else if (event.key() != 0) {
                        descriptor.keybind().setKeyCode(event.key());
                    }
                }
            }
            listeningIndex = -1;
            return true;
        }
        // 引导不可跳过：ESC/回车不关闭也不完成，唯一出口是“完成”按钮
        if (event.isEscape() || event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll += scrollY > 0 ? -24 : 24;
        return true;
    }

    private void clampScroll(int top) {
        int viewport = cardH - 100;
        int max = Math.max(0, contentHeight - viewport + 60);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private void complete() {
        StarterGuideState.markCompleted();
        if (minecraft == null) return;
        if (parent != null) minecraft.setScreenAndShow(parent);
        else minecraft.setScreenAndShow(new MicxPanelScreen(null));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
