package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Functional AutoText editor for the original key-to-message binding list. */
public final class AutoTextConfigScreen extends ModuleConfigScreen {
    private static final int MAX_ROWS = 8;
    private final List<EditBox> textBoxes = new ArrayList<>();
    private final List<Integer> codes = new ArrayList<>();
    private boolean listening;
    private int listeningRow = -1;

    public AutoTextConfigScreen(Screen parent) {
        super(parent, "AutoText", "快捷文本 · 绑定列表");
    }

    @Override
    protected void rebuildWidgets() {
        textBoxes.clear();
        codes.clear();
        for (AutoTextModule.Binding binding : AutoTextModule.instance().bindings()) {
            if (textBoxes.size() >= MAX_ROWS) break;
            addRow(binding.code(), binding.text());
        }
        if (textBoxes.isEmpty()) addRow(0, "");
    }

    private void addRow(int code, String text) {
        EditBox box = new EditBox(font, 0, 0, 170, 20, Component.literal("message"));
        box.setMaxLength(256);
        box.setValue(text == null ? "" : text);
        box.setBordered(true);
        textBoxes.add(box);
        codes.add(code);
        addRenderableWidget(box);
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        AutoTextModule module = AutoTextModule.instance();
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        graphics.text(font, "任何 Screen 打开时不会触发；释放绑定键只发送一次。", contentLeft(), y + 17, TEXT_DIM);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        y += 38;
        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        section(graphics, "BINDINGS / 快捷文本", y);
        y += 20;
        for (int i = 0; i < textBoxes.size(); i++) {
            EditBox box = textBoxes.get(i);
            box.setX(contentLeft() + 74);
            box.setY(y);
            String key = listening && listeningRow == i ? "按键..." : new InputBinding(codes.get(i)).label();
            drawButton(graphics, key, contentLeft(), y + 1, 68, 18,
                    isInside(mouseX, mouseY, contentLeft(), y + 1, 68, 18));
            if (textBoxes.size() > 1) {
                drawButton(graphics, "×", contentRight() - 18, y + 1, 18, 18,
                        isInside(mouseX, mouseY, contentRight() - 18, y + 1, 18, 18));
            }
            y += 28;
        }
        if (textBoxes.size() < MAX_ROWS) {
            drawButton(graphics, "+ 添加绑定", contentLeft(), y + 2, 92, 18,
                    isInside(mouseX, mouseY, contentLeft(), y + 2, 92, 18));
            y += 30;
        }
        y = wrapped(graphics, "文本按原配置逐项保存到 AutoText；空文本或未绑定行不会发送。",
                contentLeft(), y, TEXT_DIM, contentWidth());
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            if (listening && listeningRow >= 0) {
                codes.set(listeningRow, -100 + event.button());
                listening = false;
                listeningRow = -1;
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        int y = contentTop() + 21;
        if (isInside(event.x(), event.y(), contentRight() - 44, y, 44, 16)) {
            AutoTextModule.instance().setEnabled(!AutoTextModule.instance().enabled());
            return true;
        }
        y += 38 + 14 + 20;
        for (int i = 0; i < textBoxes.size(); i++) {
            if (isInside(event.x(), event.y(), contentLeft(), y + 1, 68, 18)) {
                listening = true;
                listeningRow = i;
                return true;
            }
            if (textBoxes.size() > 1 && isInside(event.x(), event.y(), contentRight() - 18, y + 1, 18, 18)) {
                removeWidget(textBoxes.remove(i));
                codes.remove(i);
                if (textBoxes.isEmpty()) addRow(0, "");
                return true;
            }
            y += 28;
        }
        if (textBoxes.size() < MAX_ROWS && isInside(event.x(), event.y(), contentLeft(), y + 2, 92, 18)) {
            addRow(0, "");
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening && listeningRow >= 0) {
            if (event.isEscape()) {
                listening = false;
                listeningRow = -1;
            } else {
                codes.set(listeningRow, event.key());
                listening = false;
                listeningRow = -1;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void saveAndClose() {
        List<AutoTextModule.Binding> bindings = new ArrayList<>();
        for (int i = 0; i < textBoxes.size(); i++) {
            String text = textBoxes.get(i).getValue().trim();
            if (codes.get(i) != 0 && !text.isBlank()) bindings.add(new AutoTextModule.Binding(codes.get(i), text));
        }
        AutoTextModule.instance().replaceBindings(bindings);
        super.saveAndClose();
    }
}
