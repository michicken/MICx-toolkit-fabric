package dev.micx.micxfabric;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputBindingTest {
    @Test
    void usesForgeMouseEncodingForMatchingAndLabels() {
        InputBinding middle = new InputBinding(-98);
        InputBinding side = new InputBinding(-96);

        assertTrue(middle.matches(new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(2, 0))));
        assertFalse(middle.matches(new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(1, 0))));
        assertTrue(side.matches(new MouseButtonEvent(0.0, 0.0, new MouseButtonInfo(4, 0))));
        assertEquals("Mouse 2", middle.label());
        assertEquals("Mouse 4", side.label());
    }

    @Test
    void recognizesKeyboardAndUnboundCodes() {
        InputBinding binding = new InputBinding(GLFW.GLFW_KEY_V);
        assertTrue(binding.matches(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0)));
        assertFalse(binding.matches(new KeyEvent(GLFW.GLFW_KEY_B, 0, 0)));
        assertFalse(binding.isUnbound());

        binding.setCode(GLFW.GLFW_KEY_UNKNOWN);
        assertTrue(binding.isUnbound());
        assertEquals("未绑定", binding.label());
        assertFalse(binding.matches(new KeyEvent(GLFW.GLFW_KEY_V, 0, 0)));
    }

    @Test
    void adapterNormalizesCodesAndClearsThroughTheRealSetter() {
        InputBinding binding = new InputBinding(GLFW.GLFW_KEY_V);
        AtomicInteger written = new AtomicInteger(Integer.MIN_VALUE);
        ModuleKeybindAdapter adapter = new ModuleKeybindAdapter(
                "input_test", "test", () -> binding, code -> {
                    written.set(code);
                    binding.setCode(code);
                });

        adapter.setKeyCode(-100);
        assertEquals(-100, written.get());
        assertEquals(-100, adapter.keyCode());
        assertEquals("Mouse 0", adapter.keyLabel());

        adapter.setKeyCode(0);
        assertEquals(GLFW.GLFW_KEY_UNKNOWN, written.get());
        assertTrue(binding.isUnbound());
        assertEquals("未绑定", adapter.keyLabel());
    }
}
