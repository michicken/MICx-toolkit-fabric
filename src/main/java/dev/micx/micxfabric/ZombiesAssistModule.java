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

    /** 诊断限流：最多打 40 次、每 3s 一次，避免刷屏。定位 HUD 消失用，定位后移除。 */
    private static long micxDiagLastMs;
    private static int micxDiagCount;

    private static boolean micxDiagTick() {
        if (micxDiagCount >= 40) return false;
        long now = System.currentTimeMillis();
        if (now - micxDiagLastMs < 3_000L) return false;
        micxDiagLastMs = now;
        micxDiagCount++;
        return true;
    }

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
    private long tooSpawnUntil;
    private double tooSpawnDistanceVal;
    private final Set<Integer> seenTooIds = new HashSet<>();
    private long blockUntil;
    private long blockDistanceMs;
    private long frAllReadySince;
    private String frDownDigest = "";
    private String lastTooCommand;
    private final ZombiesLsState lsState = new ZombiesLsState();
    private int lastTacticalRound = -1;
    private long lastTacticalElapsedMs = 0L;
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
        long elapsed = Math.max(0L, now - tracker.roundStartMsForWaveHud());
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
                String stats = "Game Over | down " + state.totalDowns()
                        + " | revive " + state.totalRevives() + " | death " + state.totalDeaths();
                // showStats（Hits / Crit 开关）控制命中统计段，与 Forge 口径一致
                if (config.showStats) {
                    stats += " | hits " + tracker.hits() + " | crit " + tracker.crits();
                }
                stats += " | LR " + tracker.lrUses();
                sendLocalMessage(client, stats);
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
        Set<Integer> activeIds = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Zombie zombie) || !zombie.isAlive()
                    || !isTooSignature(zombie, config.tooStrictGreen)) continue;
            activeIds.add(zombie.getId());
            double distance = client.player.distanceTo(zombie);
            current.put(zombie.getId(), distance);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = zombie;
            }
        }
        tooSamples.keySet().retainAll(current.keySet());
        // 仅生成位置警报：每只 TOO 仅在首次出现时判定，走进范围不补报
        if (config.tooSpawnAlert) {
            double threshold = Math.max(5, Math.min(30, config.tooSpawnDistance));
            double thresholdSq = threshold * threshold;
            double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
            boolean triggered = false;
            double triggeredDist = 0;
            Set<Integer> newVisible = new HashSet<>();
            for (Entity entity : client.level.entitiesForRendering()) {
                if (!(entity instanceof Zombie zombie) || !zombie.isAlive()
                        || !isTooSignature(zombie, config.tooStrictGreen)) continue;
                int id = zombie.getId();
                if (seenTooIds.contains(id)) continue;
                newVisible.add(id);
                double dx = zombie.getX() - px, dy = zombie.getY() - py, dz = zombie.getZ() - pz;
                if (dx * dx + dy * dy + dz * dz >= thresholdSq) continue;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!triggered || d < triggeredDist) { triggered = true; triggeredDist = d; }
            }
            seenTooIds.addAll(newVisible);
            seenTooIds.retainAll(activeIds);
            if (triggered) {
                long now = System.currentTimeMillis();
                tooSpawnUntil = now + 3_000L;
                tooSpawnDistanceVal = triggeredDist;
            }
        } else {
            seenTooIds.clear();
            tooSpawnUntil = 0L;
        }
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

    static boolean isTooSignature(Zombie zombie, boolean tooStrictGreen) {
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

    static boolean isClownSignature(Zombie zombie) {
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
        lastTacticalRound = -1;
        lastTacticalElapsedMs = 0L;
        lastTooNoticeAt = 0L;
        lastTooCommandAt = 0L;
        lastTooRushNoticeAt = 0L;
        tooRushUntil = 0L;
        tooRushDistance = 0.0;
        tooSpawnUntil = 0L;
        tooSpawnDistanceVal = 0.0;
        seenTooIds.clear();
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

        // [micx-hud] 周期诊断已静音（0.2.34 排障使命完成；0.2.42 起 -Dmicx.diag=true 才输出）
        if (Boolean.getBoolean("micx.diag") && micxDiagTick()) {
            ZombiesTracker dbg = ZombiesTracker.instance();
            MicxFabric.LOGGER.info("[micx-hud] drawHud enabled={} overlay={} inZb={} round={} goldRows={} "
                            + "showEco={} origSb={} pu={} wave={}",
                    enabled, overlayEnabled(), dbg.isInZombies(), dbg.round(),
                    dbg.frame() == null ? -1 : dbg.frame().playerGolds().size(),
                    config().showEconomy, config().originalScoreboard, config().showPowerups, config().waveTableHud);
        }

        ZombiesConfig cfg = config();
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInZombies()) {
            drawCentered(graphics, client, "zombies.top", "ZB: No ZB", cfg.topHudXOffset, cfg.topHudY,
                    cfg.topHudScale, 0xFF98A0AB, true);
            if (cfg.showPowerups) drawPowerUpPanel(graphics, client, tracker, cfg, false);
            return;
        }

        drawTopHud(graphics, client, tracker, cfg);
        int tacticalLines = drawTacticalHud(graphics, client, tracker, cfg);
        drawAuxiliaryHud(graphics, client, tracker, cfg, tacticalLines);
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
        String text = "§fRound §6" + round + " §7| §fTime:§a" + gameTime + " §7| §fLeft:§c" + left;
        if (tracker.isInAlienArcadium()) text += " §7| §bAA";
        drawCentered(graphics, client, "zombies.top", text, cfg.topHudXOffset, cfg.topHudY,
                cfg.topHudScale, 0xFFE8A73E, true);

        drawActivePowerUpsTop(graphics, client, tracker, cfg);
    }

    private static void drawActivePowerUpsTop(GuiGraphicsExtractor graphics, Minecraft client,
                                               ZombiesTracker tracker, ZombiesConfig cfg) {
        long now = System.currentTimeMillis();
        Map<String, PowerUpTimer.Active> snap = tracker.eventState().powerUps().activeSnapshot();
        // Hypixel 读秒口径：多 PU 同时生效时只显示最长剩余那个，其它不抢行
        Map.Entry<String, PowerUpTimer.Active> best = null;
        long bestRem = -1L;
        for (Map.Entry<String, PowerUpTimer.Active> e : snap.entrySet()) {
            long rem = e.getValue().expiresAt() - now;
            if (rem <= 0L) continue;
            if (rem > bestRem) { bestRem = rem; best = e; }
        }
        if (best == null) {
            drawCentered(graphics, client, "zombies.top", "——", cfg.topHudXOffset,
                    cfg.topHudY + Math.round(12 * cfg.topHudScale), cfg.topHudScale,
                    ChatMessageStyles.INFO, false);
            return;
        }
        drawCentered(graphics, client, "zombies.top",
                powerUpLabel(best.getKey()) + " " + formatSeconds(bestRem),
                cfg.topHudXOffset, cfg.topHudY + Math.round(12 * cfg.topHudScale),
                cfg.topHudScale, powerUpColor(best.getKey()), false);
    }

    /**
     * 右侧战术区 —— Forge 块1(roundLines)/块2(combatLines)/extra 三组 renderBlock 流水。
     * v0.2.11 重构：移除地图名/Down/Dead 行；块1 六行 + 块2 战斗行全部带 § 段色。
     */
    private int drawTacticalHud(GuiGraphicsExtractor graphics, Minecraft client,
                                  ZombiesTracker tracker, ZombiesConfig cfg) {
        int round = tracker.round();
        long now = System.currentTimeMillis();
        // Cal-Title(SST): single authoritative baseline = Round N title (roundStartMs). No entity nudge.
        long titleBase = tracker.roundStartMs();
        long rawElapsed = titleBase <= 0L ? 0L : Math.max(0L, now - titleBase);
        // Monotonic guard per round: elapsed never goes backward (title reset is the only valid jump)
        long elapsed = rawElapsed;
        if (lastTacticalRound == round) elapsed = Math.max(lastTacticalElapsedMs, rawElapsed);
        lastTacticalRound = round;
        lastTacticalElapsedMs = elapsed;
        int[] times = ZombiesRoundData.waveTimes(round);
        int wave = ZombiesRoundData.waveAt(round, elapsed);
        int[] too = ZombiesRoundData.tooWaves(round);
        int[] giant = ZombiesRoundData.giantWaves(round);
        int[] tooGiant = ZombiesRoundData.tooGiantWaves(round);

        // === 块 1：回合（Forge roundLines 逐行）===
        List<String> roundLines = new ArrayList<>();
        roundLines.add(buildRoundLine(round, wave, times.length,
                cfg.showMobs ? ZombiesRoundData.roundMobs(round) : null));

        long snapshotAge = expertSnapshotAt <= 0L ? 0L : Math.max(0L, now - expertSnapshotAt);
        String tempoLine = cfg.waveTempo ? waveTempoLine(waveTempo.snapshot(), snapshotAge) : null;
        if (tempoLine != null) {
            roundLines.add(tempoLine);
        } else if (times.length > 0 && wave < times.length) {
            long nextMs = times[wave] * 1_000L - elapsed;
            roundLines.add(nextMs > 0
                    ? "§7下波倒计时 §f" + formatSeconds(nextMs)
                    : "§8最后一波");
        } else if (times.length > 0) {
            roundLines.add("§8最后一波");
        }

        if (cfg.showMobs) {
            String nextMobs = ZombiesRoundData.roundMobs(round + 1);
            if (nextMobs != null) roundLines.add("§7下回合 §f" + nextMobs);
        }

        if (times.length > 0 && wave >= times.length && tracker.zombiesLeft() != 0) {
            String clearLine = buildClearMobLine(ZombiesRoundData.mobClearRemainingMs(round, elapsed));
            if (clearLine != null) roundLines.add(clearLine);
        }

        String forecast = buildWaveForecastLine(too, giant, tooGiant);
        if (forecast != null) roundLines.add(forecast);

        if (round == 101) {
            roundLines.add("§4§lFINAL BOSS");
        } else if (cfg.showPowerups) {
            String hint = ZombiesRoundData.roundTypeHint(round);
            if (hint != null) roundLines.add(hint);
        }

        // === 块 2：战斗（Forge combatLines）===
        List<String> combatLines = new ArrayList<>();
        boolean threatNearCrosshair = cfg.specialThreatHud && specialThreatRound(round);
        ThreatSnapshot threats = scanThreats(client, cfg.tooStrictGreen);
        if (cfg.specialThreatHud && !threatNearCrosshair && !threats.empty()) {
            String threat = threatLine(client, threats);
            if (threat != null) combatLines.add(threat);
        }
        String nextAlert = buildNextWaveAlert(too, giant, tooGiant, wave, times.length);
        if (nextAlert != null) combatLines.add(nextAlert);
        combatLines.addAll(puDropLines(client, now));

        // === extra：史莱姆成长（保持 Forge extraLines 语义）===
        List<String> extraLines = new ArrayList<>();
        if (cfg.slimeGrowth) {
            String growth = slimeGrowthLine(slimeGrowth.snapshot(), snapshotAge);
            if (growth != null) extraLines.add(growth);
        }

        int blockLines = roundLines.size() + combatLines.size() + extraLines.size();
        float scaleX = HudLayoutRegistry.scaleX("zombies.tactical", cfg.tacticalHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.tactical", cfg.tacticalHudScale);
        int right = Math.max(4, graphics.guiWidth() - Math.max(0, cfg.tacticalHudRight));
        int y = Math.max(0, cfg.tacticalHudY);
        if (micxDiagTick()) {
            MicxFabric.LOGGER.info("[micx-hud] tactical blockLines={} rl={} cl={} el={} round={} "
                            + "right={} y={} sx={} sy={} first={}",
                    blockLines, roundLines.size(), combatLines.size(), extraLines.size(), round,
                    right, y, scaleX, scaleY,
                    roundLines.isEmpty() ? "<empty>" : roundLines.get(0));
        }
        if (blockLines == 0) return 0;
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int logicalRight = Math.round(right / scaleX);
            int logicalY = Math.round(y / scaleY);
            logicalY = renderBlock(graphics, client, roundLines, logicalRight, logicalY);
            logicalY = renderBlock(graphics, client, combatLines, logicalRight, logicalY);
            renderBlock(graphics, client, extraLines, logicalRight, logicalY);
        } finally {
            graphics.pose().popMatrix();
        }
        return blockLines;
    }

    private void updateBlockAlert(Minecraft client, ZombiesTracker tracker) {
        int round = tracker.round();
        long waveStart = tracker.roundStartMs();
        long elapsed = waveStart <= 0L
                ? -1L : Math.max(0L, System.currentTimeMillis() - waveStart);
        long remaining = elapsed < 0L ? -1L : ZombiesRoundData.blockCountdownMs(round, elapsed);
        if (remaining >= 0L && remaining <= 3_000L) {
            blockDistanceMs = remaining;
            blockUntil = System.currentTimeMillis() + 1_000L;
        } else if (System.currentTimeMillis() >= blockUntil) {
            blockDistanceMs = 0L;
        }
    }

    private void drawAuxiliaryHud(GuiGraphicsExtractor graphics, Minecraft client,
                                  ZombiesTracker tracker, ZombiesConfig cfg, int tacticalRenderedLines) {
        long now = System.currentTimeMillis();
        int round = tracker.round();
        long waveStart = tracker.roundStartMs();
        long elapsed = waveStart <= 0L
                ? 0L : Math.max(0L, now - waveStart);

        ZombiesEventState state = tracker.eventState();
        if (cfg.showEconomy && !cfg.originalScoreboard && tracker.isInZombies() && cfg.overlayEnabled) {
            drawEconomyPanel(graphics, client, tracker, cfg);
        }

        // Mobs/下回合/下波倒计时/清怪/tempo/特殊波预警/史莱姆成长已并入 drawTacticalHud 块1/块2/extra（v0.2.11）。

        if (cfg.frCoach) drawCooldownHud(graphics, client, tracker, cfg, now);
        if (cfg.lsAssist && lsState.shouldShow()) {
            drawLsHud(graphics, client, tracker, cfg, round, elapsed);
        }
        if (cfg.specialThreatHud && specialThreatRound(round)) {
            ThreatSnapshot threats = scanThreats(client, cfg.tooStrictGreen);
            drawThreatHud(graphics, client, threats, cfg);
        }
        if (cfg.tooSpawnAlert && now < tooSpawnUntil) {
            String tooSpawnText = String.format(Locale.ROOT, "TOO 生成于 %.1fm!", tooSpawnDistanceVal);
            try {
                String wid = WindowSpawnCounterModule.instance().lastTooWindowId();
                if (wid != null) {
                    String full = WindowSpawnCounterModule.fullNameOf(wid);
                    tooSpawnText = String.format(Locale.ROOT, "TOO 生成于 %s %.1fm!", full, tooSpawnDistanceVal);
                }
            } catch (Throwable ignored) {}
            drawCentered(graphics, client, "zombies.too_spawn",
                    tooSpawnText,
                    cfg.tooSpawnXOffset, graphics.guiHeight() / 4 + cfg.tooSpawnYOffset,
                    cfg.tooSpawnScale, 0xFFFF5964, true);
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

    /**
     * 玩家经济表（Forge renderEcoPanel 对齐）：名字白 / 金币 §e(flash §a) / §7| / 击杀 §c
     * 三列按各行最大像素宽对齐，gap=6，行高 11，≤4 行，自己置顶、金币降序。
     */
    private static void drawEconomyPanel(GuiGraphicsExtractor graphics, Minecraft client,
                                         ZombiesTracker tracker, ZombiesConfig cfg) {
        Map<String, Integer> gold = tracker.frame().playerGolds();
        if (micxDiagTick()) {
            MicxFabric.LOGGER.info("[micx-hud] economy gold={} self={} inGold={} showEco={} origSb={} "
                            + "right={} y={} sx={} sy={}",
                    gold.size(),
                    client.player == null ? "<no-player>" : client.player.getName().getString(),
                    client.player == null ? "-" : gold.containsKey(client.player.getName().getString()),
                    cfg.showEconomy, cfg.originalScoreboard,
                    Math.max(4, graphics.guiWidth() - Math.max(0, cfg.ecoHudRight)),
                    Math.round(graphics.guiHeight() / 2.0f + cfg.ecoHudCenterYOffset),
                    HudLayoutRegistry.scaleX("zombies.economy", cfg.ecoHudScale),
                    HudLayoutRegistry.scaleY("zombies.economy", cfg.ecoHudScale));
        }
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

        // 预计算三列最大宽（Forge nameW/goldW/killW 语义）
        int nameW = 0, goldW = 0, killW = 0;
        List<String[]> rows = new ArrayList<>();
        for (String name : names) {
            int gv = gold.getOrDefault(name, 0);
            String gs;
            if (flash) {
                long rate = name.equals(self) ? eco.selfRate() : eco.rate(name);
                gs = rate < 0 ? "--" : ZombiesTracker.fmtEco2min(rate) + "/2min";
            } else {
                gs = String.format(Locale.ROOT, "%,d", gv);
            }
            int kills = playerKills(client, name);
            String ks = kills < 0 ? "\u2014" : String.valueOf(kills);
            rows.add(new String[]{name, gs, ks});
            nameW = Math.max(nameW, client.font.width(name));
            goldW = Math.max(goldW, client.font.width(gs));
            killW = Math.max(killW, client.font.width(ks));
        }

        float scaleX = HudLayoutRegistry.scaleX("zombies.economy", cfg.ecoHudScale);
        float scaleY = HudLayoutRegistry.scaleY("zombies.economy", cfg.ecoHudScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(scaleX, scaleY);
        try {
            int right = Math.round((graphics.guiWidth() - cfg.ecoHudRight) / scaleX);
            int y = Math.round((graphics.guiHeight() / 2.0f + cfg.ecoHudCenterYOffset) / scaleY);
            int gap = 6;
            int pipeW = client.font.width("|");
            int tableW = nameW + gap + goldW + gap + pipeW + gap + killW;
            int leftX = right - tableW;
            int col2 = leftX + nameW + gap;
            int pipeX = col2 + goldW + gap;
            int col4 = pipeX + pipeW + gap;
            for (String[] row : rows) {
                // 26.2 text() 对 alpha==0 颜色直接丢弃，名字列必须带 FF alpha
                graphics.text(client.font, Component.literal(row[0]), leftX, y, 0xFFFFFFFF, true);
                graphics.text(client.font, Component.literal(row[1]), col2, y,
                        flash ? 0xFF55FF55 : 0xFFE8A73E, true);
                graphics.text(client.font, Component.literal("|"), pipeX, y, 0xFF8A8A8A, true);
                graphics.text(client.font, Component.literal(row[2]), col4, y, 0xFFFF5555, true);
                y += 11;
            }
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** 从 tab 计分板(LIST 目标)读玩家击杀数；无则 -1。不产生副作用。 */
    private static int playerKills(Minecraft client, String name) {
        try {
            if (client.level == null) return -1;
            net.minecraft.world.scores.Scoreboard sb = client.level.getScoreboard();
            net.minecraft.world.scores.Objective objective =
                    sb.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST);
            if (objective == null) return -1;
            for (net.minecraft.world.scores.PlayerScoreEntry entry : sb.listPlayerScores(objective)) {
                if (name.equals(entry.owner())) return entry.value();
            }
        } catch (Exception ignored) {
        }
        return -1;
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
                                      ThreatSnapshot threats, ZombiesConfig cfg) {
        List<String> lines = List.of("\u00a7d\u00a7lTOO: \u00a7f" + threats.tooCount,
                "\u00a72\u00a7lGiant: \u00a7f" + threats.giantCount,
                "\u00a7c\u00a7lClown: \u00a7f" + threats.clownCount);
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
                Component component = LegacyText.of(line);
                graphics.text(client.font, component,
                        -client.font.width(component) / 2, logicalY, color, true);
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
                ? "\u00a7cOVERLAP +" + formatSeconds(overlapMs)
                : "\u00a7aMARGIN " + formatSeconds(-overlapMs);
        return "\u00a7bW" + snapshot.nextWave + " \u00a7f" + formatSeconds(nextWaveMs)
                + " \u00a77| CLEAR ETA \u00a7f" + formatSeconds(clearEtaMs)
                + " \u00a77| " + relation;
    }

    static String slimeGrowthLine(SlimeGrowthTracker.Snapshot snapshot) {
        return slimeGrowthLine(snapshot, 0L);
    }

    static String slimeGrowthLine(SlimeGrowthTracker.Snapshot snapshot, long snapshotAgeMs) {
        if (snapshot == null || !snapshot.present) return null;
        StringBuilder line = new StringBuilder("\u00a7d").append(snapshot.smallestKind)
                .append(" \u00a77\u6700\u5c0f \u00a7f").append(snapshot.smallestStage).append('/')
                .append(snapshot.totalStages);
        if (snapshot.allMax) return line.append(" \u00a77| \u00a7a\u00a7lALL MAX").toString();
        long nextGrowthMs = Math.max(0L, snapshot.nextGrowthMs - Math.max(0L, snapshotAgeMs));
        line.append(" \u00a77| NEXT \u00a7f").append(formatSeconds(nextGrowthMs));
        if (snapshot.attackReady) line.append(" \u00a77| \u00a7a\u00a7l\u53ef\u5f00\u6253");
        else line.append(" \u00a77| \u00a7e\u518d\u957f ").append(snapshot.remainingStages).append(" \u6863");
        return line.toString();
    }

    /* ==================== 块1/块2 纯构建器（Forge roundLines/combatLines 逐行对齐，可单测） ==================== */

    /** 块1 第1行：§6Round:§fN §7| §bwi/t[§8(last)][ §f<mobs>] */
    static String buildRoundLine(int round, int wave, int waveTotal, String mobs) {
        StringBuilder l1 = new StringBuilder("\u00a76Round:\u00a7f").append(round);
        if (waveTotal > 0) {
            l1.append(" \u00a77| \u00a7bw").append(wave).append('/').append(waveTotal);
            if (wave >= waveTotal) l1.append("\u00a78(last)");
        }
        if (mobs != null && !mobs.isBlank()) l1.append(" \u00a7f").append(mobs);
        return l1.toString();
    }

    /** 块1 清怪行：§7清怪 §c|§e M:SS §7后怪物消失；非末段（<=0）返回 null。 */
    static String buildClearMobLine(long clearInMs) {
        if (clearInMs <= 0L) return null;
        String color = clearInMs <= 60_000L ? "\u00a7c" : "\u00a7e";
        long seconds = clearInMs / 1000L;
        return String.format(Locale.ROOT, "\u00a77\u6e05\u602a %s%d:%02d \u00a77\u540e\u602a\u7269\u6d88\u5931",
                color, seconds / 60L, seconds % 60L);
    }

    /** 块1 波次预告行：§d§lTOO§r §7w2,3  §2§lGiant§r §7w4  §5§lTOO+Giant§r §7w5,6；全空返回 null。 */
    static String buildWaveForecastLine(int[] too, int[] giant, int[] tooGiant) {
        if (too.length + giant.length + tooGiant.length == 0) return null;
        StringBuilder sp = new StringBuilder();
        if (too.length > 0) sp.append("\u00a7d\u00a7lTOO\u00a7r \u00a7w")
                .append(ZombiesRoundData.join(too)).append("  ");
        if (giant.length > 0) sp.append("\u00a72\u00a7lGiant\u00a7r \u00a7w")
                .append(ZombiesRoundData.join(giant)).append("  ");
        if (tooGiant.length > 0) sp.append("\u00a75\u00a7lTOO+Giant\u00a7r \u00a7w")
                .append(ZombiesRoundData.join(tooGiant));
        return sp.toString().trim();
    }

    /** 块2 下一波预警：! TOO NEXT WAVE ! / ! GIANT NEXT WAVE !（无预警返回 null）。 */
    static String buildNextWaveAlert(int[] too, int[] giant, int[] tooGiant, int wave, int waveTotal) {
        if (waveTotal <= 0 || wave >= waveTotal) return null;
        int next = wave + 1;
        if (contains(too, next) || contains(tooGiant, next)) return "\u00a7c\u00a7l! TOO NEXT WAVE !";
        if (contains(giant, next)) return "\u00a72\u00a7l! GIANT NEXT WAVE !";
        return null;
    }

    /** 8 方位箭头（Forge directionTo 纯函数版）。 */
    static String directionTo(double dx, double dz, float yaw) {
        double bearing = Math.toDegrees(Math.atan2(-dx, dz));
        float rel = (float) (bearing - yaw);
        rel = ((rel % 360f) + 540f) % 360f - 180f;
        String[] arrows = {"\u2191", "\u2197", "\u2192", "\u2198", "\u2193", "\u2199", "\u2190", "\u2196"};
        return arrows[(Math.round(rel / 45f) + 8) % 8];
    }

    private static boolean contains(int[] waves, int wave) {
        for (int value : waves) if (value == wave) return true;
        return false;
    }

    /** 块2 PU 掉落行（≤2 行，Forge puPriority 优先级 + 60s 存在倒计时，≤15s 红催捡）。 */
    private List<String> puDropLines(Minecraft client, long now) {
        Map<Integer, ZombiesPowerUpTracker.DropVisual> drops = powerUpTracker.dropsSnapshot();
        if (drops.isEmpty() || client.player == null) return List.of();
        List<ZombiesPowerUpTracker.DropVisual> sorted = new ArrayList<>(drops.values());
        sorted.sort((a, b) -> Integer.compare(puPriority(canonicalPowerUpKind(b.kind())),
                puPriority(canonicalPowerUpKind(a.kind()))));
        List<String> lines = new ArrayList<>();
        for (ZombiesPowerUpTracker.DropVisual drop : sorted) {
            if (lines.size() >= 2) break;
            double dx = drop.x() - client.player.getX();
            double dy = drop.y() - client.player.getY();
            double dz = drop.z() - client.player.getZ();
            int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
            String dir = directionTo(dx, dz, client.player.getYRot());
            long left = Math.max(0L, drop.expiresAt() - now);
            String life = (left <= 15_000L ? " \u00a7c" : " \u00a77") + formatSeconds(left);
            lines.add(powerUpLabel(drop.kind())
                    + " \u00a7f" + dist + "m " + dir + life);
        }
        return lines;
    }

    /** Forge puPriority 语义：dg > shopping > max > 其他。 */
    private static int puPriority(String kind) {
        return switch (kind) {
            case "dg" -> 100;
            case "shopping" -> 90;
            case "max" -> 80;
            default -> 0;
        };
    }

    /** Forge renderBlock 语义：右对齐逐行 +11px，块尾 +2px 间隙；返回下一块可用 y。 */
    private static int renderBlock(GuiGraphicsExtractor graphics, Minecraft client,
                                   List<String> lines, int rightX, int y) {
        if (lines == null || lines.isEmpty()) return y;
        int cy = y;
        for (String line : lines) {
            drawRightAligned(graphics, client, line, rightX, cy);
            cy += 11;
        }
        return y + lines.size() * 11 + 2;
    }

    /** 右对齐绘制带 § 段色的行。纯文本量宽定位 + 逐段 literal+color 渲染。
     *  ⚠ 26.2 硬约束（javap 实证）：GuiGraphicsExtractor.text 在 ARGB.alpha(color)==0 时
     *  直接 return 不绘制，且 26.2 已无 Font.adjustColor 自动补 alpha——
     *  所有传给 text 的颜色必须带 0xFF alpha 位（0xFFFFFF 这类 24 位色=全透明）。
     *  故在此统一 |0xFF000000 兜底；LEGACY_COLORS 为 24 位 RGB 由本方法补 alpha。
     *  包内可见：DpsCounterModule 的 RC/GS 行复用本方法。 */
    static void drawRightAligned(GuiGraphicsExtractor graphics, Minecraft client,
                                         String line, int rightX, int y) {
        if (line == null || line.isEmpty()) return;
        String plain = line.replaceAll("\u00a7.", "");
        int x = rightX - client.font.width(plain);
        int color = 0xFFFFFF;
        boolean bold = false;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\u00a7' && i + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(i + 1));
                int nc = legacyColor(code);
                if (nc >= 0) color = nc;
                else if (code == 'l') bold = true;
                else if (code == 'r') { color = 0xFFFFFF; bold = false; }
                i += 2;
                continue;
            }
            int start = i;
            while (i < line.length() && line.charAt(i) != '\u00a7') i++;
            String seg = line.substring(start, i);
            if (seg.isEmpty()) continue;
            net.minecraft.network.chat.MutableComponent comp = Component.literal(seg);
            if (bold) comp = comp.withStyle(net.minecraft.ChatFormatting.BOLD);
            graphics.text(client.font, comp, x, y, color | 0xFF000000, true);
            x += client.font.width(seg);
        }
    }

    private static int legacyColor(char code) {
        int idx = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (idx < 0) return -1;
        return LEGACY_COLORS[idx];
    }

    private static final int[] LEGACY_COLORS = {
            0xFFFFFF, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA,
            0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

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
        long waveStart = tracker.roundStartMs();
        long elapsed = waveStart <= 0L ? 0L : Math.max(0L, System.currentTimeMillis() - waveStart);
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
                        int wR = client.font.width(t);
                        int x = baseX + 13 + wR + 4;
                        int gray = 0xFFAAAAAA;
                        String la = " " + bl.labelA();
                        graphics.text(client.font, net.minecraft.network.chat.Component.literal(la), x, y, gray, true);
                        x += client.font.width(la) + 2;
                        String sR = SpeedrunBaseline.formatDelta(dR);
                        graphics.text(client.font, net.minecraft.network.chat.Component.literal(sR), x, y, SpeedrunBaseline.deltaColor(dR), true);
                        x += client.font.width(sR) + 2;
                        String sT = "\u03A3" + SpeedrunBaseline.formatDelta(dT);
                        graphics.text(client.font, net.minecraft.network.chat.Component.literal(sT), x, y, SpeedrunBaseline.deltaColor(dT), true);
                        x += client.font.width(sT) + 6;
                        if (tracker.lastSplitHasB()) {
                            long dR2 = tracker.lastSplitDelta2Ms(); long dT2 = tracker.lastSplitTotalDelta2Ms();
                            String lb = " " + bl.labelB();
                            graphics.text(client.font, net.minecraft.network.chat.Component.literal(lb), x, y, gray, true);
                            x += client.font.width(lb) + 2;
                            String sR2 = SpeedrunBaseline.formatDelta(dR2);
                            graphics.text(client.font, net.minecraft.network.chat.Component.literal(sR2), x, y, SpeedrunBaseline.deltaColor(dR2), true);
                            x += client.font.width(sR2) + 2;
                            String sT2 = "\u03A3" + SpeedrunBaseline.formatDelta(dT2);
                            graphics.text(client.font, net.minecraft.network.chat.Component.literal(sT2), x, y, SpeedrunBaseline.deltaColor(dT2), true);
                        }
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
        Component component = LegacyText.of(text);
        int width = client.font.width(component);
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f + xOffset, y);
        graphics.pose().scale(scaleX, scaleY);
        try {
            graphics.text(client.font, component, -width / 2, 0, color, shadow);
        } finally {
            graphics.pose().popMatrix();
        }
    }

    /** Forge puLabel 对齐：带 § 段色，Max §9蓝 / SS §5紫 / DG §6金 / Insta §c红 等。 */
    private static String powerUpLabel(String kind) {
        return switch (canonicalPowerUpKind(kind)) {
            case "max" -> "§9Max Ammo";
            case "insta" -> "§cInsta Kill";
            case "shopping" -> "§5Shopping Spree";
            case "dg" -> "§6Double Gold";
            case "bg" -> "§6Bonus Gold";
            case "carp" -> "§eCarpenter";
            default -> kind == null || kind.isBlank() ? "§7Power-up" : "§7" + kind;
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

    private static ThreatSnapshot scanThreats(Minecraft client, boolean tooStrictGreen) {
        ThreatSnapshot snapshot = new ThreatSnapshot();
        if (client.level == null || client.player == null) return snapshot;
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || !entity.isAlive()) continue;
            double distance = client.player.distanceTo(entity);
            if (isExplicitGiantEntity(entity)) {
                snapshot.giantCount++;
                if (distance < snapshot.giantDistance) {
                    snapshot.giantDistance = distance;
                    snapshot.giantNear = entity;
                }
                continue;
            }
            if (!(entity instanceof Zombie zombie)) continue;
            if (isTooSignature(zombie, tooStrictGreen)) {
                snapshot.tooCount++;
                if (distance < snapshot.tooDistance) {
                    snapshot.tooDistance = distance;
                    snapshot.tooNear = entity;
                }
            } else if (isClownSignature(zombie)) {
                snapshot.clownCount++;
                if (distance < snapshot.clownDistance) {
                    snapshot.clownDistance = distance;
                    snapshot.clownNear = entity;
                }
            }
        }
        return snapshot;
    }

    /** 威胁行：TOO/Giant/Clown 数量 + 最近距离 + 方向（Forge threatLine 对齐）。null=无威胁。 */
    private static String threatLine(Minecraft client, ThreatSnapshot snapshot) {
        if (snapshot == null || snapshot.empty()) return null;
        StringBuilder b = new StringBuilder();
        if (snapshot.tooCount > 0) {
            b.append("\u00a7d\u00a7lTOO \u00a7r\u00a7f").append((int) snapshot.tooDistance).append("m ")
                    .append(directionOf(client, snapshot.tooNear)).append(" \u00a77")
                    .append(snapshot.tooCount).append("\u4e2a");
        }
        if (snapshot.giantCount > 0) {
            if (b.length() > 0) b.append(" \u00a78| ");
            b.append("\u00a72\u00a7lGiant \u00a7r\u00a7f").append((int) snapshot.giantDistance).append("m ")
                    .append(directionOf(client, snapshot.giantNear)).append(" \u00a77")
                    .append(snapshot.giantCount).append("\u4e2a");
        }
        if (snapshot.clownCount > 0) {
            if (b.length() > 0) b.append(" \u00a78| ");
            b.append("\u00a7c\u00a7lClown \u00a7r\u00a7f").append((int) snapshot.clownDistance).append("m ")
                    .append(directionOf(client, snapshot.clownNear)).append(" \u00a77")
                    .append(snapshot.clownCount).append("\u4e2a");
        }
        return b.length() == 0 ? null : b.toString();
    }

    /** 8 方位箭头（Forge directionTo 语义）。 */
    private static String directionOf(Minecraft client, Entity target) {
        if (client.player == null || target == null) return "";
        return directionTo(target.getX() - client.player.getX(),
                target.getZ() - client.player.getZ(), client.player.getYRot());
    }

    private static boolean isExplicitGiantEntity(Entity entity) {
        if (entity == null || entity.getType() == null) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "minecraft".equals(key.getNamespace())
                && "giant".equals(key.getPath());
    }

    private record HudLine(String text, int color) {
    }

    /** 威胁快照（Forge ThreatSnapshot 对齐）：计数 + 最近距离 + 最近实体。 */
    static final class ThreatSnapshot {
        int tooCount, giantCount, clownCount;
        Entity tooNear, giantNear, clownNear;
        double tooDistance = 1e9, giantDistance = 1e9, clownDistance = 1e9;

        boolean empty() {
            return tooCount == 0 && giantCount == 0 && clownCount == 0;
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
