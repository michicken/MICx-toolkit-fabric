package dev.micx.micxfabric;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * ZombieFade —— 半径内敌对怪淡化（v0.2.11 三层对齐，spec FR-3 / AC-7）：
 * <ul>
 *   <li>身体：{@code MixinLivingEntityRenderer} tint alpha（原有路径，保留）。</li>
 *   <li>盔甲四件：{@code MixinEquipmentLayerRenderer} armorCutoutNoCull →
 *       {@link ChamsRenderTypes#fade} translucent + submitModel tint 高位注入 alpha
 *       （对齐 Forge v2.31.2 FadeArmorLayer 语义；手持物品与 Forge 一样不淡化）。</li>
 *   <li>受击暗红：渲染期间临时 hurtTime=0（suppressedHurt，Post/异常/disable 恢复），
 *       对齐 Forge v2.25.3 —— setBrightness 的受伤红在片段级烘焙，混合因子分离不掉。</li>
 * </ul>
 */
public final class ZombieFadeModule implements Module {
    private static final ZombieFadeModule INSTANCE = new ZombieFadeModule();
    private boolean enabled;
    private boolean configLoaded;

    /** 渲染中临时屏蔽 hurtTime 的实体 → 原值（IdentityHashMap 防对象复用；渲染线程顺序配对）。 */
    final Map<LivingEntity, Integer> suppressedHurt = new IdentityHashMap<>();

    private ZombieFadeModule() {}

    public static ZombieFadeModule instance() { return INSTANCE; }

    @Override public String id() { return "zombie_fade"; }
    @Override public boolean defaultEnabled() { return false; }
    @Override public boolean enabled() { return enabled; }
    @Override public void setEnabled(boolean v) {
        loadConfig();
        enabled = v;
        if (!v) restoreAllHurt();
        ModuleStateStore.put(id(), v);
    }

    public double getRadius() { loadConfig(); return ZombieFadeRules.radiusBlocks(); }
    public void setRadius(double r) { ZombieFadeRules.setRadiusBlocks(r); saveConfig(); }

    /** 淡化不透明度（0.05~1，越小越透明），面板滑条可调。 */
    public double getAlpha() { loadConfig(); return ZombieFadeRules.alpha(); }
    public void setAlpha(double v) { ZombieFadeRules.setAlpha((float) v); saveConfig(); }

    /** 当前淡化 alpha（0~1）。 */
    public float alpha() { return ZombieFadeRules.alpha(); }

    public boolean shouldFade(LivingEntity entity) {
        if (!enabled || entity == null) return false;
        if (!(entity instanceof Enemy)) return false;
        if (!entity.isAlive() || entity.isDeadOrDying()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        double dx = mc.player.getX() - entity.getX();
        double dz = mc.player.getZ() - entity.getZ();
        return ZombieFadeRules.shouldFade(true, false, 0) && ZombieFadeRules.isWithinRadiusSq(dx * dx + dz * dz);
    }

    /** 盔甲 tint：原色 rgb + fade alpha 高位（MixinEquipmentLayerRenderer submitModel 用）。 */
    public int armorTint(int originalTint) {
        if (!enabled) return originalTint;
        int rgb = originalTint & 0x00FFFFFF;
        int a = Math.round(ZombieFadeRules.alpha() * 255f);
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | rgb;
    }

    public static int fadedTint(int originalTint) {
        if (!INSTANCE.enabled) return originalTint;
        int rgb = originalTint & 0x00FFFFFF;
        int a = Math.round(ZombieFadeRules.alpha() * 255f);
        if (a < 0) a = 0; if (a > 255) a = 255;
        return (a << 24) | rgb;
    }

    /* ---- hurtTime 屏蔽（渲染 Pre/Post 成对；disable 兜底恢复） ---- */

    /** 渲染前：fade 实体 hurtTime>0 时置 0 并记录原值。返回是否已屏蔽。 */
    public boolean suppressHurt(LivingEntity entity) {
        if (!shouldFade(entity)) return false;
        suppressedHurt.putIfAbsent(entity, entity.hurtTime);
        if (entity.hurtTime > 0) entity.hurtTime = 0;
        return true;
    }

    /** 渲染后：恢复该实体的原始 hurtTime。 */
    public void restoreHurt(LivingEntity entity) {
        Integer saved = suppressedHurt.remove(entity);
        if (saved != null) entity.hurtTime = saved;
    }

    /** disable/resetState 兜底：恢复所有被屏蔽的实体。 */
    void restoreAllHurt() {
        for (Map.Entry<LivingEntity, Integer> e : suppressedHurt.entrySet()) {
            e.getKey().hurtTime = e.getValue();
        }
        suppressedHurt.clear();
    }

    private void loadConfig() {
        if (configLoaded) return;
        configLoaded = true;
        Path cur = FabricRuntime.configPath().resolve("zombie-fade.properties");
        Path leg = FabricRuntime.configPath().getParent().resolve("MICxToolkit_ZombieFade.cfg");
        Properties p = ConfigProperties.load(cur, leg);
        String v = p.getProperty("radiusBlocks");
        if (v == null) v = p.getProperty("zombie_fade.radiusBlocks");
        if (v != null) try { ZombieFadeRules.setRadiusBlocks(Double.parseDouble(v.trim())); } catch (Exception ignored) {}
        String a = p.getProperty("alpha");
        if (a != null) try { ZombieFadeRules.setAlpha(Float.parseFloat(a.trim())); } catch (Exception ignored) {}
    }

    private void saveConfig() {
        Properties p = new Properties();
        p.setProperty("radiusBlocks", Double.toString(ZombieFadeRules.radiusBlocks()));
        p.setProperty("alpha", Float.toString(ZombieFadeRules.alpha()));
        try { AtomicProperties.store(FabricRuntime.configPath().resolve("zombie-fade.properties"), p, "MICx ZombieFade"); } catch (IOException e) { MicxFabric.LOGGER.warn("Unable to save ZombieFade configuration", e); }
    }

    @Override public void resetState() { restoreAllHurt(); }
}
