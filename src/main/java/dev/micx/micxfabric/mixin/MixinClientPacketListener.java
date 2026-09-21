package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimLeadModule;
import dev.micx.micxfabric.LrIndicatorModule;
import dev.micx.micxfabric.SlimeForecastModule;
import dev.micx.micxfabric.ZombiesAssistModule;
import dev.micx.micxfabric.ZombiesTracker;
import dev.micx.micxfabric.jev.JevFeedbackJournal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures server movement packets after vanilla has applied them. */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {
    private float micx$localYaw;
    private float micx$localPitch;
    private boolean micx$restoreRotation;

    @Inject(method = "close", at = @At("HEAD"))
    private void micx$resetJevFeedback(CallbackInfo callbackInfo) {
        JevFeedbackJournal.instance().reset();
    }

    @Inject(method = "handleMoveEntity", at = @At("RETURN"))
    private void micx$recordMove(ClientboundMoveEntityPacket packet, CallbackInfo callbackInfo) {
        if (!packet.hasPosition()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = packet.getEntity(client.level);
        if (entity != null) {
            AimLeadModule.recordAbsolute(entity.getId(), entity.getPositionCodec().getBase());
        }
    }

    @Inject(method = "handleEntityPositionSync", at = @At("RETURN"))
    private void micx$recordSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo callbackInfo) {
        AimLeadModule.recordAbsolute(packet.id(), packet.values().position());
    }

    @Inject(method = "handleTeleportEntity", at = @At("RETURN"))
    private void micx$recordTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(packet.id());
        if (entity != null) {
            AimLeadModule.recordAbsolute(packet.id(), entity.getPositionCodec().getBase());
        }
    }

    @Inject(method = "handleCommandSuggestions", at = @At("RETURN"))
    private void micx$recordRtt(ClientboundCommandSuggestionsPacket packet, CallbackInfo callbackInfo) {
        AimLeadModule.recordRttResponse(packet.id());
    }

    @Inject(method = "setTitleText", at = @At("RETURN"))
    private void micx$title(ClientboundSetTitleTextPacket packet, CallbackInfo callbackInfo) {
        if (!ZombiesAssistModule.instance().enabled()) return;
        ZombiesTracker.instance().onTitleText(packet.text().getString(), System.currentTimeMillis());
    }

    @Inject(method = "setSubtitleText", at = @At("RETURN"))
    private void micx$subtitle(ClientboundSetSubtitleTextPacket packet, CallbackInfo callbackInfo) {
        if (!ZombiesAssistModule.instance().enabled()) return;
        ZombiesTracker.instance().onSubtitleText(packet.text().getString(), System.currentTimeMillis());
    }

    @Inject(method = "setActionBarText", at = @At("RETURN"))
    private void micx$actionBar(ClientboundSetActionBarTextPacket packet, CallbackInfo callbackInfo) {
        if (!ZombiesAssistModule.instance().enabled()) return;
        ZombiesTracker.instance().onActionBarText(packet.text().getString(), System.currentTimeMillis());
    }

    @Inject(method = "handleSoundEvent", at = @At("RETURN"))
    private void micx$sound(ClientboundSoundPacket packet, CallbackInfo callbackInfo) {
        if (packet.getSound() == null) return;
        String soundId = packet.getSound().value().location().toString();
        long now = System.currentTimeMillis();
        if (ZombiesAssistModule.instance().enabled()) ZombiesTracker.instance().onSound(soundId, packet.getPitch(), packet.getX(), packet.getY(), packet.getZ(), now);
        try { LrIndicatorModule.instance().onSound(soundId, packet.getPitch(), packet.getX(), packet.getY(), packet.getZ(), now); } catch (Throwable ignored) {}
    }

    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void micx$windowSpawn(ClientboundAddEntityPacket packet, CallbackInfo ci2) {
        try { var mc2 = Minecraft.getInstance(); if (mc2 != null && mc2.level != null) {
            dev.micx.micxfabric.WindowSpawnCounterModule.instance().recordBirthPos(packet.getId(), packet.getX(), packet.getY(), packet.getZ());
            var e2 = mc2.level.getEntity(packet.getId());
            if (e2 != null) dev.micx.micxfabric.WindowSpawnCounterModule.instance().onEntitySpawn(e2);
        }} catch (Throwable ignored) {}
    }

    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void micx$golemJoin(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        String id = String.valueOf(packet.getType());
        if (id.contains("iron_golem") || id.contains("giant")) {
            try { LrIndicatorModule.instance().onGolemJoin(System.currentTimeMillis(), packet.getX(), packet.getY(), packet.getZ()); } catch (Throwable ignored) {}
        }
        // 史莱姆/岩浆怪出生：SlimeForecast 的"刷出后转墨绿/隐藏"标记（同波幂等）
        if (id.contains("slime") || id.contains("magma_cube")) {
            try { SlimeForecastModule.instance().onSlimeJoined(System.currentTimeMillis()); } catch (Throwable ignored) {}
        }
        // ZombiesExplorer: spawn order (ZE SpawnPatternNotice.java parity) — entity already added by vanilla before RETURN
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                Entity zeEnt = mc.level.getEntity(packet.getId());
                if (zeEnt != null) dev.micx.micxfabric.ZombiesExplorerModule.instance().onEntityJoin(zeEnt);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "handleAnimate", at = @At("RETURN"))
    private void micx$recordSwing(ClientboundAnimatePacket packet, CallbackInfo callbackInfo) {
        // 只收玩家挥臂（挥剑相关性信号）；怪物自身挥臂不采集——打窗户≠锁敌玩家，
        // 打窗怪的误判由真实伤害计时兜底。本地玩家不收包，由 AimbotModule.tick 自喂。
        if (packet.getAction() != ClientboundAnimatePacket.SWING_MAIN_HAND) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(packet.getId());
        if (entity == null || !(entity instanceof net.minecraft.world.entity.player.Player)) return;
        try {
            dev.micx.micxfabric.AimbotModule.recordRemotePlayerSwing(
                    entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        } catch (Throwable ignored) {}
    }

    @Inject(method = "handleHurtAnimation", at = @At("RETURN"))
    private void micx$recordHurt(ClientboundHurtAnimationPacket packet, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(packet.id());
        if (entity == null || entity instanceof net.minecraft.world.entity.player.Player) return;
        try {
            dev.micx.micxfabric.AimbotModule.recordMobHurt(
                    entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        } catch (Throwable ignored) {}
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void micx$captureRotation(ClientboundPlayerPositionPacket packet, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (!ZombiesAssistModule.instance().enabled()
                || !ZombiesAssistModule.instance().config().noRotate
                || client.player == null) return;
        // Forge v3.1 同款防噪声：幅度 ≤15° 的小校正不值得恢复，也避免传送微调反复拉视角
        float curYaw = client.player.getYRot();
        float curPitch = client.player.getXRot();
        boolean yawRel = packet.relatives().contains(net.minecraft.world.entity.Relative.Y_ROT);
        boolean pitchRel = packet.relatives().contains(net.minecraft.world.entity.Relative.X_ROT);
        float tgtYaw = yawRel ? curYaw + packet.change().yRot() : packet.change().yRot();
        float tgtPitch = pitchRel ? curPitch + packet.change().xRot() : packet.change().xRot();
        float dy = Math.abs(net.minecraft.util.Mth.wrapDegrees(tgtYaw - curYaw));
        float dp = Math.abs(tgtPitch - curPitch);
        if (dy <= 15.0f && dp <= 15.0f) return;
        micx$localYaw = curYaw;
        micx$localPitch = curPitch;
        micx$restoreRotation = true;
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void micx$restoreRotation(ClientboundPlayerPositionPacket packet, CallbackInfo callbackInfo) {
        if (!micx$restoreRotation) return;
        micx$restoreRotation = false;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        client.player.setYRot(micx$localYaw);
        client.player.setXRot(micx$localPitch);
        client.player.setYHeadRot(micx$localYaw);
        client.player.setYBodyRot(micx$localYaw);
    }
}
