package dev.micx.micxfabric;

import net.minecraft.world.entity.LivingEntity;

/** 跨 Mixin 共享：佩戴头颅淡化窗口的 ThreadLocal（避免 Mixin 间 public static 互调触发校验失败）。 */
public final class ZombieFadeHeadContext {
    private static final ThreadLocal<LivingEntity> HEAD = new ThreadLocal<>();

    private ZombieFadeHeadContext() {}

    public static void set(LivingEntity entity) {
        if (entity == null) HEAD.remove();
        else HEAD.set(entity);
    }

    public static LivingEntity get() {
        return HEAD.get();
    }

    public static void clear() {
        HEAD.remove();
    }
}
