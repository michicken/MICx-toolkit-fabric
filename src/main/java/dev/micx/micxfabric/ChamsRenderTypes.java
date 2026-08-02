package dev.micx.micxfabric;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.micx.micxfabric.mixin.RenderPipelinesAccess;
import dev.micx.micxfabric.mixin.RenderTypeAccess;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Texture-preserving entity pipeline with depth comparison disabled for Chams. */
public final class ChamsRenderTypes {
    private static final Map<Identifier, RenderType> CACHE = new ConcurrentHashMap<>();
    private static final RenderPipeline PIPELINE = createPipeline();

    private ChamsRenderTypes() {
    }

    public static RenderType entity(Identifier texture) {
        if (texture == null) return null;
        return CACHE.computeIfAbsent(texture, ChamsRenderTypes::createType);
    }

    static void clear() {
        CACHE.clear();
    }

    private static RenderPipeline createPipeline() {
        return RenderPipeline.builder(RenderPipelinesAccess.micx$entitySnippet())
                .withLocation(Identifier.fromNamespaceAndPath("micx-fabric", "pipeline/chams_entity"))
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withCull(false)
                .build();
    }

    private static RenderType createType(Identifier texture) {
        RenderSetup setup = RenderSetup.builder(PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutputTarget(OutputTarget.MAIN_TARGET)
                .createRenderSetup();
        return RenderTypeAccess.micx$create("micx_chams_entity/" + texture, setup);
    }
}
