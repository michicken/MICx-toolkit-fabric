package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.RemoteShopModule;
import dev.micx.micxfabric.RemoteShopRules;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RemoteShop 开启时，把客户端自己的准星射程抬到服务端允许的范围。
 *
 * <p>两边根本不是一条线：客户端准星用 {@code entityInteractionRange()}（默认 3 格）/
 * {@code blockInteractionRange()}（默认 4.5 格）卡自己，而服务端 26.2 接受的是
 * 眼球到碰撞箱 &lt; (3.0+3.0)=6 格（实体）、&lt; (4.5+1.0)=5.5 格（方块）。
 * 客户端的 3 格比服务端窄这么多，所以这里只是把客户端那条线抬到服务端那条线，
 * 不改任何封包、不做位置造假——超出去的交互本来就会被服务端静默丢包。
 *
 * <p>拾取范围在 {@code LocalPlayer} 里通过这两个方法读取，所以注入 Player 就够了。
 */
@Mixin(Player.class)
public final class MixinPlayerInteractionRange {
    @Inject(method = "blockInteractionRange", at = @At("RETURN"), cancellable = true)
    private void micx$widenBlockRange(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(micx$widen(cir.getReturnValueD()));
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void micx$widenEntityRange(CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(micx$widen(cir.getReturnValueD()));
    }

    /** 只放大不缩小：创造模式或服务端发下来的更大值照样尊重。 */
    private static double micx$widen(double vanilla) {
        if (!RemoteShopModule.instance().enabled()) return vanilla;
        return Math.max(vanilla, RemoteShopRules.CLIENT_RANGE);
    }
}
