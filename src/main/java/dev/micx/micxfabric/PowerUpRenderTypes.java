package dev.micx.micxfabric;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import dev.micx.micxfabric.mixin.RenderPipelinesAccess;
import dev.micx.micxfabric.mixin.RenderTypeAccess;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/** Isolated through-wall Power-up beam pipeline. */
public final class PowerUpRenderTypes {
    private static final RenderType POWER_UP_BEAM = createPowerUpBeam();

    private PowerUpRenderTypes() {
    }

    public static RenderType powerUpBeam() {
        return POWER_UP_BEAM;
    }

    private static RenderType createPowerUpBeam() {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelinesAccess.micx$debugFilledSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("micx-fabric", "pipeline/powerup_beam"))
                .withColorTargetState(new ColorTargetState(
                        com.mojang.blaze3d.pipeline.BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build();
        RenderSetup setup = RenderSetup.builder(pipeline)
                .sortOnUpload()
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup();
        return RenderTypeAccess.micx$create("micx_powerup_beam", setup);
    }
}
