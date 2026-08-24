package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;

public final class FullbrightModule implements Module {
    private static final FullbrightModule INSTANCE = new FullbrightModule();
    private boolean enabled;
    private Double savedGamma;
    private boolean configLoaded;

    private FullbrightModule() {}

    public static FullbrightModule instance() { return INSTANCE; }

    @Override public String id() { return "fullbright"; }
    @Override public boolean defaultEnabled() { return true; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        if (v == enabled) return;
        enabled = v;
        ModuleStateStore.put(id(), v);
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        if (v) {
            savedGamma = mc.options.gamma().get();
            mc.options.gamma().set(1.0d);
        } else if (savedGamma != null) {
            mc.options.gamma().set(savedGamma);
            savedGamma = null;
        }
    }

    @Override public void tick(Minecraft client) {
        if (!enabled || client == null || client.options == null) return;
        if (client.player == null || client.level == null) {
            if (savedGamma != null) client.options.gamma().set(savedGamma);
            return;
        }
        double g = client.options.gamma().get();
        if (g != 1.0d) {
            if (savedGamma == null) savedGamma = g;
            client.options.gamma().set(1.0d);
        }
    }

    @Override public void resetState() {}

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
    }
}
