package dev.micx.micxfabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

/** Shared toggle and validated numeric-row implementation for Zombies sub-pages. */
abstract class ZombiesSubConfigScreen extends ModuleConfigScreen {
    private final List<NumericField> numericFields = new ArrayList<>();

    protected ZombiesSubConfigScreen(Screen parent, String title, String subtitle) {
        super(parent, title, subtitle);
    }

    protected abstract List<ToggleRow> toggleRows();

    protected List<NumericSpec> numericSpecs() {
        return List.of();
    }

    @Override
    protected void rebuildWidgets() {
        numericFields.clear();
        ZombiesConfig config = ZombiesAssistModule.instance().config();
        for (NumericSpec spec : numericSpecs()) {
            EditBox box = new EditBox(font, 0, 0, 100, 20, Component.literal(spec.label));
            box.setMaxLength(spec.decimal ? 12 : 8);
            box.setValue(spec.format(spec.getter.applyAsDouble(config)));
            addTextWidget(box);
            numericFields.add(new NumericField(spec, box));
        }
    }

    protected final void drawParentModuleToggle(GuiGraphicsExtractor graphics, int y,
                                                int mouseX, int mouseY) {
        ZombiesAssistModule module = ZombiesAssistModule.instance();
        graphics.text(font, UiText.shown("启用模块", "Enable"), contentLeft(), y + 4, TEXT);
        graphics.text(font, UiText.shown("打开或关闭 ZombiesAssist 的全部运行逻辑。", "启用或关闭 ZombiesAssist 全部运行逻辑。"),
                contentLeft(), y + 17, TEXT_DIM);
        int toggleX = contentRight() - 44;
        drawToggle(graphics, toggleX, y + 1, 44, 16, module.enabled(),
                isInside(mouseX, mouseY, toggleX, y + 1, 44, 16));
    }

    private int parentToggleY() {
        return contentY() + 21;
    }

    @Override
    protected final void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int y) {
        section(graphics, UiText.shown("模块开关", "MODULE / 模块"), y);
        y += 20;
        drawParentModuleToggle(graphics, y, mouseX, mouseY);
        y += 38;
        section(graphics, UiText.shown("选项", "OPTIONS / 选项"), y);
        y += 20;
        for (ToggleRow row : toggleRows()) {
            graphics.text(font, row.label(), contentLeft(), y + 4, TEXT);
            wrapped(graphics, row.description(), contentLeft(), y + 17, TEXT_DIM,
                    Math.max(20, contentWidth() - 58));
            int toggleX = contentRight() - 44;
            int toggleY = y + 1;
            drawToggle(graphics, toggleX, toggleY, 44, 16, row.value(),
                    isInside(mouseX, mouseY, toggleX, toggleY, 44, 16));
            y += 38;
        }

        if (!numericFields.isEmpty()) {
            graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
            y += 14;
            section(graphics, UiText.shown("HUD 位置与缩放", "HUD POSITION / 位置与缩放"), y);
            y += 20;
            for (NumericField field : numericFields) {
                NumericSpec spec = field.spec;
                graphics.text(font, spec.label, contentLeft(), y + 4, TEXT);
                wrapped(graphics, spec.description, contentLeft(), y + 17, TEXT_DIM,
                        Math.max(20, contentWidth() - 120));
                int boxX = contentRight() - 100;
                field.box.setX(boxX);
                field.box.setY(y);
                field.box.setVisible(y + 20 >= contentTop() && y <= contentBottom());
                y += 34;
            }
        }

        graphics.fill(contentLeft(), y, contentRight(), y + 1, LINE);
        y += 14;
        info(graphics, UiText.shown("离开这个页面时会校验并保存；整数和缩放都按 Forge 的范围限制。", "修改在离开页面时校验并保存；整数和缩放值均按 Forge 范围限制。"), y);
        y += 34;
        setContentHeight(y - contentTop() + scrollOffset());
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int parentY = parentToggleY();
            int toggleX = contentRight() - 44;
            if (isInside(event.x(), event.y(), toggleX, parentY, 44, 16)) {
                ZombiesAssistModule module = ZombiesAssistModule.instance();
                ModuleRuntime.setEnabled(module.id(), !module.enabled());
                return true;
            }

            int y = contentY() + 21 + 58;
            for (ToggleRow row : toggleRows()) {
                int rowToggleX = contentRight() - 44;
                if (isInside(event.x(), event.y(), rowToggleX, y, 44, 16)) {
                    row.setValue(!row.value());
                    ZombiesAssistModule.instance().saveConfig();
                    return true;
                }
                y += 38;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void saveAndClose() {
        List<ParsedNumeric> parsed = new ArrayList<>(numericFields.size());
        for (NumericField field : numericFields) {
            NumericSpec spec = field.spec;
            String value = field.box.getValue().trim();
            try {
                Number number;
                if (spec.decimal) {
                    float parsedValue = Float.parseFloat(value);
                    if (!Float.isFinite(parsedValue)) throw new NumberFormatException();
                    number = Math.max(spec.min, Math.min(spec.max, parsedValue));
                } else {
                    int parsedValue = Integer.parseInt(value);
                    number = Math.max(spec.min, Math.min(spec.max, parsedValue));
                }
                parsed.add(new ParsedNumeric(spec, number));
            } catch (NumberFormatException exception) {
                setErrorMessage(spec.label + " 必须是有效的" + (spec.decimal ? "有限小数" : "整数")
                        + "（" + spec.minText() + "–" + spec.maxText() + "）");
                field.box.setFocused(true);
                return;
            }
        }

        ZombiesConfig config = ZombiesAssistModule.instance().config();
        for (ParsedNumeric value : parsed) {
            value.spec.setter.accept(config, value.value);
        }
        ZombiesAssistModule.instance().saveConfig();
        setErrorMessage(null);
        super.saveAndClose();
    }

    protected final ToggleRow toggle(String label, String description,
                                     BooleanSupplier getter, Consumer<Boolean> setter) {
        return new ToggleRow(label, description, getter, setter);
    }

    protected final NumericSpec integer(String key, String label, String description,
                                        int min, int max, ToDoubleFunction<ZombiesConfig> getter,
                                        BiConsumer<ZombiesConfig, Number> setter) {
        return new NumericSpec(key, label, description, false, min, max, getter, setter);
    }

    protected final NumericSpec decimal(String key, String label, String description,
                                        float min, float max, ToDoubleFunction<ZombiesConfig> getter,
                                        BiConsumer<ZombiesConfig, Number> setter) {
        return new NumericSpec(key, label, description, true, min, max, getter, setter);
    }

    protected static final class ToggleRow {
        private final String label;
        private final String description;
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;

        private ToggleRow(String label, String description,
                          BooleanSupplier getter, Consumer<Boolean> setter) {
            this.label = label;
            this.description = description;
            this.getter = getter;
            this.setter = setter;
        }

        private String label() {
            return label;
        }

        private String description() {
            return description;
        }

        private boolean value() {
            return getter.getAsBoolean();
        }

        private void setValue(boolean value) {
            setter.accept(value);
        }
    }

    protected static final class NumericSpec {
        private final String key;
        private final String label;
        private final String description;
        private final boolean decimal;
        private final double min;
        private final double max;
        private final ToDoubleFunction<ZombiesConfig> getter;
        private final BiConsumer<ZombiesConfig, Number> setter;

        private NumericSpec(String key, String label, String description, boolean decimal,
                            double min, double max, ToDoubleFunction<ZombiesConfig> getter,
                            BiConsumer<ZombiesConfig, Number> setter) {
            this.key = key;
            this.label = label;
            this.description = description;
            this.decimal = decimal;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
        }

        private String format(double value) {
            return decimal ? Float.toString((float) value) : Integer.toString((int) value);
        }

        private String minText() {
            return decimal ? Float.toString((float) min) : Integer.toString((int) min);
        }

        private String maxText() {
            return decimal ? Float.toString((float) max) : Integer.toString((int) max);
        }
    }

    private static final class NumericField {
        private final NumericSpec spec;
        private final EditBox box;

        private NumericField(NumericSpec spec, EditBox box) {
            this.spec = spec;
            this.box = box;
        }
    }

    private record ParsedNumeric(NumericSpec spec, Number value) {
    }
}
