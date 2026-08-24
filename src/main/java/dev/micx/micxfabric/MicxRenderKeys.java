package dev.micx.micxfabric;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.world.entity.LivingEntity;

/** Shared render-state → living-entity association used by the renderer mixins. */
public final class MicxRenderKeys {
    public static final RenderStateDataKey<LivingEntity> ENTITY =
            RenderStateDataKey.create(() -> "micx_entity");

    private MicxRenderKeys() {
    }
}
