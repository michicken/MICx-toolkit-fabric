package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** Small, persistence-friendly keyboard/mouse binding used by migrated modules. */
public final class InputBinding {
    private int code;

    public InputBinding(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public boolean isUnbound() {
        return code == GLFW.GLFW_KEY_UNKNOWN || code == 0;
    }

    public boolean matches(KeyEvent event) {
        return code >= 0 && event.key() == code;
    }

    public boolean matches(MouseButtonEvent event) {
        return code < 0 && code + 100 == event.button();
    }

    public boolean down(Minecraft client) {
        if (client == null || client.getWindow() == null || isUnbound()) return false;
        try {
            if (code < 0) return GLFW.glfwGetMouseButton(client.getWindow().handle(), code + 100) == GLFW.GLFW_PRESS;
            return GLFW.glfwGetKey(client.getWindow().handle(), code) == GLFW.GLFW_PRESS;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public String label() {
        if (isUnbound()) return "未绑定";
        return KeyChord.keyName(code);
    }
}
