package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.UseItemEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.BedAura;
import keystrokesmod.module.impl.player.FastMine;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class MixinPlayerControllerMP {
    @Shadow
    private int miningCooldown;

    @Unique
    private BlockState feral$fastMineTargetState;

    @Redirect(
            method = {"startMiningBlock", "tickBlockMining", "stopMiningBlock"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/network/handler/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"))
    private void feral$bedAuraDiggingRotations(ClientPlayNetworkHandler handler, Packet<?> packet) {
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura == null || !bedAura.sendMiningPacket(packet)) {
            handler.sendPacket(packet);
        }
    }

    @Inject(
            method = {"startMiningBlock", "tickBlockMining"},
            at = @At("HEAD"))
    private void feral$fastMineCaptureTargetState(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        World world = Minecraft.getInstance().world;
        this.feral$fastMineTargetState = world == null ? null : world.getBlockState(pos);
    }

    @Inject(
            method =
                    "useItem(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)Z",
            at = @At("HEAD"),
            cancellable = true)
    public void injectUseItemEvent(
            PlayerEntity p_sendUseItem_1_,
            World p_sendUseItem_2_,
            ItemStack p_sendUseItem_3_,
            CallbackInfoReturnable<Boolean> ci) {
        UseItemEvent event = new UseItemEvent(p_sendUseItem_3_);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.setReturnValue(false);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void injectAttackEntity(PlayerEntity playerIn, Entity targetEntity, CallbackInfo callbackInfo) {
        AttackEvent event = new AttackEvent(targetEntity, playerIn, true);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @Redirect(
            method = "startMiningBlock",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/Block;getMiningSpeed(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)F"))
    private float feral$bedAuraGroundHardness(Block block, PlayerEntity player, World worldIn, BlockPos pos) {
        float hardness = block.getMiningSpeed(player, worldIn, pos);
        BedAura bedAura = ModuleManager.bedAura;
        return bedAura != null && bedAura.shouldOverrideFastMine()
                ? hardness * bedAura.getGroundSpoofMultiplier()
                : hardness;
    }

    @Redirect(
            method = "tickBlockMining",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/block/Block;getMiningSpeed(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)F"))
    private float fastMineScaleHardness(Block block, PlayerEntity player, World worldIn, BlockPos pos) {
        float hardness = block.getMiningSpeed(player, worldIn, pos);
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.shouldOverrideFastMine()) {
            return hardness * bedAura.getBreakSpeedMultiplier() * bedAura.getGroundSpoofMultiplier();
        }

        FastMine fm = ModuleManager.fastMine;
        return fm == null ? hardness : hardness * fm.getBreakSpeedMultiplier(worldIn.getBlockState(pos));
    }

    @Unique
    private void feral$fastMineApplyBreakDelaySlider() {
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura != null && bedAura.shouldOverrideFastMine()) {
            int delay = bedAura.getBreakDelayTicks();
            if (delay < 5) {
                this.miningCooldown = delay;
            }
        } else {
            FastMine fm = ModuleManager.fastMine;
            if (fm != null) {
                int o = fm.getBlockHitDelayOverrideOrMinusOne(this.feral$fastMineTargetState);
                if (o >= 0) {
                    this.miningCooldown = o;
                }
            }
        }
    }

    @Inject(
            method = "startMiningBlock",
            at =
                    @At(
                            value = "FIELD",
                            opcode = 181,
                            target = "Lnet/minecraft/client/ClientPlayerInteractionManager;miningCooldown:I",
                            ordinal = 0,
                            shift = Shift.AFTER))
    private void feral$fastMineAfterClickBlockSetDelay(
            BlockPos loc, Direction face, CallbackInfoReturnable<Boolean> cir) {
        this.feral$fastMineApplyBreakDelaySlider();
    }

    @Inject(
            method = "tickBlockMining",
            at =
                    @At(
                            value = "FIELD",
                            opcode = 181,
                            target = "Lnet/minecraft/client/ClientPlayerInteractionManager;miningCooldown:I",
                            ordinal = 1,
                            shift = Shift.AFTER))
    private void feral$fastMineAfterCreativeMiningSetDelay(
            BlockPos posBlock, Direction directionFacing, CallbackInfoReturnable<Boolean> cir) {
        this.feral$fastMineApplyBreakDelaySlider();
    }

    @Inject(
            method = "tickBlockMining",
            at =
                    @At(
                            value = "FIELD",
                            opcode = 181,
                            target = "Lnet/minecraft/client/ClientPlayerInteractionManager;miningCooldown:I",
                            ordinal = 2,
                            shift = Shift.AFTER))
    private void feral$fastMineAfterBreakBlockSetDelay(
            BlockPos posBlock, Direction directionFacing, CallbackInfoReturnable<Boolean> cir) {
        this.feral$fastMineApplyBreakDelaySlider();
    }
}
