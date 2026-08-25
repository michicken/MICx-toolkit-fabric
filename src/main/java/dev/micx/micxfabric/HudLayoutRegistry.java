package dev.micx.micxfabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Registry shared by the editor and HUD drawers. Position fields remain in their owning modules. */
public final class HudLayoutRegistry {
    private static final List<HudLayoutBlock> BLOCKS = new ArrayList<>();
    private static boolean initialized;

    private HudLayoutRegistry() {
    }

    public static synchronized List<HudLayoutBlock> blocks() {
        initialize();
        return Collections.unmodifiableList(BLOCKS);
    }

    public static float scaleX(String id, float moduleScale) {
        return HudLayoutMath.clampScale(moduleScale) * HudLayoutConfig.instance().scaleX(id);
    }

    public static float scaleY(String id, float moduleScale) {
        return HudLayoutMath.clampScale(moduleScale) * HudLayoutConfig.instance().scaleY(id);
    }

    public static void saveAll() {
        initialize();
        for (HudLayoutBlock block : BLOCKS) block.save();
        HudLayoutConfig.instance().save();
    }

    public static void resetAll() {
        initialize();
        for (HudLayoutBlock block : BLOCKS) block.reset();
        saveAll();
    }

    private static void initialize() {
        if (initialized) return;
        initialized = true;
        addZombiesBlocks();
        addLrIndicatorBlock();
        addSprintBlock();
        addTeammateBlock();
        addTeamSyncBlock();
        addToroBlock();
        addAsrBlock();
        addDpsBlock();
        addAimMarkerBlock();
    }

    /** Sprint：左下 [Sprint] 状态文字（对齐 Forge）。 */
    private static void addSprintBlock() {
        ToggleSprintModule mod = ToggleSprintModule.instance();
        BLOCKS.add(new HudLayoutBlock("sprint", "Sprint", "[Sprint]", 82, 16,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return mod.hudX(); }
                    public int y(int sw, int sh, int rw, int rh) { return sh - mod.hudBottom() - rh; }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setHudPosition(Math.max(0, x), Math.max(0, sh - y - rh));
                    }
                    public void resetPosition() { mod.setHudPosition(8, 20); }
                    public void save() { }
                    public float moduleScaleX() { return 1.0f; }
                    public float moduleScaleY() { return 1.0f; }
                }));
    }

    /** LR Indicator：默认位置 = 快捷栏上方 80px，偏移字段 lrHudDx/Dy。 */
    private static void addLrIndicatorBlock() {
        LrIndicatorModule mod = LrIndicatorModule.instance();
        BLOCKS.add(new HudLayoutBlock("lr_indicator", "LR Indicator", "① ② ③ ④", 110, 16,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return sw / 2 + mod.lrHudDx - rw / 2; }
                    public int y(int sw, int sh, int rw, int rh) { return sh - 22 - 80 + mod.lrHudDy; }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setHudOffsets(x + rw / 2 - sw / 2, y - (sh - 22 - 80));
                    }
                    public void resetPosition() { mod.setHudOffsets(0, 0); }
                    public void save() { }
                    public float moduleScaleX() { return 1.0f; }
                    public float moduleScaleY() { return 1.0f; }
                }));
    }

    private static void addZombiesBlocks() {
        ZombiesAssistModule mod = ZombiesAssistModule.instance();
        ZombiesConfig cfg = mod.config();
        BLOCKS.add(new HudLayoutBlock("zombies.top", "ZB Top Status", "Round 55 | Left 32 | 18.4s", 250, 34,
                centeredAdapter(
                        () -> cfg.topHudXOffset,
                        value -> cfg.topHudXOffset = value,
                        () -> cfg.topHudY,
                        value -> cfg.topHudY = value,
                        () -> cfg.topHudScale,
                        () -> cfg.topHudScale = 1.0f,
                        () -> { cfg.topHudXOffset = 0; cfg.topHudY = 2; },
                        mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.tactical", "ZB Tactical", "Alien Arcadium | Down 1", 190, 72,
                rightTopAdapter(
                        () -> cfg.tacticalHudRight, value -> cfg.tacticalHudRight = value,
                        () -> cfg.tacticalHudY, value -> cfg.tacticalHudY = value,
                        () -> cfg.tacticalHudScale, () -> cfg.tacticalHudScale = 1.0f,
                        () -> { cfg.tacticalHudRight = 4; cfg.tacticalHudY = 32; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.economy", "Player Economy", "MICx 68,420 | teammate 54,210", 180, 104,
                centerRightAdapter(
                        () -> cfg.ecoHudRight, value -> cfg.ecoHudRight = value,
                        () -> cfg.ecoHudCenterYOffset, value -> cfg.ecoHudCenterYOffset = value,
                        () -> cfg.ecoHudScale, () -> cfg.ecoHudScale = 1.0f,
                        () -> { cfg.ecoHudRight = 4; cfg.ecoHudCenterYOffset = 0; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.wave_table", "Wave Table", "R1 00:13 | W1 00:10 ...", 110, 88,
                leftTopAdapter(
                        () -> cfg.waveTableHudDx, value -> cfg.waveTableHudDx = value,
                        () -> cfg.waveTableHudDy, value -> cfg.waveTableHudDy = value,
                        () -> 1.0f, () -> {},
                        () -> { cfg.waveTableHudDx = 0; cfg.waveTableHudDy = 0; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.powerup", "Power-up Forecast", "INS R18,R21 | x1 x6 Max Ammo", 190, 58,
                rightBottomAdapter(
                        () -> cfg.puHudRight, value -> cfg.puHudRight = value,
                        () -> cfg.puHudBottom, value -> cfg.puHudBottom = value,
                        () -> cfg.puHudScale, () -> cfg.puHudScale = 1.0f,
                        () -> { cfg.puHudRight = 4; cfg.puHudBottom = 6; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.eco_clock", "SlimeEco", "ECO 卡死: 0 亏≈0", 190, 20,
                rightBottomAdapter(
                        () -> cfg.ecoClockRight, value -> cfg.ecoClockRight = value,
                        () -> cfg.ecoClockBottom, value -> cfg.ecoClockBottom = value,
                        () -> cfg.ecoClockScale, () -> cfg.ecoClockScale = 1.0f,
                        () -> { cfg.ecoClockRight = 4; cfg.ecoClockBottom = 8; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.fast_revive", "Fast Revive", "FR RTT 68ms | Player READY", 250, 100,
                centeredOffsetAdapter(
                        () -> cfg.frHudXOffset, value -> cfg.frHudXOffset = value,
                        () -> cfg.frHudYOffset, value -> cfg.frHudYOffset = value,
                        () -> cfg.frHudScale, () -> cfg.frHudScale = 1.0f,
                        () -> { cfg.frHudXOffset = 0; cfg.frHudYOffset = 48; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.ls", "LS / Rescue", "LS R70 W3/5 | QUAD | Player HP 20/20", 250, 120,
                centeredOffsetAdapter(
                        () -> cfg.lsHudXOffset, value -> cfg.lsHudXOffset = value,
                        () -> cfg.lsHudYOffset, value -> cfg.lsHudYOffset = value,
                        () -> cfg.lsHudScale, () -> cfg.lsHudScale = 1.0f,
                        () -> { cfg.lsHudXOffset = 0; cfg.lsHudYOffset = 0; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.threat", "Threat Counts", "TOO: 1 | Giant: 2 | Clown: 1", 120, 54,
                centeredOffsetAdapter(
                        () -> cfg.threatHudXOffset, value -> cfg.threatHudXOffset = value,
                        () -> cfg.threatHudYOffset, value -> cfg.threatHudYOffset = value,
                        () -> cfg.threatHudScale, () -> cfg.threatHudScale = 1.0f,
                        () -> { cfg.threatHudXOffset = 18; cfg.threatHudYOffset = 8; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.too_rush", "TOO Rush Alert", "TOO rush 8.4m", 240, 24,
                centeredOffsetAdapter(
                        () -> cfg.tooRushXOffset, value -> cfg.tooRushXOffset = value,
                        () -> cfg.tooRushYOffset, value -> cfg.tooRushYOffset = value,
                        () -> cfg.tooRushScale, () -> cfg.tooRushScale = 1.0f,
                        () -> { cfg.tooRushXOffset = 0; cfg.tooRushYOffset = 0; }, mod::saveConfig)));
        BLOCKS.add(new HudLayoutBlock("zombies.block_alert", "BLOCK Alert", "BLOCK NOW 1.2s", 220, 24,
                centeredThirdAdapter(
                        () -> cfg.blockAlertXOffset, value -> cfg.blockAlertXOffset = value,
                        () -> cfg.blockAlertYOffset, value -> cfg.blockAlertYOffset = value,
                        () -> cfg.blockAlertScale, () -> cfg.blockAlertScale = 1.0f,
                        () -> { cfg.blockAlertXOffset = 0; cfg.blockAlertYOffset = 14; }, mod::saveConfig)));
    }

    private static void addTeammateBlock() {
        TeammateHpModule mod = TeammateHpModule.instance();
        BLOCKS.add(new HudLayoutBlock("teammate_hp", "Teammate HP", "Player 20/20 | Teammate 18/20", 180, 114,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return mod.screenX(); }
                    public int y(int sw, int sh, int rw, int rh) { return mod.screenY(); }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setLayoutPosition(x, y);
                    }
                    public void resetPosition() { mod.setLayoutPosition(8, 40); }
                    public void save() { mod.saveLayoutConfiguration(); }
                    public float moduleScaleX() { return mod.uiScale(); }
                    public float moduleScaleY() { return mod.uiScale(); }
                    public void resetScale() { mod.setUiScale(1.0f); }
                }));
    }

    private static void addTeamSyncBlock() {
        TeamSyncModule mod = TeamSyncModule.instance();
        BLOCKS.add(new HudLayoutBlock("team_sync", "TeamSync", "TeamSync ● 2 | peer 68ms", 170, 84,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return sw - mod.config().hudRightOffset; }
                    public int y(int sw, int sh, int rw, int rh) { return mod.config().hudY; }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        TeamSyncConfig c = mod.config();
                        c.hudRightOffset = Math.max(20, sw - x);
                        c.hudY = Math.max(0, y);
                    }
                    public void resetPosition() { mod.config().hudRightOffset = 150; mod.config().hudY = 8; }
                    public void save() { mod.saveConfig(); }
                    public float moduleScaleX() { return mod.config().hudScaleX; }
                    public float moduleScaleY() { return mod.config().hudScaleY; }
                    public void resetScale() { mod.config().hudScaleX = 1.0f; mod.config().hudScaleY = 1.0f; }
                }));
    }

    private static void addToroBlock() {
        ToroHealthModule mod = ToroHealthModule.instance();
        BLOCKS.add(new HudLayoutBlock("toro_health", "Toro Health", "Zombie 20/20 [health bar]", 104, 30,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return toroX(mod, sw, rw); }
                    public int y(int sw, int sh, int rw, int rh) { return toroY(mod, sh, rh); }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setDisplayPosition(5);
                        mod.setDisplayX(x);
                        mod.setDisplayY(y);
                    }
                    public void resetPosition() { mod.setDisplayPosition(0); mod.setDisplayX(0); mod.setDisplayY(0); }
                    public void save() { mod.saveLayoutConfiguration(); }
                    public float moduleScaleX() { return mod.hudScaleX(); }
                    public float moduleScaleY() { return mod.hudScaleY(); }
                    public void resetScale() { mod.setHudScale(1.0f, 1.0f); }
                }));
    }

    private static int toroX(ToroHealthModule mod, int sw, int rw) {
        return switch (mod.displayPosition()) {
            case 1, 3 -> 2;
            case 2, 4 -> sw - 102;
            case 5 -> mod.displayX();
            default -> sw / 2 - 50;
        };
    }

    private static int toroY(ToroHealthModule mod, int sh, int rh) {
        return switch (mod.displayPosition()) {
            case 3, 4 -> sh - 30;
            case 5 -> mod.displayY();
            default -> 4;
        };
    }

    private static void addAsrBlock() {
        AsrModule mod = AsrModule.instance();
        BLOCKS.add(new HudLayoutBlock("asr", "ASR", "识别中…\n[Enter] 发送", 220, 42,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return mod.hudX(); }
                    public int y(int sw, int sh, int rw, int rh) { return sh - mod.hudBottom() - rh; }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setHudPosition(x, Math.max(0, sh - y - rh));
                    }
                    public void resetPosition() { mod.setHudPosition(8, 64); }
                    public void save() { mod.saveConfiguration(); }
                    public float moduleScaleX() { return mod.hudScaleX(); }
                    public float moduleScaleY() { return mod.hudScaleY(); }
                    public void resetScale() { mod.setHudScale(1.0f, 1.0f); }
                }));
    }

    private static void addDpsBlock() {
        DpsCounterModule mod = DpsCounterModule.instance();
        // Height covers 3 lines (OBS DPS + RC + GS) at ~10px each — fixes editor overlap where 14px hid the lower rows.
        BLOCKS.add(new HudLayoutBlock("dps_counter", "DPS Counter", "OBS DPS 128\nRC ON/OFF\nGS 23/234/24/34", 90, 34,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return sw - mod.hudRight() - rw; }
                    public int y(int sw, int sh, int rw, int rh) { return mod.hudY(); }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setHudPosition(Math.max(4, sw - x - rw), Math.max(0, y));
                    }
                    public void resetPosition() { mod.setHudPosition(4, 2); }
                    public void save() { mod.saveLayoutConfiguration(); }
                    public float moduleScaleX() { return mod.hudScaleX(); }
                    public float moduleScaleY() { return mod.hudScaleY(); }
                    public void resetScale() { mod.setHudScale(1.0f, 1.0f); }
                }));
    }

    private static void addAimMarkerBlock() {
        AimLeadModule mod = AimLeadModule.instance();
        BLOCKS.add(new HudLayoutBlock("aim_lead_marker", "AimLead Marker", "准心提前点", 8, 4,
                new HudLayoutBlock.Adapter() {
                    public int x(int sw, int sh, int rw, int rh) { return sw / 2 + mod.markerOffsetX() - rw / 2; }
                    public int y(int sw, int sh, int rw, int rh) { return sh / 2 + mod.markerOffsetY(); }
                    public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                        mod.setMarkerOffset(x + rw / 2 - sw / 2, y - sh / 2);
                    }
                    public void resetPosition() { mod.setMarkerOffset(0, 6); }
                    public void save() { mod.saveConfiguration(); }
                    public float moduleScaleX() { return mod.markerScaleX(); }
                    public float moduleScaleY() { return mod.markerScaleY(); }
                    public void resetScale() { mod.setMarkerScale(1.0f, 1.0f); }
                }));
    }

    private interface IntGetter { int get(); }
    private interface IntSetter { void set(int value); }
    private interface FloatGetter { float get(); }
    private interface FloatReset { void reset(); }
    private interface Action { void run(); }

    private static HudLayoutBlock.Adapter centeredAdapter(IntGetter xGet, IntSetter xSet,
                                                           IntGetter yGet, IntSetter ySet,
                                                           FloatGetter scaleGet, FloatReset scaleReset,
                                                           Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw / 2 + xGet.get() - rw / 2; }
            public int y(int sw, int sh, int rw, int rh) { return yGet.get(); }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                xSet.set(x + rw / 2 - sw / 2);
                ySet.set(Math.max(0, y));
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter centeredOffsetAdapter(IntGetter xGet, IntSetter xSet,
                                                                 IntGetter yGet, IntSetter ySet,
                                                                 FloatGetter scaleGet, FloatReset scaleReset,
                                                                 Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw / 2 + xGet.get() - rw / 2; }
            public int y(int sw, int sh, int rw, int rh) { return sh / 2 + yGet.get(); }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                xSet.set(x + rw / 2 - sw / 2);
                ySet.set(y - sh / 2);
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter rightTopAdapter(IntGetter rightGet, IntSetter rightSet,
                                                           IntGetter yGet, IntSetter ySet,
                                                           FloatGetter scaleGet, FloatReset scaleReset,
                                                           Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw - rightGet.get() - rw; }
            public int y(int sw, int sh, int rw, int rh) { return yGet.get(); }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                rightSet.set(Math.max(0, sw - x - rw)); ySet.set(Math.max(0, y));
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter leftTopAdapter(IntGetter xGet, IntSetter xSet,
                                                          IntGetter yGet, IntSetter ySet,
                                                          FloatGetter scaleGet, FloatReset scaleReset,
                                                          Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return xGet.get(); }
            public int y(int sw, int sh, int rw, int rh) { return yGet.get(); }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                xSet.set(Math.max(0, x)); ySet.set(Math.max(0, y));
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter centerRightAdapter(IntGetter rightGet, IntSetter rightSet,
                                                               IntGetter yGet, IntSetter ySet,
                                                               FloatGetter scaleGet, FloatReset scaleReset,
                                                               Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw - rightGet.get() - rw; }
            public int y(int sw, int sh, int rw, int rh) { return sh / 2 + yGet.get() - rh / 2; }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                rightSet.set(Math.max(0, sw - x - rw));
                ySet.set(y + rh / 2 - sh / 2);
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter rightBottomAdapter(IntGetter rightGet, IntSetter rightSet,
                                                              IntGetter bottomGet, IntSetter bottomSet,
                                                              FloatGetter scaleGet, FloatReset scaleReset,
                                                              Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw - rightGet.get() - rw; }
            public int y(int sw, int sh, int rw, int rh) { return sh - bottomGet.get() - rh; }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                rightSet.set(Math.max(0, sw - x - rw)); bottomSet.set(Math.max(0, sh - y - rh));
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

    private static HudLayoutBlock.Adapter centeredThirdAdapter(IntGetter xGet, IntSetter xSet,
                                                                IntGetter yGet, IntSetter ySet,
                                                                FloatGetter scaleGet, FloatReset scaleReset,
                                                                Action resetPosition, Action save) {
        return new HudLayoutBlock.Adapter() {
            public int x(int sw, int sh, int rw, int rh) { return sw / 2 + xGet.get() - rw / 2; }
            public int y(int sw, int sh, int rw, int rh) { return sh / 3 + yGet.get(); }
            public void setPosition(int x, int y, int sw, int sh, int rw, int rh) {
                xSet.set(x + rw / 2 - sw / 2); ySet.set(y - sh / 3);
            }
            public void resetPosition() { resetPosition.run(); }
            public void save() { save.run(); }
            public void resetScale() { scaleReset.reset(); }
            public float moduleScaleX() { return scaleGet.get(); }
            public float moduleScaleY() { return scaleGet.get(); }
        };
    }

}
