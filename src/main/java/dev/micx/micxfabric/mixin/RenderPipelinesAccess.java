package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuses Minecraft's fully-bound line shader snippet for the ESP pipeline. */
@Mixin(targets = "net.minecraft.client.renderer.RenderPipelines")
public interface RenderPipelinesAccess {
    @Accessor("LINES_SNIPPET")
    static RenderPipeline.Snippet micx$linesSnippet() {
        throw new AssertionError();
    }

    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet micx$entitySnippet() {
        throw new AssertionError();
    }

    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet micx$debugFilledSnippet() {
        throw new AssertionError();
    }
}
