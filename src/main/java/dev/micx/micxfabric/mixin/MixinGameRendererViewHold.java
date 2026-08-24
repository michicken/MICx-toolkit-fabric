package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.ViewHoldModule;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ViewHold Pitch Mirror 的渲染帧包裹（26.2 无 Forge RenderTickEvent）：
 * renderLevel HEAD 取反 pitch → 相机与实体模型同帧生效；RETURN 恢复。
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRendererViewHold {

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void micx$mirrorStart(CallbackInfo ci) {
        ViewHoldModule.instance().renderLevelStart();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void micx$mirrorEnd(CallbackInfo ci) {
        ViewHoldModule.instance().renderLevelEnd();
    }
}
