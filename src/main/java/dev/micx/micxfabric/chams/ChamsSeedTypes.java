package dev.micx.micxfabric.chams;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.micx.micxfabric.mixin.RenderPipelinesAccess;
import net.minecraft.client.renderer.BindGroupLayouts;
import dev.micx.micxfabric.mixin.RenderTypeAccess;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Chams v6：深度种子双通道（FairCauth/Hypixel-Zombies-Mod 同款设计，26.2 反向 Z）。
 *
 * 26.2 使用反向 Z（越近深度越大，vanilla 深度测试为 GREATER_THAN_OR_EQUAL）。
 * 此前 v1-v4 用 LEQUAL 方向完全反了——保留的是最远片元，这就是
 * "看到背面/后方身体遮挡前方手臂/上下半身脱节"的总根因。
 *
 * 双通道：
 * 1. 种子通道（本类型）：LESS_THAN + 写深度 + 关面剔除。只有比世界已有的深度更小
 *    （=更远，即被墙挡住）的片元能通过并写入深度——把实体被遮挡部分的最远表面
 *    深度种进主目标深度缓冲；
 * 2. vanilla 通道：外层正常提交用原版 GEQUAL 渲染，实体最近表面 ≥ 种子深度，
 *    从后向前重建出正确表面——穿墙部分自然可见，自遮挡 100% 原生正确。
 *
 * 全程在主目标内：无独立 RenderTarget、无合成 pass、无深度偏移数值，
 * GL / Vulkan 双端同一条代码路径。
 */
public final class ChamsSeedTypes {
    /** 种子提交窗口标记。渲染单线程，普通静态即可（Mixin 不可持有非私有 static 字段）。 */
    public static boolean ACTIVE = false;

    private static final RenderPipeline PIPELINE =
            RenderPipeline.builder(RenderPipelinesAccess.micx$entitySnippet())
                    .withLocation(Identifier.fromNamespaceAndPath("micx-fabric", "pipeline/chams_depth_seed"))
                    .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                    .withShaderDefine("PER_FACE_LIGHTING")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN, true))
                    .build();

    private static final Function<Identifier, RenderType> SEED = Util.memoize(texture ->
            RenderTypeAccess.micx$create("micx_chams_depth_seed",
                    RenderSetup.builder(PIPELINE)
                            .withTexture("Sampler0", texture)
                            .useLightmap()
                            .useOverlay()
                            .setOutline(RenderSetup.OutlineProperty.NONE)
                            .createRenderSetup()));

    private ChamsSeedTypes() {}

    public static RenderType seedType(Identifier texture) {
        return SEED.apply(texture);
    }
}
