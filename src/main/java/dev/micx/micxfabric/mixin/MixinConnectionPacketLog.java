package dev.micx.micxfabric.mixin;

import dev.micx.micxfabric.PacketLogModule;
import dev.micx.micxfabric.jev.JevPoiRecorderModule;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PacketLog 的采集点：在 {@link Connection} 上旁观收发包。
 *
 * <p>只旁观、不改写：两个注入都是 HEAD 且不 cancel，包照原样继续走。
 * 只记"玩家自己这条连接"——单人存档里集成服务端的 Connection 也是同一个类，
 * 不排掉就会把服务端视角的包一起记进来（同一件事出现两遍）。
 *
 * <p>{@code send()} 在客户端线程，{@code channelRead0()} 在网络线程；模块那边只做排队，
 * 真正落盘交给客户端 tick，不占网络线程。
 */
@Mixin(Connection.class)
public final class MixinConnectionPacketLog {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"))
    private void micx$logOutbound(Packet<?> packet, CallbackInfo callbackInfo) {
        if (!micx$isPlayerConnection()) return;
        PacketLogModule.instance().capture(true, packet);
        JevPoiRecorderModule.instance().capturePacket(true, packet);
    }

    @Inject(method = "channelRead0", at = @At("HEAD"))
    private void micx$logInbound(ChannelHandlerContext context, Packet<?> packet, CallbackInfo callbackInfo) {
        if (!micx$isPlayerConnection()) return;
        PacketLogModule.instance().capture(false, packet);
        JevPoiRecorderModule.instance().capturePacket(false, packet);
    }

    private boolean micx$isPlayerConnection() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener listener = client == null ? null : client.getConnection();
        return listener != null && listener.getConnection() == (Object) this;
    }
}
