package dev.micx.micxfabric.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code KeyMapping.key} 是 protected 的「当前绑定」字段，没有公开 getter。
 * 连点必须按当前绑定注入：玩家左右键互换后，默认键在 KeyMapping.MAP 里已经属于别的动作。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccess {
    @Accessor("key")
    InputConstants.Key micx$currentKey();
}
