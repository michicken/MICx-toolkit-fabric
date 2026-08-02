package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Forge-compatible Zombies ammo tracker.
 *
 * <p>Hypixel exposes reload/jam state through the player's green XP level.
 * The item stack count is deliberately never consulted: during a delayed slot
 * switch it can still describe the previous weapon.</p>
 */
public final class AmmoTracker {
    static final long SETTLE_MS = 450L;
    static final long JAM_MS = 1_600L;
    static final long RELOAD_ACTIONBAR_GRACE_MS = 450L;

    private int lastSlot = -1;
    private long slotChangedAt;
    private String heldTag;
    private int heldLastXp = -1;
    private long xpFlatSince;
    private boolean jammed;
    private String jamTag;

    private boolean reloadActive;
    private long reloadStartedAt;
    private long reloadLastSeenAt;
    private String reloadTag;
    private int reloadSlot = -1;
    private int reloadStartDamage = -1;
    private int reloadLastDamage = -1;
    private int reloadDamageChanges;
    private int reloadLeftClicks;
    private int reloadSlotSwitches;
    private long reloadCount;
    private long outOfAmmoCount;
    private long reloadAttemptCount;
    private long lastAttackPressedAt;
    private boolean attackWasDown;
    private boolean reloadStall;
    private int reloadRttMs;
    private int reloadJitterMs;
    private String reloadLatencySource = "unavailable";
    private boolean reloadLatencyConfident;

    public void reset() {
        lastSlot = -1;
        slotChangedAt = 0L;
        heldTag = null;
        heldLastXp = -1;
        xpFlatSince = 0L;
        jammed = false;
        jamTag = null;
        reloadActive = false;
        reloadStartedAt = 0L;
        reloadLastSeenAt = 0L;
        reloadTag = null;
        reloadSlot = -1;
        reloadStartDamage = -1;
        reloadLastDamage = -1;
        reloadDamageChanges = 0;
        reloadLeftClicks = 0;
        reloadSlotSwitches = 0;
        reloadCount = 0L;
        outOfAmmoCount = 0L;
        reloadAttemptCount = 0L;
        lastAttackPressedAt = 0L;
        attackWasDown = false;
        reloadStall = false;
        reloadRttMs = 0;
        reloadJitterMs = 0;
        reloadLatencySource = "unavailable";
        reloadLatencyConfident = false;
    }

    /** Client-thread adapter: reads only vanilla player state and action keys. */
    public void tick(Minecraft client, ZombiesTracker tracker) {
        if (client == null || client.player == null) return;
        long now = System.currentTimeMillis();
        int slot = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getMainHandItem();
        String tag = gunTag(held == null ? null : cleanName(held));
        boolean attackDown = client.options != null && client.options.keyAttack.isDown();
        boolean useDown = client.options != null && client.options.keyUse.isDown();
        if (attackDown && !attackWasDown) {
            lastAttackPressedAt = now;
            if (reloadActive) reloadLeftClicks++;
        }
        attackWasDown = attackDown;

        if (slot != lastSlot) {
            if (reloadActive && lastSlot >= 0) reloadSlotSwitches++;
            lastSlot = slot;
            slotChangedAt = now;
            heldLastXp = -1;
            xpFlatSince = 0L;
            jammed = false;
        }

        updateReload(tracker, held, tag, slot, now);
        heldTag = tag;
        if (tag == null || "刀".equals(tag)) {
            jammed = false;
            return;
        }
        if (now - slotChangedAt < SETTLE_MS) return;

        int xp = client.player.experienceLevel;
        if (heldLastXp >= 0 && heldLastXp != xp) {
            xpFlatSince = now;
            jammed = false;
        }
        heldLastXp = xp;
        if (useDown && xp > 0) {
            if (xpFlatSince == 0L) xpFlatSince = now;
            if (now - xpFlatSince >= JAM_MS) {
                jammed = true;
                jamTag = tag;
            }
        } else {
            xpFlatSince = now;
            jammed = false;
        }
    }

    private void updateReload(ZombiesTracker tracker, ItemStack held, String tag,
                              int slot, long now) {
        boolean serverReloading = tracker != null && tracker.eventState().reloading();
        boolean damageSignal = held != null && !held.isEmpty() && held.getMaxDamage() > 0
                && held.getDamageValue() > 0
                && (!reloadActive || slot == reloadSlot)
                && ("ZP".equals(tag) || now - lastAttackPressedAt <= 700L);
        if (serverReloading || damageSignal) {
            reloadLastSeenAt = now;
            if (!reloadActive) beginReload(held, tag, slot, now);
        }
        if (!reloadActive) return;

        if (held != null && slot == reloadSlot && held.getMaxDamage() > 0) {
            int damage = held.getDamageValue();
            if (reloadLastDamage >= 0 && damage != reloadLastDamage) reloadDamageChanges++;
            reloadLastDamage = damage;
        }
        long elapsed = Math.max(0L, now - reloadStartedAt);
        long expected = expectedReloadMs(reloadTag);
        reloadStall = elapsed >= expected + 750L + Math.min(1_000L, Math.max(0, reloadRttMs));

        if (!serverReloading && now - reloadLastSeenAt > RELOAD_ACTIONBAR_GRACE_MS) {
            reloadActive = false;
            reloadCount++;
            reloadStall = false;
        }
    }

    private void beginReload(ItemStack held, String tag, int slot, long now) {
        reloadActive = true;
        reloadAttemptCount++;
        reloadStartedAt = now;
        reloadLastSeenAt = now;
        reloadTag = tag;
        reloadSlot = slot;
        reloadStartDamage = held == null || held.isEmpty() ? -1 : held.getDamageValue();
        reloadLastDamage = reloadStartDamage;
        reloadDamageChanges = 0;
        reloadLeftClicks = 0;
        reloadSlotSwitches = 0;
        reloadStall = false;
        AimLeadModule aim = AimLeadModule.instance();
        reloadRttMs = Math.max(0, aim.gameRtt());
        reloadJitterMs = 0;
        reloadLatencySource = aim.pingSource();
        reloadLatencyConfident = reloadRttMs > 0;
    }

    /** Called by the event bridge so actionbar interruptions retain the grace window. */
    public void onReloadActionbar(boolean reloading, long now) {
        if (reloading) {
            reloadLastSeenAt = now;
            if (!reloadActive) beginReload(null, heldTag, lastSlot, now);
        } else if (reloadActive && now - reloadLastSeenAt > RELOAD_ACTIONBAR_GRACE_MS) {
            reloadActive = false;
            reloadCount++;
            reloadStall = false;
        }
    }

    public static String gunTag(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("double barrel") || name.contains("双管")) return "DSG";
        if (lower.contains("pistol")) return "PT";
        if (lower.contains("shotgun")) return "SG";
        if (lower.contains("rainbow")) return "RR";
        if (lower.contains("digger")) return "GD";
        if (lower.contains("zapper") || name.contains("电击")) return "ZP";
        if (lower.contains("knife") || name.contains("刀")) return "刀";
        return null;
    }

    static long expectedReloadMs(String tag) {
        return "ZP".equals(tag) ? 2_000L : 1_500L;
    }

    public boolean isJammed() { return jammed; }
    public String jamTag() { return jamTag; }
    public boolean reloadActive() { return reloadActive; }
    public boolean reloadStall() { return reloadStall; }
    public long reloadCount() { return reloadCount; }
    public long outOfAmmoCount() { return outOfAmmoCount; }
    public long reloadAttemptCount() { return reloadAttemptCount; }
    public String heldTag() { return heldTag; }

    private static String cleanName(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : stack.getHoverName().getString()
                .replaceAll("§.", "").trim();
    }
}
