package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

public interface Module {
    String id();

    boolean enabled();

    void setEnabled(boolean enabled);

    default void tick(Minecraft client) {
    }

    /** Optional primary action key, using legacy LWJGL codes (-100 + mouse button). */
    default InputBinding primaryBinding() {
        return null;
    }

    /** Called on the press edge of the optional primary binding. */
    default void onPrimaryPressed(Minecraft client) {
    }

    /** Called on a press edge, with the Forge-compatible first-enable marker. */
    default void onPrimaryPressed(Minecraft client, boolean newlyEnabled) {
        onPrimaryPressed(client);
    }

    /** Optional secondary action key used by modules with a mode cycle. */
    default InputBinding secondaryBinding() {
        return null;
    }

    /** Called on the press edge of the optional secondary binding. */
    default void onSecondaryPressed(Minecraft client) {
    }

    /** Whether a missing persisted state should use this module's original default. */
    default boolean defaultEnabled() {
        return false;
    }

    /** Called when a module is disabled so held input and transient state can be released. */
    default void resetInput() {
    }

    /** Clears world/client transient state without changing enabled/configuration state. */
    default void resetState() {
        resetInput();
    }
}
