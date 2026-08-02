package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimLeadModule;
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

    @Inject(method = "handleTeleportEntity", at = @At("HEAD"))
    private void micx$recordTeleport(ClientboundTeleportEntityPacket packet, CallbackInfo callbackInfo) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        Entity entity = client.level.getEntity(packet.id());
        if (entity != null) {
            AimLeadModule.recordTeleport(packet.id(), entity.getPositionCodec().getBase(), entity,
                    packet.change(), packet.relatives());
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
        if (!ZombiesAssistModule.instance().enabled() || packet.getSound() == null) return;
        String soundId = packet.getSound().value().location().toString();
        ZombiesTracker.instance().onSound(soundId, packet.getPitch(), packet.getX(), packet.getY(), packet.getZ(),
                System.currentTimeMillis());
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
