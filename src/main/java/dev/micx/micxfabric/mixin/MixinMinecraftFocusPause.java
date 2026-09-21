package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.RankUpToolModule;
import dev.micx.micxfabric.jev.HeadlessModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RankUpTool 开启时，切到别的应用不再自动弹暂停界面。
 *
 * <p>26.2 里「窗口失焦自动暂停」只走 {@code Minecraft.pauseIfInactive()}（由 renderFrame 每帧调用，
 * 失焦满 500ms 且 pauseOnLostFocus 开着就 pauseGame(false)）；手动按 ESC 走的是
 * KeyboardHandler → pauseGame，另一条路，所以只掐这里不会把 ESC 菜单弄坏。
 * 改 options.pauseOnLostFocus 的写法要不得——那会写进 options.txt 污染玩家设置。
 */
@Mixin(Minecraft.class)
public final class MixinMinecraftFocusPause {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void micx$keepRankUpRunningWhenUnfocused(CallbackInfo callbackInfo) {
        // 无头模式：窗口是藏起来的、永远没有焦点，一旦弹出暂停界面，模块里那些
        // “screen != null 就跳过”的判定会连带失效。
        if (HeadlessModule.instance().enabled()) {
            callbackInfo.cancel();
            return;
        }
        if (RankUpToolModule.instance().enabled()) callbackInfo.cancel();
    }
}
