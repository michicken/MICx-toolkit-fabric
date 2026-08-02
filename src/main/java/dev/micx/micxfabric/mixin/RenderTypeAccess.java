package dev.micx.micxfabric.mixin;

import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the package-private RenderType factory for MICx's isolated line pipeline. */
@Mixin(RenderType.class)
public interface RenderTypeAccess {
    @Invoker("create")
    static RenderType micx$create(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
