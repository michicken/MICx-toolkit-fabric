package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * AntiAXE 守卫（Forge AntiAxeGuard 的移植）。
 *
 * <p>领取窗（10.5s）内，若准星射线命中 Lucky Chest 领取区，则拦截本地右键。
 * Forge 用 ASM 注入 Minecraft.rightClickMouse；Fabric 版改为 Mixin startUseItem。</p>
 *
 * <p>26.2 差异：无法遍历方块实体定位箱子，改用 LUCKY CHEST 盔甲架标记本身
 * 锚定领取区（标记紧贴领取箱放置，取距玩家最近标记构建 ±1.3 xz / 高 2.4 的判定盒）。</p>
 */
public final class AntiAxeGuard {

    private static volatile long armedUntil;
    private static volatile long lastBlockedDiagnostic;
    private static volatile ClientLevel claimWorld;
    private static volatile AABB claimBox;
    private static boolean enabled;

    private AntiAxeGuard() {
    }

    record AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    }

    static void setEnabled(boolean value) {
        enabled = value;
        if (!value) disarm("disabled");
    }

    static void arm(long now) {
        armedUntil = now + AntiAxeRules.CLAIM_WINDOW_MS;
        claimWorld = null;   // 强制下个 look 检查重建区域
        claimBox = null;
    }

    static void disarm(String reason) {
        armedUntil = 0L;
    }

    static boolean isArmed() {
        return enabled && armedUntil > 0L
                && System.currentTimeMillis() < armedUntil;
    }

    static long remainingMs() {
        return isArmed() ? Math.max(0L, armedUntil - System.currentTimeMillis()) : 0L;
    }

    /** Mixin 在原版选择目标/发送交互包之前调用。 */
    public static boolean shouldBlockRightClick() {
        Minecraft mc = Minecraft.getInstance();
        if (!AntiAxeAaContext.isInAA(mc)) {
            if (armedUntil > 0L) disarm("not_aa");
            return false;
        }
        if (!isArmed()) return false;
        if (!isLookingAtClaimZone(mc)) return false;
        long now = System.currentTimeMillis();
        if (now - lastBlockedDiagnostic >= 500L) {
            lastBlockedDiagnostic = now;
        }
        return true;
    }

    static boolean isLookingAtClaimZone(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) return false;
        AABB box = claimZone(mc);
        if (box == null) return false;
        net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition(1.0f);
        net.minecraft.world.phys.Vec3 look = mc.player.getViewVector(1.0f);
        double reach = 5.0D;
        net.minecraft.world.phys.Vec3 end = eye.add(look.scale(reach));
        return AntiAxeRules.intersects(eye.x, eye.y, eye.z,
                end.x, end.y, end.z,
                box.minX(), box.minY(), box.minZ(),
                box.maxX(), box.maxY(), box.maxZ());
    }

    /** 领取区：距玩家最近的 PUNCHER/LUCKY CHEST 标记盔甲架 ±1.3 xz、高 2.4。 */
    private static AABB claimZone(Minecraft mc) {
        ClientLevel level = mc.level;
        if (claimWorld == level && claimBox != null) return claimBox;

        List<ArmorStand> markers = new ArrayList<>();
        try {
            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof ArmorStand stand) || stand.isRemoved()) continue;
                Component name = stand.getCustomName();
                if (name == null) continue;
                String text = name.getString();
                if (text == null) continue;
                String upper = text.replaceAll("\u00a7.", "").toUpperCase(Locale.ROOT);
                if (upper.contains("LUCKY CHEST") || upper.contains("RIGHT-CLICK TO CLAIM")
                        || upper.contains("THE PUNCHER")) {
                    markers.add(stand);
                }
            }
        } catch (RuntimeException ignored) {
        }

        AABB best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (ArmorStand marker : markers) {
            double px = marker.getX() - mc.player.getX();
            double py = marker.getY() - mc.player.getY();
            double pz = marker.getZ() - mc.player.getZ();
            double score = px * px + py * py + pz * pz;
            if (score < bestScore && score <= 3600.0) {   // 60m 内才可信
                bestScore = score;
                double cx = marker.getX();
                double cy = marker.getY();
                double cz = marker.getZ();
                best = new AABB(cx - 1.3, cy - 0.5, cz - 1.3, cx + 1.3, cy + 1.9, cz + 1.3);
            }
        }
        claimWorld = level;
        claimBox = best;
        return best;
    }
}
