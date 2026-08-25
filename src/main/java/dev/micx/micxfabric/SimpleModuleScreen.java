package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * 通用模块配置页：Enable 开关 + 若干 toggle / 数值行。
 * 给轻量模块补配置入口用，避免每个模块手写一个 screen。
 */
public final class SimpleModuleScreen extends ModuleConfigScreen {

    /** 一行控件：toggle 或数值（int/double）。 */
    public static final class Row {
        final String label;
        final String desc;
        final BooleanSupplier boolGet;
        final Consumer<Boolean> boolSet;
        final IntSupplier intGet;
        final IntConsumer intSet;
        final DoubleSupplier doubleGet;
        final DoubleConsumer doubleSet;
        final String minMaxHint;
        final boolean isDouble;

        private Row(String label, String desc, BooleanSupplier boolGet, Consumer<Boolean> boolSet,
                    IntSupplier intGet, IntConsumer intSet,
                    DoubleSupplier doubleGet, DoubleConsumer doubleSet,
                    String minMaxHint, boolean isDouble) {
            this.label = label;
            this.desc = desc;
            this.boolGet = boolGet;
            this.boolSet = boolSet;
            this.intGet = intGet;
            this.intSet = intSet;
            this.doubleGet = doubleGet;
            this.doubleSet = doubleSet;
            this.minMaxHint = minMaxHint;
            this.isDouble = isDouble;
        }

        public static Row toggle(String label, BooleanSupplier get, Consumer<Boolean> set, String desc) {
            return new Row(label, desc, get, set, null, null, null, null, null, false);
        }

        public static Row integer(String label, IntSupplier get, IntConsumer set,
                                  int min, int max, String desc) {
            return new Row(label, desc, null, null, get, set, null, null, min + "–" + max, false);
        }

        public static Row decimal(String label, DoubleSupplier get, DoubleConsumer set,
                                  double min, double max, String desc) {
            return new Row(label, desc, null, null, null, null, get, set, min + "–" + max, true);
        }
    }

    private final Module module;
    private final List<Row> rows;
    private final List<EditBox> boxes = new ArrayList<>();
    private final List<int[]> toggleRects = new ArrayList<>();

    public SimpleModuleScreen(Screen parent, Module module, String title, String subtitle, List<Row> rows) {
        super(parent, title, subtitle);
        this.module = module;
        this.rows = rows;
    }

    @Override
    protected void rebuildWidgets() {
        boxes.clear();
        for (Row row : rows) {
            if (row.boolGet != null) continue;
            EditBox box = new EditBox(font, 0, 0, 84, 18, Component.literal(row.label));
            box.setMaxLength(10);
            box.setValue(row.isDouble
                    ? Double.toString(row.doubleGet.getAsDouble())
                    : Integer.toString(row.intGet.getAsInt()));
            addRenderableWidget(box);
            boxes.add(box);
        }
    }

    @Override
    protected void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        toggleRects.clear();
        nextBoxIndex = 0;
        section(graphics, "STATUS / 状态", y);
        y += 20;
        graphics.text(font, "Enable", contentLeft(), y + 4, TEXT);
        drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
        toggleRects.add(new int[]{contentRight() - 44, y + 1});
        y += 34;
        for (Row row : rows) {
            graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
            y += 12;
            if (row.boolGet != null) {
                graphics.text(font, row.label, contentLeft(), y + 4, TEXT);
                drawToggle(graphics, contentRight() - 44, y + 1, 44, 16, row.boolGet.getAsBoolean(),
                        isInside(mouseX, mouseY, contentRight() - 44, y + 1, 44, 16));
                toggleRects.add(new int[]{contentRight() - 44, y + 1});
                y += 26;
            } else {
                graphics.text(font, row.label, contentLeft(), y + 4, TEXT);
                graphics.text(font, row.minMaxHint == null ? "" : row.minMaxHint,
                        contentRight() - 100 - font.width(row.minMaxHint), y + 6, TEXT_FAINT);
                EditBox box = nextBoxIndex < boxes.size() ? boxes.get(nextBoxIndex++) : null;
                if (box != null) {
                    box.setX(contentRight() - 88);
                    box.setY(y);
                    box.setWidth(84);
                }
                y += 28;
            }
            if (row.desc != null && !row.desc.isEmpty()) {
                y = wrapped(graphics, row.desc, contentLeft(), y, TEXT_DIM, contentWidth()) + 6;
            }
        }
        setContentHeight(y - (contentTop() - scrollOffset()));
    }

    private int nextBoxIndex;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int[] rect : toggleRects) {
                if (isInside(event.x(), event.y(), rect[0], rect[1], 44, 16)) {
                    if (rect == toggleRects.get(0)) {
                        module.setEnabled(!module.enabled());
                    } else {
                        int rowIndex = toggleRects.indexOf(rect) - 1;
                        List<Row> toggles = new ArrayList<>();
                        for (Row row : rows) {
                            if (row.boolGet != null) toggles.add(row);
                        }
                        if (rowIndex >= 0 && rowIndex < toggles.size()) {
                            Row row = toggles.get(rowIndex);
                            row.boolSet.accept(!row.boolGet.getAsBoolean());
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        int boxCursor = 0;
        try {
            for (Row row : rows) {
                if (row.boolGet != null) continue;
                EditBox box = boxes.get(boxCursor++);
                String raw = box.getValue().trim();
                if (row.isDouble) {
                    String[] parts = row.minMaxHint.split("–");
                    double value = Double.parseDouble(raw);
                    if (value < Double.parseDouble(parts[0]) || value > Double.parseDouble(parts[1])) {
                        setErrorMessage(row.label + " 超出范围 " + row.minMaxHint);
                        return;
                    }
                    row.doubleSet.accept(value);
                } else {
                    String[] parts = row.minMaxHint.split("–");
                    int value = Integer.parseInt(raw);
                    if (value < Integer.parseInt(parts[0]) || value > Integer.parseInt(parts[1])) {
                        setErrorMessage(row.label + " 超出范围 " + row.minMaxHint);
                        return;
                    }
                    row.intSet.accept(value);
                }
            }
            super.saveAndClose();
        } catch (NumberFormatException exception) {
            setErrorMessage("数值格式不正确");
        }
    }
}
