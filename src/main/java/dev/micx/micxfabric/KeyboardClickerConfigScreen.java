package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Functional KeyboardClicker settings for mode selection, delay, gate, and two bindings. */
public final class KeyboardClickerConfigScreen extends ModuleConfigScreen {
    private EditBox intervalBox;
    private boolean listeningToggle;
    private boolean listeningMode;
    private final int[] modeToggleY = new int[4];
    private final int[] modeToggleX = new int[4];
    private int protectToggleY = -1;

    public KeyboardClickerConfigScreen(Screen parent) {
        super(parent, UiText.shown("自动切枪", "KeyboardClicker"), UiText.shown("KeyboardClicker · 原生按键队列", "键盘连点 · 原生按键队列"));
    }

    @Override
    protected void rebuildWidgets() {
        intervalBox = new EditBox(font, 0, 0, 64, 20, Component.literal("interval"));
        intervalBox.setMaxLength(3);
        intervalBox.setValue(Integer.toString(KeyboardClickerModule.instance().clickInterval()));
        intervalBox.setBordered(true);
        addRenderableWidget(intervalBox);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        KeyboardClickerModule module = KeyboardClickerModule.instance();
        section(graphics, UiText.shown("开关与状态", "STATUS / 状态"), y);
        y += 20;
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "只排队原版 KeyMapping click（数字键 / Q），不直接构造点击包。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.text(font, UiText.shown("右键门控", "Right-click gate"), contentLeft(), y + 4, TEXT);
        graphics.text(font, "启用后仅在按住原生右键时连点。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.rightClickTrigger(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("模式", "MODES / 模式"), y);
        y += 20;
        graphics.text(font, UiText.shown("当前状态", "Current"), contentLeft(), y + 4, TEXT);
        graphics.text(font, module.modeName(), contentLeft() + 70, y + 4, AMBER);
        graphics.text(font, UiText.shown("间隔 ms", "Interval (ms)"), contentLeft() + 150, y + 4, TEXT);
        intervalBox.setX(contentRight() - 64);
        intervalBox.setY(y);
        y += 30;
        String[] modeNames = {"23", "234", "24", "34"};
        boolean[] enabled = {module.isMode23(), module.isMode234(), module.isMode24(), module.isMode34()};
        for (int i = 0; i < modeNames.length; i++) {
            modeToggleX[i] = contentRight() - 44;
            modeToggleY[i] = y;
            graphics.text(font, UiText.shown("模式 ", "Mode ") + modeNames[i], contentLeft(), y + 4, TEXT);
            drawToggle(graphics, modeToggleX[i], y, 44, 16, enabled[i],
                    isInside(mouseX, mouseY, modeToggleX[i], y, 44, 16));
            y += 24;
        }
        y += 2;
        y = wrapped(graphics, UiText.shown("可以同时勾选多个组合；V 键只在勾选到的模式之间循环，` 键切换连点开关。至少要留 1 项，间隔范围 40–100 毫秒。", "可同时勾选多个组合；V 只在已勾选模式间循环，` 切换连点开关。至少保留 1 项，范围 40–100 ms。"),
                contentLeft(), y, TEXT_DIM, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("防卡弹", "JAM PROTECT / 防卡弹"), y);
        y += 20;
        protectToggleY = y;
        graphics.text(font, "保护模式（模式 B）", contentLeft(), y + 4, TEXT);
        graphics.text(font, "实验：切到瞬间检测前兆，40ms 后按 Q 换弹 + 90ms 切走。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.isJamProtectModeB(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 24;
        y = wrapped(graphics, "换弹键混合（0.2.112）：默认左键；55/59/60/75/77/80/85/87/90/95/97/100/101 回合，"
                + "或本回合有人释放 LR（含队友，需 LR Indicator 开启）→ 整回合改用 Q。",
                contentLeft(), y, TEXT_FAINT, contentWidth());
        y += 14;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, UiText.shown("快捷键", "KEYS / 快捷键"), y);
        y += 20;
        graphics.text(font, UiText.shown("开关快捷键", "Toggle"), contentLeft(), y + 4, TEXT);
        drawButton(graphics, listeningToggle ? "按键..." : new InputBinding(module.toggleKey()).label(),
                contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 28;
        graphics.text(font, UiText.shown("模式", "Mode"), contentLeft(), y + 4, TEXT);
        drawButton(graphics, listeningMode ? "按键..." : new InputBinding(module.modeKey()).label(),
                contentRight() - 156, y, 156, 18,
                isInside(mouseX, mouseY, contentRight() - 156, y, 156, 18));
        y += 34;
        y = wrapped(graphics, UiText.shown("按 1 暂停，按 2 / 3 / 4 恢复成原版模式。这些按键只在游戏内、且没开界面时才会响应。", "按 1 可暂停，按 2/3/4 可恢复原版模式；所有操作受世界与 Screen 状态保护。"),
                contentLeft(), y, TEXT_FAINT, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if ((listeningToggle || listeningMode) && event.button() != 0) {
            int code = -100 + event.button();
            boolean toggle = listeningToggle;
            listeningToggle = false;
            listeningMode = false;
            if (toggle) KeyboardClickerModule.instance().setToggleKey(code);
            else KeyboardClickerModule.instance().setModeKey(code);
            return true;
        }
        if (event.button() == 0) {
            int toggleY = contentTop() - scrollOffset() + 21;
            if (isInside(event.x(), event.y(), contentRight() - 44, toggleY, 44, 16)) {
                KeyboardClickerModule.instance().setEnabled(!KeyboardClickerModule.instance().enabled());
                return true;
            }
            toggleY += 38;
            if (isInside(event.x(), event.y(), contentRight() - 44, toggleY, 44, 16)) {
                KeyboardClickerModule module = KeyboardClickerModule.instance();
                module.setRightClickTrigger(!module.rightClickTrigger());
                return true;
            }
            for (int i = 0; i < modeToggleY.length; i++) {
                if (!isInside(event.x(), event.y(), modeToggleX[i], modeToggleY[i] - scrollOffset(), 44, 16)) continue;
                KeyboardClickerModule module = KeyboardClickerModule.instance();
                switch (i) {
                    case 0 -> module.setMode23(!module.isMode23());
                    case 1 -> module.setMode234(!module.isMode234());
                    case 2 -> module.setMode24(!module.isMode24());
                    case 3 -> module.setMode34(!module.isMode34());
                }
                return true;
            }
            if (protectToggleY >= 0 && isInside(event.x(), event.y(),
                    contentRight() - 44, protectToggleY - scrollOffset() + 1, 44, 16)) {
                KeyboardClickerModule module = KeyboardClickerModule.instance();
                module.setJamProtectModeB(!module.isJamProtectModeB());
                return true;
            }
            int keyY = contentTop() - scrollOffset() + 21;
            // 38+38 状态区两行；14+20 分隔+MODES 标题；30 当前行；96+2 四模式行；
            // 14 模式提示；14+20+38+30+14+14 防卡弹区（分隔/标题/开关/分隔）
            keyY += 38 + 38 + 14 + 20 + 30 + 4 * 24 + 2 + 14 + 14 + 20 + 14 + 20 + 38 + 30 + 14 + 14;
            if (isInside(event.x(), event.y(), contentRight() - 156, keyY, 156, 18)) {
                listeningToggle = true;
                listeningMode = false;
                return true;
            }
            keyY += 28;
            if (isInside(event.x(), event.y(), contentRight() - 156, keyY, 156, 18)) {
                listeningMode = true;
                listeningToggle = false;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listeningToggle || listeningMode) {
            if (event.isEscape()) {
                listeningToggle = false;
                listeningMode = false;
            } else if (listeningToggle) {
                KeyboardClickerModule.instance().setToggleKey(event.key());
                listeningToggle = false;
            } else {
                KeyboardClickerModule.instance().setModeKey(event.key());
                listeningMode = false;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        try {
            KeyboardClickerModule.instance().setClickInterval(Integer.parseInt(intervalBox.getValue().trim()));
        } catch (NumberFormatException exception) {
            setErrorMessage("间隔必须是 40–100 ms 的数字");
            return;
        }
        super.saveAndClose();
    }
}
