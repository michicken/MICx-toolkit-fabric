package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AsrModule;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MixinMinecraftEscape {
    @Inject(method = "handleGlobalKeyPress", at = @At("HEAD"), cancellable = true)
    private void micx$consumeAsrEscape(InputConstants.Key key, boolean hasControlDown, CallbackInfoReturnable<Boolean> cir) {
        if (key.getType() == InputConstants.Type.KEYSYM && key.getValue() == GLFW.GLFW_KEY_ESCAPE
                && AsrModule.instance().consumeEscape()) cir.setReturnValue(true);
    }
}
