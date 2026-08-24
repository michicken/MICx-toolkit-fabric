package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimLeadModule;
import dev.micx.micxfabric.LrIndicatorModule;
import dev.micx.micxfabric.SlimeForecastModule;
import dev.micx.micxfabric.ZombiesAssistModule;
import dev.micx.micxfabric.ZombiesTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
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
    private void micx$golemJoin(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        String id = packet.getType().toString();
        if (id.contains("iron_golem") || id.contains("giant")) {
            try { LrIndicatorModule.instance().onGolemJoin(System.currentTimeMillis(), packet.getX(), packet.getY(), packet.getZ()); } catch (Throwable ignored) {}
        }
        // 史莱姆/岩浆怪出生：SlimeForecast 的"刷出后转墨绿/隐藏"标记（同波幂等）
        if (id.contains("slime") || id.contains("magma_cube")) {
            try { SlimeForecastModule.instance().onSlimeJoined(System.currentTimeMillis()); } catch (Throwable ignored) {}
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void micx$captureRotation(ClientboundPlayerPositionPacket packet, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (!ZombiesAssistModule.instance().enabled()
                || !ZombiesAssistModule.instance().config().noRotate
                || client.player == null) return;
        micx$localYaw = client.player.getYRot();
        micx$localPitch = client.player.getXRot();
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
