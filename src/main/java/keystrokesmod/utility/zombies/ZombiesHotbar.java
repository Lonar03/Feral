package keystrokesmod.utility.zombies;

import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.IMinecraftInstance;
import keystrokesmod.utility.Utils;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;

/** Checks shared by the Zombies weapon-cycling modules (Quick Switch, No Reload). */
public final class ZombiesHotbar implements IMinecraftInstance {
    /** Shop / revive armor stands closer than this pause weapon cycling. */
    private static final double ARMOR_STAND_RANGE = 3.0;

    private ZombiesHotbar() {}

    /** Looking at a shop or revive armor stand within reach. */
    public static boolean isLookingAtNearbyArmorStand() {
        HitResult over = mc.crosshairTarget;
        return over != null
                && over.entity instanceof ArmorStandEntity
                && mc.player.distanceTo(over.entity) <= ARMOR_STAND_RANGE;
    }

    public static boolean isLookingAtChest() {
        BlockPos pos = lookedAtBlock();
        return pos != null && mc.world.getBlockEntity(pos) instanceof ChestBlockEntity;
    }

    public static boolean isLookingAtButton() {
        BlockPos pos = lookedAtBlock();
        return pos != null && BlockUtils.getBlock(pos) instanceof ButtonBlock;
    }

    public static boolean isLookingAtLever() {
        BlockPos pos = lookedAtBlock();
        return pos != null && BlockUtils.getBlock(pos) instanceof LeverBlock;
    }

    /** Looking at a block you right-click to use: a button, lever or chest. */
    public static boolean isLookingAtInteractable() {
        return isLookingAtButton() || isLookingAtLever() || isLookingAtChest();
    }

    /** Slot 1 holds the knife in Zombies. */
    public static boolean isHoldingKnife() {
        return Utils.nullCheck() && mc.player.inventory.selectedSlot == 0;
    }

    /** A hotbar slot (0-8) holding something that isn't in {@code ignored}. */
    public static boolean isWeaponSlot(int hotbarIndex, ItemListSetting ignored) {
        if (!Utils.nullCheck() || hotbarIndex < 0 || hotbarIndex > 8) {
            return false;
        }
        ItemStack stack = mc.player.inventory.getItem(hotbarIndex);
        return stack != null && !ignored.matches(stack);
    }

    private static BlockPos lookedAtBlock() {
        HitResult over = mc.crosshairTarget;
        return over != null && mc.world != null ? over.getPos() : null;
    }
}
