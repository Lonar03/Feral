package keystrokesmod.mixin.impl.network;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.PacketUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class MixinNetworkManager {
    @Shadow
    private void doSend(Packet inPacket, GenericFutureListener<? extends Future<? super Void>>[] futureListeners) {
        throw new AssertionError();
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    public void sendPacket(Packet p_sendPacket_1_, CallbackInfo ci) {
        if (PacketUtils.consumeSendEventSkip(p_sendPacket_1_)) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(new NoEventPacketEvent(p_sendPacket_1_));
        } else {
            SendPacketEvent sendPacketEvent = new SendPacketEvent(p_sendPacket_1_);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(sendPacketEvent);
            if (sendPacketEvent.isCanceled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "doSend", at = @At("HEAD"), cancellable = true)
    public void dispatchPacket(
            Packet inPacket, GenericFutureListener<? extends Future<? super Void>>[] futureListeners, CallbackInfo ci) {
        if (ModuleManager.velocity != null
                && ModuleManager.velocity.deferAttackPacket(
                        (Connection) (Object) this, inPacket, () -> this.doSend(inPacket, futureListeners))) {
            ci.cancel();
        } else {
            DispatchPacketEvent dispatchPacketEvent = new DispatchPacketEvent((Connection) (Object) this, inPacket);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(dispatchPacketEvent);
        }
    }

    @Inject(
            method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V",
            at = @At("HEAD"),
            cancellable = true)
    public void receivePacket(ChannelHandlerContext p_channelRead0_1_, Packet p_channelRead0_2_, CallbackInfo ci) {
        if (!PacketUtils.consumeReceiveEventSkip(p_channelRead0_2_)) {
            ReceivePacketEvent receivePacketEvent = new ReceivePacketEvent(p_channelRead0_2_);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(receivePacketEvent);
            if (receivePacketEvent.isCanceled()) {
                ci.cancel();
            }
        }
    }
}
