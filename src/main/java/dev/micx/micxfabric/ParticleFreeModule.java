package dev.micx.micxfabric;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * NoParticles —— 粒子全隐：屏蔽所有粒子效果渲染（爆炸/方块破坏/药水烟雾等一切）。
 *
 * <p>26.2 原版"粒子效果显示: 最少"仍会渲染部分粒子（爆炸等关键粒子不受最小档限制）；
 * 本模块直接在 {@code ParticleEngine.add} 漏斗处整段取消（所有客户端粒子入列的唯一入口），
 * 并提前取消 {@code createTrackingEmitter} 追踪发射器，实现 1.8.9 式的零粒子。
 */
public final class ParticleFreeModule implements Module {
    private static final ParticleFreeModule INSTANCE = new ParticleFreeModule();
    private boolean enabled;
    private boolean configLoaded;

    private ParticleFreeModule() {}

    public static ParticleFreeModule instance() { return INSTANCE; }

    @Override public String id() { return "particle_free"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }

    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        ModuleStateStore.put(id(), v);
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("enabled", Boolean.toString(enabled));
        try {
            AtomicProperties.store(FabricRuntime.configPath().resolve("particle-free.properties"), p,
                    "MICx ParticleFree");
        } catch (IOException e) {
            MicxFabric.LOGGER.warn("Unable to save ParticleFree configuration", e);
        }
    }
}
