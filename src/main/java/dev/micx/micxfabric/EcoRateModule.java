package dev.micx.micxfabric;

/** EcoRate — right economy panel flashes per-2min pure growth (green) interleaved with gold. */
public final class EcoRateModule implements Module {
    private static final EcoRateModule INSTANCE = new EcoRateModule();
    private static EcoRateConfig cfg;
    private boolean enabled = true;

    private EcoRateModule() {}

    public static EcoRateModule instance() { return INSTANCE; }

    public static synchronized EcoRateConfig cfg() {
        if (cfg == null) { cfg = new EcoRateConfig(); cfg.load(); }
        return cfg;
    }

    @Override public String id() { return "eco_rate"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) { enabled = v; ModuleStateStore.put(id(), v); }

    static boolean flashActive(long now, int intervalSec, int durationSec) {
        if (intervalSec <= 1) return false;
        long d = Math.min(Math.max(1, durationSec), intervalSec - 1);
        return (now % (intervalSec * 1000L)) < d * 1000L;
    }
}
