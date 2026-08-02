package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Shared layout and input behavior for module configuration pages. */
public abstract class ModuleConfigScreen extends Screen {
    protected static final int BG_SCRIM = 0xE60C0E12;
    protected static final int BG_PANEL = 0xFF14181E;
    protected static final int BG_RAISED = 0xFF1B2028;
    protected static final int BG_HOVER = 0xFF232A34;
    protected static final int LINE = 0xFF2A313B;
    protected static final int LINE_HI = 0xFF39424F;
    protected static final int TEXT = 0xFFE7EAEE;
    protected static final int TEXT_DIM = 0xFF98A0AB;
    protected static final int TEXT_FAINT = 0xFF5C6572;
    protected static final int AMBER = 0xFFE8A73E;
    protected static final int ON = 0xFF57B98C;
    protected static final int OFF = 0xFF525A64;

    private final Screen parent;
    private final String titleText;
    private final String subtitleText;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardH;
    private int contentTop;
    private int contentBottom;
    private int contentScroll;
    private int contentHeight;
    private String errorMessage;
    private int backX;
    private int backY;
    private static final int BACK_W = 52;
    private static final int BACK_H = 16;

    protected ModuleConfigScreen(Screen parent, String titleText, String subtitleText) {
        super(Component.literal(titleText));
        this.parent = parent;
        this.titleText = titleText;
        this.subtitleText = subtitleText;
    }

    protected final Screen parentScreen() {
        return parent;
    }

    protected final String errorMessage() {
        return errorMessage;
    }

    protected final void setErrorMessage(String message) {
        errorMessage = message;
    }

    @Override
    protected final void init() {
        cardW = Math.min(486, Math.max(0, width - 20));
        cardX = (width - cardW) / 2;
        cardY = Math.min(22, Math.max(0, height / 2));
        cardH = Math.max(0, height - cardY * 2);
        contentTop = cardY + 64;
        contentBottom = Math.max(contentTop, cardY + cardH - 24);
        contentScroll = Math.max(0, Math.min(contentScroll, Math.max(0, contentHeight - viewportHeight())));
        rebuildWidgets();
    }

    /** Subclasses may add native widgets after the geometry is known. */
    protected void rebuildWidgets() {
    }

    @Override
    public final void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG_SCRIM);
    }

    @Override
    public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, BG_PANEL);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, LINE_HI);
        outline(graphics, cardX, cardY, cardW, cardH, LINE);
        graphics.text(font, titleText, cardX + 14, cardY + 10, TEXT, true);
        graphics.text(font, subtitleText, cardX + 14, cardY + 30, TEXT_DIM);
        backX = cardX + cardW - 14 - BACK_W;
        backY = cardY + 11;
        boolean backHovered = mouseX >= backX && mouseX < backX + BACK_W
                && mouseY >= backY && mouseY < backY + BACK_H;
        drawButton(graphics, "‹ 返回", backX, backY, BACK_W, BACK_H, backHovered);
        graphics.fill(cardX, cardY + 52, cardX + cardW, cardY + 53, LINE);
        graphics.fill(cardX, contentBottom, cardX + cardW, contentBottom + 1, LINE);

        graphics.enableScissor(cardX + 1, contentTop, cardX + cardW - 1, contentBottom);
        int oldScroll = contentScroll;
        drawContent(graphics, mouseX, mouseY, contentTop - oldScroll);
        graphics.disableScissor();
        drawScrollbar(graphics);
        if (errorMessage != null && !errorMessage.isBlank()) {
            graphics.text(font, trim(errorMessage, cardW - 28), cardX + 14, contentBottom + 7, 0xFFD2685C);
        } else {
            graphics.text(font, "ESC 返回 · 滚轮滚动 · 修改后自动保存", cardX + 14, contentBottom + 7, TEXT_FAINT);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected abstract void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y);

    protected final int contentLeft() {
        return cardX + 14;
    }

    protected final int contentRight() {
        return cardX + cardW - 14;
    }

    protected final int contentWidth() {
        return Math.max(0, contentRight() - contentLeft());
    }

    protected final int viewportHeight() {
        return Math.max(0, contentBottom - contentTop);
    }

    protected final void addTextWidget(net.minecraft.client.gui.components.EditBox widget) {
        addRenderableWidget(widget);
    }

    protected final int contentTop() {
        return contentTop;
    }

    protected final int contentY() {
        return contentTop - contentScroll;
    }

    protected final int contentBottom() {
        return contentBottom;
    }

    protected final void setContentHeight(int height) {
        contentHeight = Math.max(0, height);
        contentScroll = Math.max(0, Math.min(contentScroll, Math.max(0, contentHeight - viewportHeight())));
    }

    protected final int scrollOffset() {
        return contentScroll;
    }

    protected final int line(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        String value = trim(text, maxWidth);
        graphics.text(font, value, x, y, color);
        return y + font.lineHeight;
    }

    protected final int wrapped(GuiGraphicsExtractor graphics, String text, int x, int y, int color, int maxWidth) {
        List<FormattedCharSequence> lines = font.split(Component.literal(text), Math.max(1, maxWidth));
        for (FormattedCharSequence sequence : lines) {
            graphics.text(font, sequence, x, y, color);
            y += font.lineHeight;
        }
        return y;
    }

    protected final void section(GuiGraphicsExtractor graphics, String text, int y) {
        graphics.text(font, text, contentLeft(), y, AMBER);
    }

    protected final void info(GuiGraphicsExtractor graphics, String text, int y) {
        wrapped(graphics, text, contentLeft(), y, TEXT_DIM, contentWidth());
    }

    protected final void drawToggle(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean enabled, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, enabled ? 0xFF1E3A31 : BG_RAISED);
        outline(graphics, x, y, w, h, hovered ? LINE_HI : LINE);
        int knobWidth = w / 2;
        int knobX = enabled ? x + w - knobWidth : x;
        graphics.fill(knobX, y, knobX + knobWidth, y + h, enabled ? ON : OFF);
        String label = enabled ? "开" : "关";
        graphics.text(font, label, knobX + (knobWidth - font.width(label)) / 2, y + 4, 0xFF0E1013);
    }

    protected final void drawButton(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h, boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? BG_HOVER : BG_RAISED);
        outline(graphics, x, y, w, h, hovered ? LINE_HI : LINE);
        graphics.text(font, text, x + 7, y + 5, hovered ? TEXT : TEXT_DIM);
    }

    protected final String mask(String value) {
        if (value == null || value.isEmpty()) return "(未设置)";
        return "•".repeat(Math.min(24, Math.max(8, value.length())));
    }

    protected final String trim(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, maxWidth, true);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (contentHeight <= viewportHeight()) return;
        int x = cardX + cardW - 5;
        graphics.fill(x, contentTop, x + 3, contentBottom, 0x33000000);
        int thumbHeight = Math.max(18, Math.round(viewportHeight() * (viewportHeight() / (float) contentHeight)));
        int maxScroll = Math.max(1, contentHeight - viewportHeight());
        int thumbY = contentTop + Math.round((viewportHeight() - thumbHeight) * (contentScroll / (float) maxScroll));
        graphics.fill(x, thumbY, x + 3, thumbY + thumbHeight, 0xFF8A6A2A);
        graphics.fill(x, thumbY, x + 3, thumbY + Math.min(6, thumbHeight), AMBER);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isInside(event.x(), event.y(), backX, backY, BACK_W, BACK_H)) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= contentTop && mouseY < contentBottom) {
            contentScroll += scrollY > 0 ? -18 : 18;
            contentScroll = Math.max(0, Math.min(contentScroll, Math.max(0, contentHeight - viewportHeight())));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    protected void saveAndClose() {
        minecraft.setScreenAndShow(parent);
    }

    protected final boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    protected final static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    protected final int keyName(int key) {
        return key;
    }

    protected final String keyLabel(int key) {
        if (key == GLFW.GLFW_KEY_UNKNOWN || key < 0) return "未绑定";
        String name = GLFW.glfwGetKeyName(key, 0);
        return name == null || name.isBlank() ? "Key " + key : name.toUpperCase();
    }

    protected final Minecraft client() {
        return minecraft;
    }
}
