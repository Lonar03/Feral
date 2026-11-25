package keystrokesmod.mixin.impl.network;

import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.event.PreEntityVelocityEvent;
import keystrokesmod.event.PreExplosionPacketEvent;
import keystrokesmod.mixin.interfaces.IMixinExplosion;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket.BlockUpdate;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuConfirmS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuContentS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class MixinNetHandlerPlayClient {
    @Inject(
            method = "handleInventoryMenuSlotContent",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
                            shift = Shift.AFTER))
    private void feral$trackSetSlot(InventoryMenuSlotContentS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.inventory != null) {
            ModuleManager.inventory.onMenuSlot(packet.getMenuId(), packet.getSlotId(), packet.getItem());
        }

        if (ModuleManager.autoSwap != null) {
            ModuleManager.autoSwap.processSetSlot(packet);
        }
    }

    @Inject(
            method = "handleInventoryMenuContent",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
                            shift = Shift.AFTER))
    private void feral$trackWindowItems(InventoryMenuContentS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.autoSwap != null) {
            ModuleManager.autoSwap.processWindowItems(packet);
        }

        if (ModuleManager.inventory != null) {
            ModuleManager.inventory.onMenuContents(packet.getMenuId(), packet.getItems());
        }
    }

    @Inject(method = "handleInventoryMenuConfirm", at = @At("RETURN"))
    private void feral$onMenuConfirm(InventoryMenuConfirmS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.inventory != null) {
            ModuleManager.inventory.onMenuConfirm(packet.getMenuId(), packet.getActionId(), packet.getAccepted());
        }
    }

    @Inject(
            method = "handleBlockUpdate",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
                            shift = Shift.AFTER))
    private void feral$trackBlockChange(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.autoSwap != null) {
            ModuleManager.autoSwap.processBlockChange(packet.getPos(), packet.getBlockState());
        }
    }

    @Inject(
            method = "handleBlocksUpdate",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V",
                            shift = Shift.AFTER))
    private void feral$trackMultiBlockChange(BlocksUpdateS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.autoSwap != null) {
            for (BlockUpdate update : packet.getUpdates()) {
                ModuleManager.autoSwap.processBlockChange(update.getBlockPos(), update.getBlockState());
            }
        }
    }

    @Inject(method = "handlePlayerMove", at = @At("HEAD"))
    public void handlePlayerPosLookPre(PlayerMoveS2CPacket packetIn, CallbackInfo ci) {
        PlayerTeleportEvent e = new PlayerTeleportEvent(packetIn);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(e);
        if (e.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "handlePlayerMove", at = @At("RETURN"))
    public void handlePlayerPosLook(PlayerMoveS2CPacket packetIn, CallbackInfo ci) {
        ModuleManager.noRotate.handlePlayerPosLook(packetIn);
    }

    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    public void handleEntityVelocityInjection(EntityVelocityS2CPacket packet, CallbackInfo ci) {
        PreEntityVelocityEvent preEntityVelocityEvent = new PreEntityVelocityEvent(packet);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(preEntityVelocityEvent);
        if (preEntityVelocityEvent.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "handleEntityVelocity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;lerpVelocity(DDD)V", shift = Shift.AFTER))
    private void onEntityVelocityApplied(EntityVelocityS2CPacket packet, CallbackInfo ci) {
        if (ModuleManager.velocity != null) {
            ModuleManager.velocity.onEntityVelocityApplied(packet);
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    public void handleExplosionInjection(ExplosionS2CPacket packet, CallbackInfo ci) {
        PreExplosionPacketEvent event = new PreExplosionPacketEvent(packet);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "handleExplosion",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/explosion/Explosion;damageBlocks(Z)V"))
    private void feral$handleLaunchExplosion(Explosion explosion, boolean particles, ExplosionS2CPacket packet) {
        if (ModuleManager.longJump != null && ModuleManager.longJump.shouldHideLaunchExplosion(packet)) {
            ((IMixinExplosion) explosion).hideEffects();
        }

        explosion.damageBlocks(particles);
    }
}
