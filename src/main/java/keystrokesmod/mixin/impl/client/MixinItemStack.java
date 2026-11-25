package keystrokesmod.mixin.impl.client;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.AutoSwap;
import keystrokesmod.module.impl.render.MobESP;
import keystrokesmod.module.impl.render.PlayerESP;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.GhostHandRenderState;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Redirect(
            method = "useOn",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/item/Item;useOn(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;FFF)Z"))
    private boolean feral$trackBlockUse(
            Item item,
            ItemStack stack,
            PlayerEntity player,
            World world,
            BlockPos pos,
            Direction side,
            float hitX,
            float hitY,
            float hitZ) {
        AutoSwap autoSwap = ModuleManager.autoSwap;
        if (autoSwap == null
                || player != Minecraft.getInstance().player
                || !(item instanceof BlockItem)
                || !autoSwap.shouldSyncStackSize()) {
            return item.useOn(stack, player, world, pos, side, hitX, hitY, hitZ);
        }

        if (autoSwap.getBlockCount(player.inventory.selectedSlot, stack) <= 0) {
            return false;
        }

        int previousSize = stack.size;
        BlockState previousState = world.getBlockState(pos);
        boolean used = item.useOn(stack, player, world, pos, side, hitX, hitY, hitZ);
        if (used && stack.size < previousSize) {
            BlockPos placedPos = previousState.equals(world.getBlockState(pos)) ? pos.offset(side) : pos;
            autoSwap.onBlockUsed(stack, previousSize, placedPos, world.getBlockState(placedPos));
        }

        return used;
    }

    @Inject(method = "hasEnchantmentGlint", at = @At("HEAD"), cancellable = true)
    private void suppressGlintDuringRenderPass(CallbackInfoReturnable<Boolean> cir) {
        if (PlayerESP.renderingOutlinePass || MobESP.renderingOutlinePass || GhostHandRenderState.isActive()) {
            cir.setReturnValue(false);
        }
    }
}
