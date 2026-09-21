package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.jev.HeadlessModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 真无头：headless 模块开启时整帧渲染直接跳过（世界 + HUD + 界面都不画）。
 *
 * <p>拦的是 {@code GameRenderer.render}，不是 {@code Minecraft.runTick}——tick 里还有任务队列、
 * 失焦判定、网络处理，砍掉它游戏就真的不动了；渲染才是纯开销。
 * 跳渲染之后依赖渲染事件的模块（ESP/HUD）自然静默；瞄准旋转改由 AimbotModule 的 tick 分支消费。
 */
@Mixin(GameRenderer.class)
public final class MixinGameRendererHeadless {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void micx$skipRenderWhenHeadless(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callbackInfo) {
        if (HeadlessModule.skipRenderActive()) {
            callbackInfo.cancel();
        }
    }
}
