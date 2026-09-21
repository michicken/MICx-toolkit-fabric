package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.jev.JevMoveFix;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Baritone's world-space path through the same camera-relative input frame as vanilla. */
@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInput {
    @Shadow
    protected Vec2 moveVector;

    @Inject(method = "tick", at = @At("TAIL"))
    private void micx$correctBaritoneMovement(CallbackInfo callbackInfo) {
        Vec2 corrected = JevMoveFix.correctBaritoneInput((KeyboardInput) (Object) this);
        if (corrected != null) moveVector = corrected;
    }
}
