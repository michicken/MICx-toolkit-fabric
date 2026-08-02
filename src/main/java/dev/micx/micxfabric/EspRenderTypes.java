package dev.micx.micxfabric;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import dev.micx.micxfabric.mixin.RenderPipelinesAccess;
import dev.micx.micxfabric.mixin.RenderTypeAccess;

/** Isolated through-wall line render type for the ESP submit path. */
public final class EspRenderTypes {
    private static final RenderType ESP_LINES = createEspLines();

    private EspRenderTypes() {
    }

    public static RenderType espLines() {
        return ESP_LINES;
    }

    private static RenderType createEspLines() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccess.micx$linesSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("micx-fabric", "pipeline/esp_lines"))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup();
        return RenderTypeAccess.micx$create("micx_esp_lines", setup);
    }
}
