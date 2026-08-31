package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.AimLeadModule;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Seeds and evicts packet-backed AimLead tracks with entity lifecycle packets. */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientEntityPackets {
    @Inject(method = "handleAddEntity", at = @At("RETURN"))
    private void micx$recordSpawn(ClientboundAddEntityPacket packet, CallbackInfo callbackInfo) {
        AimLeadModule.recordSpawn(packet.getId(), new net.minecraft.world.phys.Vec3(packet.getX(), packet.getY(), packet.getZ()));
    }

    @Inject(method = "handleRemoveEntities", at = @At("RETURN"))
    private void micx$removeTracks(ClientboundRemoveEntitiesPacket packet, CallbackInfo callbackInfo) {
        packet.getEntityIds().forEach(AimLeadModule::remove);
        try {
            var idList = packet.getEntityIds();
            int[] ids = new int[idList.size()];
            for (int i = 0; i < idList.size(); i++) ids[i] = idList.getInt(i);
            dev.micx.micxfabric.ZombiesExplorerModule.instance().onEntitiesRemoved(ids);
            dev.micx.micxfabric.WindowSpawnCounterModule.instance().onEntitiesRemoved(ids);
        } catch (Throwable ignored) {}
    }
}
