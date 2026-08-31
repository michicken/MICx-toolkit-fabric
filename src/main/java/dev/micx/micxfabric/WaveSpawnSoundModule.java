package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/** SST SpawnNotice port: per-wave bell + optional 3-2-1 countdown. */
public final class WaveSpawnSoundModule implements Module {
    private static final WaveSpawnSoundModule INSTANCE = new WaveSpawnSoundModule();

    private boolean enabled = true;
    private boolean aaSound = true;
    private boolean debbSound = true;
    private boolean debbCountdown = false;
    private boolean configLoaded;
    private int lastPlayedWave = -1;
    private int lastPlayedRound = -1;
    private int lastCountdownWave = -1;

    private WaveSpawnSoundModule() {}

    public static WaveSpawnSoundModule instance() { return INSTANCE; }

    @Override public String id() { return "wave_spawn_sound"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) { loadConfig(); enabled = v; ModuleStateStore.put(id(), v); }

    public boolean getAaSound() { loadConfig(); return aaSound; }
    public void setAaSound(boolean v) { aaSound = v; saveConfig(); }
    public boolean getDebbSound() { loadConfig(); return debbSound; }
    public void setDebbSound(boolean v) { debbSound = v; saveConfig(); }
    public boolean getDebbCountdown() { loadConfig(); return debbCountdown; }
    public void setDebbCountdown(boolean v) { debbCountdown = v; saveConfig(); }

    @Override
    public void tick(Minecraft client) {
        if (!enabled || client == null || client.level == null || client.player == null) return;
        ZombiesTracker tracker = ZombiesTracker.instance();
        if (!tracker.isInZombies()) return;
        int round = tracker.round();
        if (round <= 0) return;
        long start = tracker.roundStartMsForWaveHud();
        if (start <= 0) return;
        long elapsed = System.currentTimeMillis() - start;

        // Resolve wave schedule
        boolean isAa = tracker.isInAlienArcadium();
        int[] times;
        if (isAa) {
            times = ZombiesWaveSchedule.waveTimes(round);
        } else {
            WaveTable.ZbMap map = null;
            try {
                String t = tracker.frame().map();
                if (t != null) map = WaveTable.detect(t);
                if (map == null) { String st = tracker.sidebarTitle(); if (st != null) map = WaveTable.detect(st); }
            } catch (Throwable ignored) {}
            times = map == null ? new int[0] : WaveTable.waveTimes(map, round);
        }
        if (times.length == 0) return;

        // Sound gate per map
        boolean allowWaveSound = isAa ? aaSound : debbSound;
        // Wave spawn sound: when elapsed crosses a wave time, play once
        int currentWave = -1;
        for (int i = 0; i < times.length; i++) {
            if (elapsed >= times[i] * 1000L) currentWave = i;
        }
        // currentWave is last spawned wave index (0-based); -1 = none yet
        if (currentWave >= 0 && allowWaveSound) {
            int waveNumber = currentWave + 1; // 1-based
            if (waveNumber != lastPlayedWave || round != lastPlayedRound) {
                lastPlayedWave = waveNumber;
                lastPlayedRound = round;
                boolean isLast = waveNumber == times.length;
                play(isLast ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_PLING.value(),
                        isLast ? 0.5f : 2.0f);
            }
        }
        if (currentWave < 0) { lastPlayedWave = -1; lastCountdownWave = -1; }
        if (round != lastPlayedRound && currentWave < 0) lastPlayedRound = round;

        // DE/BB/TL/PR countdown: 3/2/1 seconds before last wave (only when debbCountdown enabled and not AA... but allow AA too if toggled)
        if (debbCountdown && times.length > 0) {
            long lastWaveMs = times[times.length - 1] * 1000L;
            long remain = lastWaveMs - elapsed;
            // remain in (2000,3000] -> T-3, (1000,2000] -> T-2, (0,1000] -> T-1
            int countdownWave = -1;
            if (remain > 2000 && remain <= 3000) countdownWave = 3;
            else if (remain > 1000 && remain <= 2000) countdownWave = 2;
            else if (remain > 0 && remain <= 1000) countdownWave = 1;
            if (countdownWave > 0 && countdownWave != lastCountdownWave) {
                lastCountdownWave = countdownWave;
                play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
            }
            if (remain <= 0 || remain > 3000) lastCountdownWave = -1;
        }
    }

    public void onRoundChanged(int round) {
        lastPlayedWave = -1;
        lastCountdownWave = -1;
        lastPlayedRound = round;
    }

    private static void play(SoundEvent event, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
        } catch (Throwable ignored) {}
    }

    private static void play(Holder<SoundEvent> holder, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getSoundManager() == null) return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(holder, pitch));
        } catch (Throwable ignored) {}
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("wave-spawn-sound.properties");
        Properties p = ConfigProperties.load(cur, null);
        aaSound = ConfigProperties.bool(p, "aaSound", true);
        debbSound = ConfigProperties.bool(p, "debbSound", true);
        debbCountdown = ConfigProperties.bool(p, "debbCountdown", false);
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("aaSound", Boolean.toString(aaSound));
        p.setProperty("debbSound", Boolean.toString(debbSound));
        p.setProperty("debbCountdown", Boolean.toString(debbCountdown));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("wave-spawn-sound.properties"), p, "MICx WaveSpawnSound configuration");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save WaveSpawnSound configuration", e);
        }
    }
}
