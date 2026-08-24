package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Full-screen editor for the configurable screen-space HUD blocks. */
public final class HudLayoutEditorScreen extends Screen {
    private static final int BG = 0xE60C0E12;
    private static final int PANEL = 0xE614181E;
    private static final int RAISED = 0xFF1B2028;
    private static final int HOVER = 0xFF232A34;
    private static final int LINE = 0xFF2A313B;
    private static final int LINE_HI = 0xFF39424F;
    private static final int TEXT = 0xFFE7EAEE;
    private static final int DIM = 0xFF98A0AB;
    private static final int FAINT = 0xFF5C6572;
    private static final int AMBER = 0xFFE8A73E;
    private static final int ON = 0xFF57B98C;

    private final Screen parent;
    private List<HudLayoutBlock> blocks;
    private HudLayoutBlock selected;
    private HudLayoutBlock dragging;
    private HudLayoutBlock resizing;
    private int dragDx;
    private int dragDy;
    private int resizeOriginX;
    private int resizeOriginY;
    private int bottomY;
    private int backX;
    private int resetX;
    private static final int BUTTON_Y_OFFSET = 5;
    private static final int BUTTON_H = 18;
    private static final int BACK_W = 56;
    private static final int RESET_W = 74;

    public HudLayoutEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        blocks = HudLayoutRegistry.blocks();
        if (selected != null && !blocks.contains(selected)) selected = null;
        dragging = null;
        resizing = null;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (blocks == null) blocks = HudLayoutRegistry.blocks();
        graphics.fill(0, 0, width, 28, PANEL);
        graphics.fill(0, height - 30, width, height, PANEL);
        graphics.fill(0, 27, width, 28, LINE_HI);
        graphics.fill(0, height - 31, width, height - 30, LINE_HI);
        graphics.text(font, "HUD Layout", 12, 8, TEXT, true);
        String state = selected == null ? "拖动 HUD 调整位置" : selected.label() + "  "
                + selected.renderWidth() + " × " + selected.renderHeight();
        graphics.text(font, state, 104, 9, selected == null ? DIM : AMBER);
        graphics.text(font, "预览 · 未进入游戏内容采样", Math.max(12, width - font.width("预览 · 未进入游戏内容采样") - 12),
                9, FAINT);

        for (HudLayoutBlock block : blocks) drawBlock(graphics, block, mouseX, mouseY);

        bottomY = height - 30;
        backX = width - BACK_W - 10;
        resetX = backX - RESET_W - 6;
        drawButton(graphics, "‹ 返回", backX, bottomY + BUTTON_Y_OFFSET, BACK_W, BUTTON_H,
                inside(mouseX, mouseY, backX, bottomY + BUTTON_Y_OFFSET, BACK_W, BUTTON_H));
        drawButton(graphics, "全部复位", resetX, bottomY + BUTTON_Y_OFFSET, RESET_W, BUTTON_H,
                inside(mouseX, mouseY, resetX, bottomY + BUTTON_Y_OFFSET, RESET_W, BUTTON_H));
        String hint = selected == null
                ? "左键拖动 · 右下角手柄自由调整宽/高 · 右键复位单块"
                : "X " + selected.scaleX() + "  Y " + selected.scaleY();
        graphics.text(font, hint, 12, bottomY + 10, selected == null ? DIM : AMBER);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawBlock(GuiGraphicsExtractor graphics, HudLayoutBlock block, int mouseX, int mouseY) {
        int x = block.x(width, height);
        int y = block.y(width, height);
        int w = block.renderWidth();
        int h = block.renderHeight();
        boolean hover = block.contains(mouseX, mouseY, width, height);
        boolean active = block == selected || block == dragging || block == resizing;
        int fill = active ? 0xCC232A34 : hover ? 0xC91F252D : 0xB81B2028;
        graphics.fill(x, y, x + w, y + h, fill);
        outline(graphics, x, y, w, h, active ? AMBER : hover ? LINE_HI : LINE);
        int titleH = Math.min(15, h);
        graphics.fill(x, y, x + w, y + titleH, active ? 0xCC8A6A2A : 0xCC2A313B);
        graphics.text(font, trim(block.label(), w - 10), x + 5, y + 3, active ? TEXT : DIM);
        if (h > 20) graphics.text(font, trim(block.sample(), w - 10), x + 5,
                y + Math.min(h - 10, 22), DIM);
        int hx = x + w - HudLayoutMath.HANDLE_SIZE;
        int hy = y + h - HudLayoutMath.HANDLE_SIZE;
        graphics.fill(hx, hy, x + w, y + h, active ? 0xFFFFFFFF : 0xFFB5BBC3);
        graphics.fill(hx + 2, hy + 2, x + w, hy + 3, active ? AMBER : LINE_HI);
        graphics.fill(hx + 2, hy + 4, x + w, hy + 5, active ? AMBER : LINE_HI);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0 && inside(mouseX, mouseY, backX, bottomY + BUTTON_Y_OFFSET, BACK_W, BUTTON_H)) {
            onClose();
            return true;
        }
        if (event.button() == 0 && inside(mouseX, mouseY, resetX, bottomY + BUTTON_Y_OFFSET, RESET_W, BUTTON_H)) {
            HudLayoutRegistry.resetAll();
            selected = null;
            return true;
        }
        if (event.button() == 1) {
            HudLayoutBlock block = findBlock(mouseX, mouseY);
            if (block != null) {
                block.reset();
                block.save();
                HudLayoutConfig.instance().save();
                selected = block;
                return true;
            }
            return false;
        }
        if (event.button() != 0) return false;
        HudLayoutBlock block = findBlock(mouseX, mouseY);
        if (block == null) {
            selected = null;
            return true;
        }
        selected = block;
        int x = block.x(width, height);
        int y = block.y(width, height);
        if (block.inResizeHandle((int) mouseX, (int) mouseY, width, height)) {
            resizing = block;
            resizeOriginX = x;
            resizeOriginY = y;
        } else {
            dragging = block;
            dragDx = HudLayoutMath.dragOffset(mouseX, x);
            dragDy = HudLayoutMath.dragOffset(mouseY, y);
        }
        setDragging(true);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (event.button() != 0) return false;
        if (resizing != null) {
            resizing.setScaleFromMouse(event.x(), event.y(), width, height, resizeOriginX, resizeOriginY);
            return true;
        }
        if (dragging != null) {
            dragging.setPosition((int) Math.round(event.x()) - dragDx,
                    (int) Math.round(event.y()) - dragDy, width, height);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && (dragging != null || resizing != null)) {
            HudLayoutBlock changed = resizing != null ? resizing : dragging;
            changed.save();
            HudLayoutConfig.instance().save();
            dragging = null;
            resizing = null;
            setDragging(false);
            return true;
        }
        return super.mouseReleased(event);
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
    public void onClose() {
        HudLayoutRegistry.saveAll();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private HudLayoutBlock findBlock(double mouseX, double mouseY) {
        if (blocks == null) return null;
        for (int i = blocks.size() - 1; i >= 0; i--) {
            HudLayoutBlock block = blocks.get(i);
            if (block.contains((int) mouseX, (int) mouseY, width, height)) return block;
        }
        return null;
    }

    private String trim(String value, int maxWidth) {
        if (value == null || maxWidth <= 0) return "";
        return font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, maxWidth, true);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void drawButton(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int h,
                                   boolean hovered) {
        graphics.fill(x, y, x + w, y + h, hovered ? HOVER : RAISED);
        outline(graphics, x, y, w, h, hovered ? LINE_HI : LINE);
        graphics.text(MinecraftHolder.font(graphics), text,
                x + (w - MinecraftHolder.font(graphics).width(text)) / 2, y + 5,
                hovered ? TEXT : DIM);
    }

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static final class MinecraftHolder {
        private static net.minecraft.client.gui.Font font(GuiGraphicsExtractor graphics) {
            return net.minecraft.client.Minecraft.getInstance().font;
        }
    }
}
