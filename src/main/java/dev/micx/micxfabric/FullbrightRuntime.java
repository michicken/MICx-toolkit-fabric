package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;

/** Keeps vanilla gamma valid and applies the built-in fullbright lightmap override. */
public final class FullbrightRuntime {
    public static final float FULLBRIGHT_FACTOR = 1.0f;

    private FullbrightRuntime() {
    }

    public static boolean isEnabled() {
        try { return FullbrightModule.instance().enabled(); } catch (Throwable ignored) { return true; }
    }

    public static void tick(Minecraft client) {
        if (client == null || client.options == null) return;
        if (!isEnabled()) return;
        double gamma = client.options.gamma().get();
        if (gamma != 1.0d) {
            client.options.gamma().set(1.0d);
        }
    }

    public static void apply(LightmapRenderState state) {
        if (!isEnabled()) return;
        if (state == null) return;
        state.blockFactor = FULLBRIGHT_FACTOR;
        state.skyFactor = FULLBRIGHT_FACTOR;
        state.blockLightTint = LightmapRenderStateExtractor.WHITE;
        state.skyLightColor = LightmapRenderStateExtractor.WHITE;
        state.ambientColor = LightmapRenderStateExtractor.WHITE;
        state.brightness = FULLBRIGHT_FACTOR;
    }
}
