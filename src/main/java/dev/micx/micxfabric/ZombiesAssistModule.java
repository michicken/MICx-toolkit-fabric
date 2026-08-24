package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** ZombiesAssist runtime facade backed by the complete Forge-compatible configuration. */
public final class ZombiesAssistModule implements Module {
    private static final ZombiesAssistModule INSTANCE = new ZombiesAssistModule();
    private final ZombiesConfig config = new ZombiesConfig();
    private final ZombiesPowerUpTracker powerUpTracker = ZombiesPowerUpTracker.instance();
    private final WaveTempoEstimator waveTempo = new WaveTempoEstimator();
    private final SlimeGrowthTracker slimeGrowth = new SlimeGrowthTracker();
    private final SlimeEcoTracker slimeEco = new SlimeEcoTracker();
    private boolean prevEspLate;
    private boolean prevEcoHidden;
    private long lastExpertUpdateMs;
    private long lastSlimeEcoUpdateMs;
    private long expertSnapshotAt;
    private boolean enabled;
    private Object behaviorLevel;
    private int lastAnnouncedRound;
    private long lastTooNoticeAt;
    private long lastTooCommandAt;
    private long lastTooRushNoticeAt;
    private long tooRushUntil;
    private double tooRushDistance;
    private long blockUntil;
    private long blockDistanceMs;
    private long frAllReadySince;
    private String frDownDigest = "";
    private String lastTooCommand;
    private final ZombiesLsState lsState = new ZombiesLsState();
    private final Map<Integer, TooSample> tooSamples = new HashMap<>();

    private ZombiesAssistModule() {
    }

    public static ZombiesAssistModule instance() {
        return INSTANCE;
    }

    @Override
    public String id() {
        return "zombies_assist";
    }

    @Override
    public boolean defaultEnabled() {
        return true;
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        loadConfig();
        this.enabled = enabled;
        if (!enabled) resetState();
        ModuleStateStore.put(id(), enabled);
    }

    @Override
    public void resetState() {
        ZombiesTracker.instance().reset();
        powerUpTracker.reset();
        waveTempo.reset();
        slimeGrowth.reset();
        slimeEco.reset();
        lastExpertUpdateMs = 0L;
        lastSlimeEcoUpdateMs = 0L;
        expertSnapshotAt = 0L;
        lsState.reset();
        resetBehavior();
    }

    @Override
    public void tick(Minecraft client) {
        if (!enabled) return;
        loadConfig();
        ZombiesTracker tracker = ZombiesTracker.instance();
        tracker.tick(client);
        updateLsState(tracker);
        powerUpTracker.tick(client);
        long now = System.currentTimeMillis();
        if (tracker.isInAlienArcadium() && tracker.round() > 0 && tracker.round() <= 60) {
            if (now - lastSlimeEcoUpdateMs >= 250L) {
                lastSlimeEcoUpdateMs = now;
                slimeEco.tick(client, now);
            }
        } else {
            slimeEco.reset();
            lastSlimeEcoUpdateMs = 0L;
        }
        updateExpertSnapshots(client, tracker, now);
        tickAutomaticBehavior(client, tracker);
    }

    public ZombiesConfig config() {
        loadConfig();
        return config;
    }

    public ZombiesPowerUpTracker powerUpTracker() {
        return powerUpTracker;
    }

    public void saveConfig() {
        loadConfig();
        config.save();
    }

    public boolean overlayEnabled() {
        return config().overlayEnabled;
    }

    public boolean showMobs() {
        return config().showMobs;
    }

    public int topHudXOffset() {
        return config().topHudXOffset;
    }

    public int topHudY() {
        return config().topHudY;
    }

    public float topHudScale() {
        return config().topHudScale;
    }

    public void setOverlayEnabled(boolean value) {
        config().overlayEnabled = value;
        saveConfig();
    }

    public void setShowMobs(boolean value) {
        config().showMobs = value;
        saveConfig();
    }

    public void setTopHudXOffset(int value) {
        config().topHudXOffset = clamp(value, -5_000, 5_000);
        saveConfig();
    }

    public void setTopHudY(int value) {
        config().topHudY = clamp(value, -5_000, 5_000);
        saveConfig();
    }

    public void setTopHudScale(float value) {
        config().topHudScale = clampFinite(value, 1.0f, 0.5f, 2.0f);
        saveConfig();
    }

    private void updateLsState(ZombiesTracker tracker) {
        if (tracker == null || !tracker.isInZombies()) {
            lsState.reset();
            return;
        }
        int down = 0;
        for (String status : tracker.eventState().statuses().values()) {
            if ("down".equals(status)) down++;
        }
        lsState.observe(tracker.round(), down);
    }

    private void updateExpertSnapshots(Minecraft client, ZombiesTracker tracker, long now) {
        if (client == null || client.level == null || !tracker.isInAlienArcadium()
                || tracker.round() <= 0 || tracker.roundStartMs() <= 0L
                || (!config.waveTempo && !config.slimeGrowth)) {
            waveTempo.reset();
            slimeGrowth.reset();
            lastExpertUpdateMs = 0L;
            expertSnapshotAt = 0L;
            return;
        }
        if (now - lastExpertUpdateMs < 250L) return;
        lastExpertUpdateMs = now;
        long elapsed = Math.max(0L, now - tracker.roundStartMs());
        if (config.waveTempo) {
            int[] times = ZombiesRoundData.waveTimes(tracker.round());
            int wave = ZombiesRoundData.waveAt(tracker.round(), elapsed);
            int nextWave = wave < times.length ? wave + 1 : 0;
            long nextMs = wave < times.length
                    ? Math.max(0L, times[wave] * 1_000L - elapsed) : -1L;
            waveTempo.update(now, tracker.round(), nextWave, tracker.zombiesLeft(),
                    countActiveEnemies(client), nextMs);
        } else {
            waveTempo.reset();
        }
        if (config.slimeGrowth && tracker.round() <= 60) slimeGrowth.tick(client, now);
        else slimeGrowth.reset();
        expertSnapshotAt = now;
    }

    private static int countActiveEnemies(Minecraft client) {
        if (client == null || client.level == null) return -1;
        int count = 0;
        try {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive()
                        || living.isRemoved() || living.isDeadOrDying() || living.getHealth() <= 0f) {
                    continue;
                }
                if (living instanceof IronGolem || living instanceof Wolf || living instanceof Enemy) count++;
            }
        } catch (RuntimeException ignored) {
            return -1;
        }
        return count;
    }

    private void tickAutomaticBehavior(Minecraft client, ZombiesTracker tracker) {
        if (client == null || client.level == null || client.player == null) {
            resetBehavior();
            return;
        }
        if (behaviorLevel != client.level) {
            resetBehavior();
            behaviorLevel = client.level;
        }

        int round = tracker.round();
        ZombiesEventState state = tracker.eventState();
        if (state.consumeGameOver()) {
            if (config.postGameStats && state.latches().claimGameOver(round)) {
                sendLocalMessage(client, "Game Over | down " + state.totalDowns()
                        + " | revive " + state.totalRevives() + " | death " + state.totalDeaths()
                        + " | hits " + tracker.hits() + " | crit " + tracker.crits()
                        + " | LR " + tracker.lrUses());
            }
        }
        tracker.finishGameOverSession();
        if (!tracker.isInZombies()) return;

        if (round > 0 && round != lastAnnouncedRound) {
            lastAnnouncedRound = round;
            if (config.pcRoundInfo && tracker.isInAlienArcadium()) {
                sendRoundAnnouncement(client, round);
            }
        }

        if (config.autoNotices) { emitStatusNotices(client, state); autoNotices(client); }
        if (config.blockAlert) updateBlockAlert(client, tracker);
        else {
            blockUntil = 0L;
            blockDistanceMs = 0L;
        }
        if (config.tooAlert || config.pcRoundInfo) scanToo(client, tracker);
    }

    private void autoNotices(Minecraft client) {
        if (!config.autoNotices) return;
        int round = ZombiesTracker.instance().round();
        boolean espLate = false;
        try { var m = ModuleRuntime.get("esp"); if (m != null && m.enabled() && m instanceof EspModule em) espLate = em.active() && round >= 60; } catch (Throwable ignored) {}
        if (espLate && !prevEspLate) sendLocalMessage(client, "ESP \u5df2\u5207\u540e\u671f\u6a21\u5f0f \u2014\u2014 r60 \u8d77\u4ec5\u663e\u793a TOO + \u5de8\u4eba\uff0c\u5269\u4f59\u602a<20 \u65f6\u81ea\u52a8\u6062\u590d\u5168\u90e8\u7ea2\u6846");
        prevEspLate = espLate;
        boolean ecoHidden = slimeEco.hasData() && round > 60;
        if (ecoHidden && !prevEcoHidden) sendLocalMessage(client, "\u517b\u602a\u7ecf\u6d4e HUD \u5df2\u81ea\u52a8\u9690\u85cf \u2014\u2014 r60 \u540e\u8fdb\u5165\u51b2\u523a\u56de\u5408\uff0c\u4e0d\u518d\u517b\u602a");
        prevEcoHidden = ecoHidden;
    }

    private void sendRoundAnnouncement(Minecraft client, int round) {
        StringBuilder message = new StringBuilder("[R").append(round).append("]");
        String mobs = ZombiesRoundData.roundMobs(round);
        String eco = config.ecoHints ? ZombiesRoundData.ecoAdvice(round) : null;
        String hint = ZombiesRoundData.roundTypeHint(round);
        if (mobs != null) message.append(' ').append(mobs);
        if (eco != null) message.append(" | ").append(eco);
        if (hint != null) message.append(" | ").append(hint);
        String value = message.length() > 250 ? message.substring(0, 250) : message.toString();
        client.player.connection.sendCommand("pc " + value);
    }

    private void emitStatusNotices(Minecraft client, ZombiesEventState state) {
        for (Map.Entry<String, String> entry : state.statuses().entrySet()) {
            String name = entry.getKey();
            String status = entry.getValue();
            String token = "notice:" + name + ':' + status;
            if (!state.latches().claim(token)) continue;
            if ("down".equals(status)) {
                sendLocalMessage(client, name + " is down");
            } else if ("dead".equals(status)) {
                sendLocalMessage(client, name + " is dead");
            }
        }
    }

    private void scanToo(Minecraft client, ZombiesTracker tracker) {
        Entity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        Map<Integer, Double> current = new HashMap<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Zombie zombie) || !zombie.isAlive()
                    || !isTooSignature(zombie, config.tooStrictGreen)) continue;
            double distance = client.player.distanceTo(zombie);
            current.put(zombie.getId(), distance);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = zombie;
            }
        }
        tooSamples.keySet().retainAll(current.keySet());
        long now = System.currentTimeMillis();
        if (nearest == null) {
            lastTooCommand = null;
            tooRushUntil = 0L;
            tooRushDistance = 0.0;
            tooSamples.clear();
            return;
        }

        if (config.tooAlert && now - lastTooNoticeAt >= 15_000L) {
            lastTooNoticeAt = now;
            sendLocalMessage(client, "The Old One spotted! " + (int) nearestDistance + "m");
        }
        if (config.pcRoundInfo && nearestDistance >= 3.0 && now - lastTooCommandAt >= 2_000L) {
            String command = "pc TOO " + (int) nearestDistance + "m";
            if (!command.equals(lastTooCommand)) {
                client.player.connection.sendCommand(command);
                lastTooCommand = command;
                lastTooCommandAt = now;
            }
        }

        if (config.tooRushAlert && tracker.zombiesLeft() >= 0 && tracker.zombiesLeft() <= 20) {
            for (Map.Entry<Integer, Double> entry : current.entrySet()) {
                TooSample previous = tooSamples.get(entry.getKey());
                if (previous != null) {
                    long elapsed = Math.max(1L, now - previous.timeMs());
                    double closingBps = (previous.distance() - entry.getValue()) * 1000.0 / elapsed;
                    if (closingBps >= 4.0) {
                        if (now - lastTooRushNoticeAt >= 1_500L) {
                            lastTooRushNoticeAt = now;
                            tooRushUntil = now + 1_500L;
                            tooRushDistance = entry.getValue();
                            sendLocalMessage(client, "TOO rush "
                                    + String.format(Locale.ROOT, "%.1f", entry.getValue()) + "m");
                        }
                        break;
                    }
                }
                tooSamples.put(entry.getKey(), new TooSample(entry.getValue(), now));
            }
        } else {
            tooSamples.clear();
        }
    }

    private static boolean isTooSignature(Zombie zombie, boolean tooStrictGreen) {
        if (!zombie.isBaby()) return false;
        ItemStack helmet = zombie.getItemBySlot(EquipmentSlot.HEAD);
        if (helmet.isEmpty() || !isHeadItem(helmet)) return false;
        ItemStack held = zombie.getMainHandItem();
        boolean diamondSword = !held.isEmpty() && itemPath(held).contains("diamond_sword");
        ItemStack chest = zombie.getItemBySlot(EquipmentSlot.CHEST);
        DyedItemColor dyed = chest.get(DataComponents.DYED_COLOR);
        Integer color = dyed == null ? null : dyed.rgb();
        return ZombieThreatRules.isToo(true, true, diamondSword, color, tooStrictGreen);
    }

    private static boolean isClownSignature(Zombie zombie) {
        if (zombie.isBaby()) return false;
        Integer chest = dyedColor(zombie.getItemBySlot(EquipmentSlot.CHEST));
        Integer legs = dyedColor(zombie.getItemBySlot(EquipmentSlot.LEGS));
        Integer boots = dyedColor(zombie.getItemBySlot(EquipmentSlot.FEET));
        return ZombieThreatRules.isClown(false, chest, legs, boots);
    }

    private static Integer dyedColor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed == null ? null : dyed.rgb();
    }

    private static boolean isHeadItem(ItemStack stack) {
        String path = itemPath(stack);
        return path.endsWith("_skull") || path.endsWith("_head") || "skull".equals(path);
    }

    private static String itemPath(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
    }

    private void resetBehavior() {
        behaviorLevel = null;
        lastAnnouncedRound = 0;
        lastTooNoticeAt = 0L;
        lastTooCommandAt = 0L;
        lastTooRushNoticeAt = 0L;
        tooRushUntil = 0L;
        tooRushDistance = 0.0;
        blockUntil = 0L;
        blockDistanceMs = 0L;
        frAllReadySince = 0L;
        frDownDigest = "";
        lastTooCommand = null;
        tooSamples.clear();
    }

    private record TooSample(double distance, long timeMs) {
    }

    public void drawHud(GuiGraphicsExtractor graphics) {
        if (!enabled || !overlayEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) return;

        ZombiesConfig cfg = config();
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInZombies()) {
            drawCentered(graphics, client, "zombies.top", "ZB: No ZB", cfg.topHudXOffset, cfg.topHudY,
                    cfg.topHudScale, 0xFF98A0AB, true);
            if (cfg.showPowerups) drawPowerUpPanel(graphics, client, tracker, cfg, false);
            return;
        }

        drawTopHud(graphics, client, tracker, cfg);
        drawTacticalHud(graphics, client, tracker, cfg);
        drawAuxiliaryHud(graphics, client, tracker, cfg);
        if (cfg.showPowerups) drawPowerUpPanel(graphics, client, tracker, cfg, true);
        drawSlimeEcoHud(graphics, client, tracker, cfg);
        drawWaveTableHud(graphics, client, tracker, cfg);
    }

    private static void drawTopHud(GuiGraphicsExtractor graphics, Minecraft client,
                                   ZombiesTracker tracker, ZombiesConfig cfg) {
        String round = tracker.round() > 0 ? Integer.toString(tracker.round()) : "?";
        String left = tracker.zombiesLeft() >= 0 ? Integer.toString(tracker.zombiesLeft()) : "?";
        long elapsed = tracker.roundStartMs() <= 0L
                ? 0L : Math.max(0L, System.currentTimeMillis() - tracker.roundStartMs());
        String gameTime = tracker.gameTimeSeconds() >= 0
                ? formatClockSeconds(tracker.gameTimeSeconds()) : "?";
        String text = "Round " + round + " | Time: " + gameTime + " | Left " + left;
        if (tracker.isInAlienArcadium()) text += " | AA";
        drawCentered(graphics, client, "zombies.top", text, cfg.topHudXOffset, cfg.topHudY,
                cfg.topHudScale, 0xFFE8A73E, true);

        drawActivePowerUpsTop(graphics, client, tracker, cfg);
    }

    private static void drawActivePowerUpsTop(GuiGraphicsExtractor graphics, Minecraft client,
                                               ZombiesTracker tracker, ZombiesConfig cfg) {
        long now = System.currentTimeMillis();
        List<String> active = new ArrayList<>();
        int color = ChatMessageStyles.INFO;
        for (Map.Entry<String, PowerUpTimer.Active> entry
                : tracker.eventState().powerUps().activeSnapshot().entrySet()) {
            long remaining = entry.getValue().expiresAt() - now;
            if (remaining <= 0L) continue;
            active.add(powerUpLabel(entry.getKey()) + " " + formatSeconds(remaining));
            if (active.size() == 1) color = powerUpColor(entry.getKey());
        }
        String text = active.isEmpty() ? "——" : String.join(" | ", active);
        drawCentered(graphics, client, "zombies.top", text, cfg.topHudXOffset,
                cfg.topHudY + Math.round(12 * cfg.topHudScale), cfg.topHudScale, color, false);
    }

    private static void drawTacticalHud(GuiGraphicsExtractor graphics, Minecraft client,
                                        ZombiesTracker tracker, ZombiesConfig cfg) {
        List<String> lines = new ArrayList<>();
        if (tracker.isInAlienArcadium()) lines.add("Alien Arcadium");
        else if (!tracker.sidebarTitle().isBlank()) lines.add(tracker.sidebarTitle());

        ThreatCounts threats = scanThreats(client, cfg.tooStrictGreen);
        boolean threatNearCrosshair = cfg.specialThreatHud && specialThreatRound(tracker.round());
        if (cfg.specialThreatHud && !threatNearCrosshair && !threats.empty()) {
            if (threats.too > 0) lines.add("TOO " + threats.too);
            if (threats.giant > 0) lines.add("Giant " + threats.giant);
            if (threats.clown > 0) lines.add("Clown " + threats.clown);
        }

        ZombiesEventState state = tracker.eventState();
        int down = 0;
        int dead = 0;
        for (String status : state.statuses().values()) {
            if ("down".equals(status)) down++;
            if ("dead".equals(status) || "quit".equals(status)) dead++;
        }
        if (down > 0) lines.add("Down players " + down);
        if (dead > 0) lines.add("Dead / quit " + dead);

        if (lines.isEmpty()) return;
        float scaleX = HudLayoutRegistry.scaleX("zombies.tactical", cfg.tacticalHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.tactical", cfg.tacticalHudScale);
        int right = Math.max(4, graphics.guiWidth() - Math.max(0, cfg.tacticalHudRight));
        int y = Math.max(0, cfg.tacticalHudY);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int logicalRight = Math.round(right / scaleX);
            int logicalY = Math.round(y / scaleY);
            for (String line : lines) {
                graphics.text(client.font, Component.literal(line),
                        logicalRight - client.font.width(line), logicalY,
                        0xFFD8DDE3, true);
                logicalY += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void updateBlockAlert(Minecraft client, ZombiesTracker tracker) {
        int round = tracker.round();
        long elapsed = tracker.roundStartMs() <= 0L
                ? -1L : Math.max(0L, System.currentTimeMillis() - tracker.roundStartMs());
        long remaining = elapsed < 0L ? -1L : ZombiesRoundData.blockCountdownMs(round, elapsed);
        if (remaining >= 0L && remaining <= 3_000L) {
            blockDistanceMs = remaining;
            blockUntil = System.currentTimeMillis() + 1_000L;
        } else if (System.currentTimeMillis() >= blockUntil) {
            blockDistanceMs = 0L;
        }
    }

    private void drawAuxiliaryHud(GuiGraphicsExtractor graphics, Minecraft client,
                                  ZombiesTracker tracker, ZombiesConfig cfg) {
        long now = System.currentTimeMillis();
        int round = tracker.round();
        long elapsed = tracker.roundStartMs() <= 0L
                ? 0L : Math.max(0L, now - tracker.roundStartMs());

        ZombiesEventState state = tracker.eventState();
        if (cfg.showEconomy && !cfg.originalScoreboard && tracker.isInZombies() && cfg.overlayEnabled) {
            drawEconomyPanel(graphics, client, tracker, cfg);
        }

        List<String> rightLines = new ArrayList<>();
        long snapshotAge = expertSnapshotAt <= 0L ? 0L : Math.max(0L, now - expertSnapshotAt);
        if (cfg.showMobs && tracker.isInAlienArcadium()) {
            String currentMobs = ZombiesRoundData.roundMobs(round);
            if (currentMobs != null) rightLines.add("Mobs " + currentMobs);
            if (round < 105) {
                String nextMobs = ZombiesRoundData.roundMobs(round + 1);
                if (nextMobs != null) rightLines.add("Next R" + (round + 1) + " " + nextMobs);
            }
        }
        if (tracker.isInAlienArcadium()) {
            String special = specialWaveLine(round, elapsed);
            if (special != null) rightLines.add(special);
            long clearRemaining = ZombiesRoundData.mobClearRemainingMs(round, elapsed);
            if (clearRemaining >= 0L && tracker.zombiesLeft() != 0) {
                rightLines.add("Mob clear " + formatSeconds(clearRemaining));
            }
        }
        if (cfg.waveTempo) {
            String tempo = waveTempoLine(waveTempo.snapshot(), snapshotAge);
            if (tempo == null) tempo = ZombiesRoundData.waveTempo(round, elapsed);
            if (tempo != null) rightLines.add(tempo);
        }
        if (cfg.slimeGrowth) {
            String growth = slimeGrowthLine(slimeGrowth.snapshot(), snapshotAge);
            if (growth != null) rightLines.add(growth);
        }
        drawRightLines(graphics, client, rightLines, cfg, 2);

        if (cfg.frCoach) drawCooldownHud(graphics, client, tracker, cfg, now);
        if (cfg.lsAssist && lsState.shouldShow()) {
            drawLsHud(graphics, client, tracker, cfg, round, elapsed);
        }
        if (cfg.specialThreatHud && specialThreatRound(round)) {
            ThreatCounts threats = scanThreats(client, cfg.tooStrictGreen);
            drawThreatHud(graphics, client, threats, cfg);
        }
        if (cfg.tooRushAlert && now < tooRushUntil) {
            drawCentered(graphics, client, "zombies.too_rush",
                    String.format(Locale.ROOT, "TOO rush %.1fm", tooRushDistance),
                    cfg.tooRushXOffset, cfg.tooRushYOffset + graphics.guiHeight() / 2,
                    cfg.tooRushScale, 0xFFFF5964, true);
        }
        if (cfg.blockAlert && now < blockUntil) {
            drawCentered(graphics, client, "zombies.block_alert",
                    "BLOCK NOW " + formatSeconds(blockDistanceMs),
                    cfg.blockAlertXOffset,
                    graphics.guiHeight() / 3 + cfg.blockAlertYOffset,
                    cfg.blockAlertScale, 0xFFFF5964, true);
        }
        long protection = state.reviveProtectionRemainingMs(now);
        if (protection > 0L) {
            drawCentered(graphics, client, "zombies.top", "Revive protection " + formatSeconds(protection),
                    cfg.topHudXOffset, cfg.topHudY + 24, cfg.topHudScale, 0xFF57B98C, true);
        }
    }

    private static void drawRightLines(GuiGraphicsExtractor graphics, Minecraft client,
                                       List<String> lines, ZombiesConfig cfg, int yOffset) {
        if (lines.isEmpty()) return;
        float scaleX = HudLayoutRegistry.scaleX("zombies.tactical", cfg.tacticalHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.tactical", cfg.tacticalHudScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int right = Math.round((graphics.guiWidth() - cfg.tacticalHudRight) / scaleX);
            int y = Math.round((cfg.tacticalHudY + yOffset) / scaleY);
            for (String line : lines) {
                graphics.text(client.font, Component.literal(line),
                        right - client.font.width(line), y, 0xFFD8DDE3, true);
                y += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawEconomyPanel(GuiGraphicsExtractor graphics, Minecraft client,
                                         ZombiesTracker tracker, ZombiesConfig cfg) {
        Map<String, Integer> gold = tracker.frame().playerGolds();
        if (gold.isEmpty()) return;
        List<String> names = new ArrayList<>(gold.keySet());
        String self = client.player == null ? "" : client.player.getName().getString();
        names.sort((left, right) -> {
            if (left.equals(self)) return -1;
            if (right.equals(self)) return 1;
            int byGold = Integer.compare(gold.getOrDefault(right, 0), gold.getOrDefault(left, 0));
            return byGold != 0 ? byGold : left.compareToIgnoreCase(right);
        });
        if (names.size() > 4) names = new ArrayList<>(names.subList(0, 4));
        EcoRateTracker eco = tracker.ecoRate();
        boolean flash = false;
        Module em = ModuleRuntime.get("eco_rate");
        if (em instanceof EcoRateModule && em.enabled()) {
            EcoRateConfig rc = EcoRateModule.cfg();
            flash = EcoRateModule.flashActive(System.currentTimeMillis(), rc.flashIntervalSec, rc.flashDurationSec);
        }
        float scaleX = HudLayoutRegistry.scaleX("zombies.economy", cfg.ecoHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.economy", cfg.ecoHudScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int right = Math.round((graphics.guiWidth() - cfg.ecoHudRight) / scaleX);
            int y = Math.round((graphics.guiHeight() / 2.0f + cfg.ecoHudCenterYOffset) / scaleY);
            for (String name : names) {
                int gv = gold.getOrDefault(name, 0);
                String gs;
                if (flash) {
                    long rate = name.equals(self) ? eco.selfRate() : eco.rate(name);
                    gs = rate < 0 ? "--" : ZombiesTracker.fmtEco2min(rate) + "/2min";
                } else {
                    gs = String.format(Locale.ROOT, "%,d", gv);
                }
                String line = name + " " + gs;
                int color = flash ? 0xFF55FF55 : 0xFFE8A73E;
                graphics.text(client.font, Component.literal(line),
                        right - client.font.width(line), y, color, true);
                y += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private void drawCooldownHud(GuiGraphicsExtractor graphics, Minecraft client,
                                 ZombiesTracker tracker, ZombiesConfig cfg, long now) {
        String self = client.player == null ? null : client.player.getName().getString();
        if (self == null || !hasFastRevive(client)) {
            resetFrHudVisibility();
            return;
        }
        String selfStatus = tracker.eventState().statuses().get(self);
        if ("down".equals(selfStatus) || "dead".equals(selfStatus) || "quit".equals(selfStatus)) {
            resetFrHudVisibility();
            return;
        }

        List<String> downNames = new ArrayList<>();
        for (Map.Entry<String, String> entry : tracker.eventState().statuses().entrySet()) {
            if ("down".equals(entry.getValue())) downNames.add(entry.getKey());
        }
        downNames.sort(String.CASE_INSENSITIVE_ORDER);
        if (downNames.isEmpty()) {
            resetFrHudVisibility();
            return;
        }

        StringBuilder digest = new StringBuilder();
        for (String name : downNames) digest.append(name.toLowerCase(Locale.ROOT)).append('|');
        String newDigest = digest.toString();
        if (!newDigest.equals(frDownDigest)) {
            frDownDigest = newDigest;
            frAllReadySince = 0L;
        }

        ZombiesEventState.RescueAction rescue = tracker.eventState().localRescueAction(now);
        List<FastReviveDecisionEngine.PlayerInput> inputs = new ArrayList<>();
        for (String name : downNames) {
            long raw = tracker.eventState().frCooldownRemainingMs(name, now);
            boolean active = rescue != null && name.equals(rescue.target());
            inputs.add(new FastReviveDecisionEngine.PlayerInput(name, raw, active,
                    active ? rescue.modeMs() : 0L, active ? rescue.remainingMs() : 0L));
        }

        FastReviveDecisionEngine.Latency latency = currentFrLatency();
        List<FastReviveDecisionEngine.Row> rows = FastReviveDecisionEngine.buildRows(inputs, latency);
        if (rows.isEmpty()) {
            resetFrHudVisibility();
            return;
        }
        boolean allReady = rescue == null;
        for (FastReviveDecisionEngine.Row row : rows) {
            if (row.adjustedCooldownMs > 0L || row.action != FastReviveDecisionEngine.Action.READY) {
                allReady = false;
                break;
            }
        }
        FastReviveDecisionEngine.Visibility visibility = FastReviveDecisionEngine.updateVisibility(
                true, allReady, frAllReadySince, now);
        frAllReadySince = visibility.allReadySince;
        if (!visibility.visible) return;

        String required = "down".equals(tracker.eventState().statuses().get(self)) ? self : null;
        List<FastReviveDecisionEngine.Row> visible =
                FastReviveDecisionEngine.limitRowsIncluding(rows, 4, required);
        float scaleX = HudLayoutRegistry.scaleX("zombies.fast_revive", cfg.frHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.fast_revive", cfg.frHudScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int center = Math.round((graphics.guiWidth() / 2.0f + cfg.frHudXOffset) / scaleX);
            int y = Math.round((graphics.guiHeight() / 2.0f + cfg.frHudYOffset) / scaleY);
            String latencyLine = frLatencyLine(latency);
            int latencyColor = latency.highConfidence ? 0xFF8BD3FF : 0xFFFFC857;
            graphics.text(client.font, Component.literal(latencyLine),
                    center - client.font.width(latencyLine) / 2, y, latencyColor, true);
            y += client.font.lineHeight + 2;
            for (FastReviveDecisionEngine.Row row : visible) {
                int color = row.tone == FastReviveDecisionEngine.Tone.GREEN ? 0xFF55FF55 : 0xFFFFFF55;
                graphics.text(client.font, Component.literal(row.text),
                        center - client.font.width(row.text) / 2, y, color, true);
                y += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static FastReviveDecisionEngine.Latency currentFrLatency() {
        try {
            AimLeadModule.LatencySnapshot snapshot = AimLeadModule.instance().latencySnapshot();
            return new FastReviveDecisionEngine.Latency(snapshot.rttMs, snapshot.jitterMs,
                    snapshot.source, snapshot.highConfidence);
        } catch (Throwable ignored) {
            return new FastReviveDecisionEngine.Latency(0, 0, "unavailable", false);
        }
    }

    private static String frLatencyLine(FastReviveDecisionEngine.Latency latency) {
        if (latency == null || latency.rttMs <= 0
                || latency.source == null || "unavailable".equals(latency.source)) {
            return "FR latency: unavailable";
        }
        if (!latency.highConfidence) {
            return "FR latency: low confidence (" + latency.source + ")";
        }
        return "FR RTT " + latency.rttMs + "ms jitter " + latency.jitterMs + "ms";
    }

    private static boolean hasFastRevive(Minecraft client) {
        if (client == null || client.player == null || client.player.getInventory() == null) return false;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            if (stack == null || stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
            if (name.contains("fast revive") || name.contains("快速救援")) return true;
        }
        return false;
    }

    private void resetFrHudVisibility() {
        frAllReadySince = 0L;
        frDownDigest = "";
    }

    private void drawLsHud(GuiGraphicsExtractor graphics, Minecraft client,
                           ZombiesTracker tracker, ZombiesConfig cfg,
                           int round, long elapsed) {
        List<String> lines = new ArrayList<>();
        int[] waves = ZombiesRoundData.isLsRound(round)
                ? ZombiesRoundData.lsWaveTimes(round) : ZombiesRoundData.waveTimes(round);
        int wave = 0;
        for (int time : waves) if (elapsed >= time * 1_000L) wave++;
        String prefix = ZombiesRoundData.isLsRound(round) ? "LS" : "RESCUE";
        List<String> names = new ArrayList<>(tracker.eventState().statuses().keySet());
        if (client.player != null) {
            String self = client.player.getName().getString();
            if (!names.contains(self)) names.add(self);
        }
        String self = client.player == null ? null : client.player.getName().getString();
        names.sort((left, right) -> {
            if (self != null && self.equals(left)) return -1;
            if (self != null && self.equals(right)) return 1;
            return String.CASE_INSENSITIVE_ORDER.compare(left, right);
        });
        String teamTag = switch (Math.max(1, names.size())) {
            case 2 -> "DUO";
            case 3 -> "TRIO";
            case 4 -> "QUAD";
            default -> Math.max(1, names.size()) + "P";
        };
        lines.add(prefix + " R" + round + " W" + wave + "/" + waves.length + " | " + teamTag);

        Map<String, String> statuses = tracker.eventState().statuses();
        Map<String, Player> players = new HashMap<>();
        if (client.level != null) {
            for (Player player : client.level.players()) {
                if (player != null) players.put(player.getName().getString(), player);
            }
        }
        for (String name : names) {
            Player player = players.get(name);
            String scoreboardStatus = statuses.get(name);
            boolean present = player != null && !player.isRemoved();
            float health = present ? player.getHealth() + player.getAbsorptionAmount() : Float.NaN;
            float maxHealth = present ? player.getMaxHealth() : Float.NaN;
            ZombiesLsStatusRules.Status status = ZombiesLsStatusRules.classify(
                    scoreboardStatus, present, health, maxHealth);
            String suffix = switch (status) {
                case DOWN -> "DOWN";
                case DEAD -> "DEAD";
                case LOW_HP -> "HP " + Math.max(0, Math.round(health)) + "/" + Math.max(0, Math.round(maxHealth));
                case ALIVE -> "HP " + Math.max(0, Math.round(health)) + "/" + Math.max(0, Math.round(maxHealth));
                case UNAVAILABLE -> "?";
            };
            lines.add(name + " " + suffix);
        }
        drawCenteredBlock(graphics, client, "zombies.ls", lines, cfg.lsHudXOffset,
                graphics.guiHeight() / 2 + cfg.lsHudYOffset, cfg.lsHudScale, 0xFFD8DDE3);
    }

    private static void drawThreatHud(GuiGraphicsExtractor graphics, Minecraft client,
                                      ThreatCounts threats, ZombiesConfig cfg) {
        List<String> lines = List.of("TOO: " + threats.too,
                "Giant: " + threats.giant, "Clown: " + threats.clown);
        drawCenteredBlock(graphics, client, "zombies.threat", lines, cfg.threatHudXOffset,
                graphics.guiHeight() / 2 + cfg.threatHudYOffset, cfg.threatHudScale, 0xFFD8DDE3);
    }

    private static void drawCenteredBlock(GuiGraphicsExtractor graphics, Minecraft client, String layoutId,
                                          List<String> lines, int xOffset, int y,
                                          float scale, int color) {
        float scaleX = HudLayoutRegistry.scaleX(layoutId, scale);
        float scaleY = HudLayoutRegistry.scaleY(layoutId, scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f + xOffset, y);
        graphics.pose().scale(scaleX, scaleY);
        try {
            int logicalY = 0;
            for (String line : lines) {
                graphics.text(client.font, Component.literal(line),
                        -client.font.width(line) / 2, logicalY, color, true);
                logicalY += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    static String waveTempoLine(WaveTempoEstimator.Snapshot snapshot) {
        return waveTempoLine(snapshot, 0L);
    }

    static String waveTempoLine(WaveTempoEstimator.Snapshot snapshot, long snapshotAgeMs) {
        if (snapshot == null || !snapshot.valid || snapshot.nextWave <= 0
                || snapshot.nextWaveMs < 0L || snapshot.clearEtaMs < 0L) return null;
        long age = Math.max(0L, snapshotAgeMs);
        long nextWaveMs = Math.max(0L, snapshot.nextWaveMs - age);
        long clearEtaMs = Math.max(0L, snapshot.clearEtaMs - age);
        long overlapMs = clearEtaMs - nextWaveMs;
        String relation = overlapMs > 0L
                ? "OVERLAP +" + formatSeconds(overlapMs)
                : "MARGIN " + formatSeconds(-overlapMs);
        return "W" + snapshot.nextWave + " " + formatSeconds(nextWaveMs)
                + " | CLEAR ETA " + formatSeconds(clearEtaMs) + " | " + relation;
    }

    static String slimeGrowthLine(SlimeGrowthTracker.Snapshot snapshot) {
        return slimeGrowthLine(snapshot, 0L);
    }

    static String slimeGrowthLine(SlimeGrowthTracker.Snapshot snapshot, long snapshotAgeMs) {
        if (snapshot == null || !snapshot.present) return null;
        StringBuilder line = new StringBuilder(snapshot.smallestKind)
                .append(" min ").append(snapshot.smallestStage).append('/')
                .append(snapshot.totalStages);
        if (snapshot.allMax) return line.append(" | ALL MAX").toString();
        long nextGrowthMs = Math.max(0L, snapshot.nextGrowthMs - Math.max(0L, snapshotAgeMs));
        line.append(" | NEXT ").append(formatSeconds(nextGrowthMs));
        if (snapshot.attackReady) line.append(" | READY");
        else line.append(" | grow ").append(snapshot.remainingStages);
        return line.toString();
    }

    private static String specialWaveLine(int round, long elapsed) {
        int[] times = ZombiesRoundData.waveTimes(round);
        if (times.length == 0) return null;
        int wave = ZombiesRoundData.waveAt(round, elapsed);
        int nextWave = wave + 1;
        if (wave > 0) {
            if (containsWave(ZombiesRoundData.tooGiantWaves(round), wave)) {
                return "NOW TOO+GIANT W" + wave;
            }
            if (containsWave(ZombiesRoundData.tooWaves(round), wave)) {
                return "NOW TOO W" + wave;
            }
            if (containsWave(ZombiesRoundData.giantWaves(round), wave)) {
                return "NOW GIANT W" + wave;
            }
        }
        if (nextWave <= times.length) {
            if (containsWave(ZombiesRoundData.tooGiantWaves(round), nextWave)) {
                return "NEXT TOO+GIANT W" + nextWave;
            }
            if (containsWave(ZombiesRoundData.tooWaves(round), nextWave)) {
                return "NEXT TOO W" + nextWave;
            }
            if (containsWave(ZombiesRoundData.giantWaves(round), nextWave)) {
                return "NEXT GIANT W" + nextWave;
            }
        }
        return null;
    }

    private static boolean containsWave(int[] waves, int wave) {
        for (int value : waves) if (value == wave) return true;
        return false;
    }

    private static boolean specialThreatRound(int round) {
        return round == 53 || round == 55 || round == 58 || round == 70
                || round == 80 || round == 90 || round == 100;
    }

    private static String fmtK(int value) {
        return Math.abs(value) >= 1_000
                ? String.format(Locale.ROOT, "%.1fk", value / 1_000.0f)
                : Integer.toString(value);
    }

    private static String signedK(int value) {
        return (value >= 0 ? "+" : "-") + fmtK(Math.abs(value));
    }

    private static int drawPowerUpPanel(GuiGraphicsExtractor graphics, Minecraft client,
                                        ZombiesTracker tracker, ZombiesConfig cfg,
                                        boolean inZombies) {
        float scaleX = HudLayoutRegistry.scaleX("zombies.powerup", cfg.puHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.powerup", cfg.puHudScale);
        int right = Math.max(4, graphics.guiWidth() - Math.max(0, cfg.puHudRight));
        int bottom = Math.max(4, graphics.guiHeight() - Math.max(0, cfg.puHudBottom));
        List<HudLine> lines = new ArrayList<>();
        if (!inZombies) {
            lines.add(new HudLine("Forecast: No ZB", 0xFF98A0AB));
        } else {
            ZombiesPowerUpTracker powerUps = ZombiesAssistModule.instance().powerUpTracker();
            for (PowerUpForecast.Line forecast : PowerUpForecast.lines(
                    tracker.round(), powerUps.maxGroup(), powerUps.instaForecast(tracker.round()))) {
                lines.add(new HudLine(forecast.text(), forecast.color()));
            }
            if (lines.isEmpty()) lines.add(new HudLine("Power-ups: waiting", 0xFF98A0AB));
        }

        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int logicalRight = Math.round(right / scaleX);
            int logicalBottom = Math.round(bottom / scaleY);
            int y = logicalBottom - lines.size() * (client.font.lineHeight + 2);
            for (HudLine line : lines) {
                graphics.text(client.font, Component.literal(line.text()),
                        logicalRight - client.font.width(line.text()), y, line.color(), true);
                y += client.font.lineHeight + 2;
            }
        } finally {
            graphics.pose().popMatrix();
        }
        return lines.size();
    }

    private void drawWaveTableHud(GuiGraphicsExtractor graphics, Minecraft client, ZombiesTracker tracker, ZombiesConfig cfg) {
        if (!cfg.waveTableHud) return;
        int round = tracker.round();
        boolean aa = tracker.isInAlienArcadium();
        int[] times;
        if (aa) {
            times = ZombiesWaveSchedule.waveTimes(round);
        } else {
            WaveTable.ZbMap zbMap = null;
            try { String t = tracker.frame().map(); if (t != null) zbMap = WaveTable.detect(t); } catch (Throwable ignored) {}
            if (zbMap == null) {
                try { String st = tracker.sidebarTitle(); if (st != null) zbMap = WaveTable.detect(st); } catch (Throwable ignored) {}
            }
            times = zbMap == null ? new int[0] : WaveTable.waveTimes(zbMap, round);
        }
        if (times.length == 0) return;
        long elapsed = tracker.roundStartMs() <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - tracker.roundStartMs());
        int wave = aa ? ZombiesWaveSchedule.waveAt(round, elapsed) : WaveTable.waveAt(times, elapsed);
        int nextWave = wave < times.length ? wave + 1 : 0;
        int baseX = 4 + cfg.waveTableHudDx;
        int baseY = 4 + cfg.waveTableHudDy;
        // First line: RoundTimer
        Module rt = ModuleRuntime.get("round_timer");
        int lineH = client.font.lineHeight + 1;
        int y = baseY;
        if (rt instanceof RoundTimerModule && rt.enabled()) {
            String t = RoundTimerModule.renderText(round, tracker.roundStartMs(), System.currentTimeMillis());
            if (t != null) {
                graphics.text(client.font, net.minecraft.network.chat.Component.literal(t), baseX + 13, y, 0xFFE0E0E0, true);
                if (cfg.speedrunEnabled && cfg.showSplitOnWaveTable) {
                    SpeedrunBaseline bl = SpeedrunBaseline.get();
                    if (bl.hasBaseline() && tracker.lastSplitRound() > 0) {
                        long dR = tracker.lastSplitDeltaMs(); long dT = tracker.lastSplitTotalDeltaMs();
                        String sR = SpeedrunBaseline.formatDelta(dR); String sT = SpeedrunBaseline.formatDelta(dT);
                        int cR = SpeedrunBaseline.deltaColor(dR); int cT = SpeedrunBaseline.deltaColor(dT);
                        int wR = client.font.width(t);
                        graphics.text(client.font, net.minecraft.network.chat.Component.literal(" " + sR), baseX + 13 + wR + 4, y, cR, true);
                        String totalStr = " \u03A3" + sT;
                        graphics.text(client.font, net.minecraft.network.chat.Component.literal(totalStr), baseX + 13 + wR + 4 + client.font.width(" " + sR) + 6, y, cT, true);
                    }
                }
                y += lineH;
            }
        }
        for (int i = 0; i < times.length; i++) {
            int w = i + 1; int lineY = y + i * lineH;
            if (w == nextWave) graphics.text(client.font, net.minecraft.network.chat.Component.literal("\u27A4 "), baseX, lineY, 0xFFCC00CC, true);
            int col = WaveTable.waveColor(aa, round, w, nextWave);
            int argb = 0xFF000000 | (col & 0xFFFFFF);
            graphics.text(client.font, net.minecraft.network.chat.Component.literal("W" + w + " " + WaveTable.formatWaveTime(times[i])), baseX + 13, lineY, argb, true);
        }
    }

    private void drawSlimeEcoHud(GuiGraphicsExtractor graphics, Minecraft client,
                                 ZombiesTracker tracker, ZombiesConfig cfg) {
        if (!tracker.isInAlienArcadium() || tracker.round() <= 0 || tracker.round() > 60
                || !slimeEco.hasData()) return;
        String text = "ECO 卡死:" + slimeEco.stuckDeaths()
                + " 亏≈" + fmtK(slimeEco.lostGold());
        float scaleX = HudLayoutRegistry.scaleX("zombies.eco_clock", cfg.ecoClockScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.eco_clock", cfg.ecoClockScale);
        int right = Math.max(4, graphics.guiWidth() - Math.max(0, cfg.ecoClockRight));
        int bottom = Math.max(4, graphics.guiHeight() - Math.max(0, cfg.ecoClockBottom));
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int logicalRight = Math.round(right / scaleX);
            int logicalBottom = Math.round(bottom / scaleY);
            int y = logicalBottom - (client.font.lineHeight + 2);
            graphics.text(client.font, Component.literal(text),
                    logicalRight - client.font.width(text), y, 0xFF55AA55, true);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft client, String layoutId,
                                     String text, int xOffset, int y, float scale, int color, boolean shadow) {
        float scaleX = HudLayoutRegistry.scaleX(layoutId, scale);
        float scaleY = HudLayoutRegistry.scaleY(layoutId, scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f + xOffset, y);
        graphics.pose().scale(scaleX, scaleY);
        try {
            graphics.text(client.font, Component.literal(text),
                    -client.font.width(text) / 2, 0, color, shadow);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    private static String powerUpLabel(String kind) {
        return switch (canonicalPowerUpKind(kind)) {
            case "max" -> "Max Ammo";
            case "insta" -> "Insta Kill";
            case "shopping" -> "Shopping Spree";
            case "dg" -> "Double Gold";
            case "bg" -> "Bonus Gold";
            case "carp" -> "Carpenter";
            default -> kind == null || kind.isBlank() ? "Power-up" : kind;
        };
    }

    private static int powerUpColor(String kind) {
        return switch (canonicalPowerUpKind(kind)) {
            case "max" -> 0xFF4D8CFF;
            case "insta" -> 0xFFFF5964;
            case "shopping" -> 0xFFC05CFF;
            case "dg", "bg" -> 0xFFFFC247;
            case "carp" -> 0xFF57B9D8;
            default -> 0xFFD8DDE3;
        };
    }

    static String canonicalPowerUpKind(String kind) {
        String value = kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
        if (value.contains("max ammo") || value.equals("max")) return "max";
        if (value.contains("insta") || value.contains("instant kill") || value.equals("ins")) return "insta";
        if (value.contains("shopping") || value.equals("ss")) return "shopping";
        if (value.contains("double gold") || value.equals("dg")) return "dg";
        if (value.contains("bonus gold") || value.equals("bg")) return "bg";
        if (value.contains("carp")) return "carp";
        return value;
    }

    private static void sendLocalMessage(Minecraft client, String text) {
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(ChatMessageStyles.notice(text));
        }
    }

    private static String formatSeconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0L, millis) / 1000.0);
    }

    private static String formatClockSeconds(int seconds) {
        int safe = Math.max(0, seconds);
        int hours = safe / 3600;
        int minutes = safe / 60 % 60;
        int remainder = safe % 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
                : String.format(Locale.ROOT, "%d:%02d", safe / 60, remainder);
    }

    private static float safeScale(float value) {
        return Float.isFinite(value) ? Math.max(0.5f, Math.min(2.0f, value)) : 1.0f;
    }

    private static ThreatCounts scanThreats(Minecraft client, boolean tooStrictGreen) {
        ThreatCounts counts = new ThreatCounts();
        if (client.level == null) return counts;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive()) continue;
            if (isExplicitGiantEntity(entity)) {
                counts.giant++;
                continue;
            }
            if (!(entity instanceof Zombie zombie)) continue;
            if (isTooSignature(zombie, tooStrictGreen)) counts.too++;
            else if (isClownSignature(zombie)) counts.clown++;
        }
        return counts;
    }

    private static boolean isExplicitGiantEntity(Entity entity) {
        if (entity == null || entity.getType() == null) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "minecraft".equals(key.getNamespace())
                && "giant".equals(key.getPath());
    }

    private record HudLine(String text, int color) {
    }

    private static final class ThreatCounts {
        int too;
        int giant;
        int clown;

        boolean empty() {
            return too == 0 && giant == 0 && clown == 0;
        }
    }

    private void loadConfig() {
        config.load();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clampFinite(float value, float fallback, float min, float max) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }
}
