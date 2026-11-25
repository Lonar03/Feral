package keystrokesmod.utility;

import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.MobType;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.item.BowItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ItemStack;

public final class ItemSortScoring {
    private static final double FIRE_ASPECT_BONUS = 4.0;
    private static final double FLAME_BONUS = 4.0;
    private static final double BOW_KNOCKBACK_BONUS = 0.15;
    private static final double MELEE_KNOCKBACK_BONUS = 0.1;
    private static final double FULLY_CHARGED_ARROW_SPEED = 3.0;
    private static final double BASE_ARROW_DAMAGE = 2.0;

    private ItemSortScoring() {}

    public static double getMeleeDamage(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }

        double damage = getAttackDamageAttribute(stack);
        damage += EnchantmentHelper.modifyDamage(stack, MobType.UNDEFINED);
        return damage + EnchantmentHelper.getLevel(Enchantment.FIRE_ASPECT.id, stack) * 4.0;
    }

    public static double getMeleeSelectionScore(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }

        double score = getMeleeDamage(stack);
        score += EnchantmentHelper.getLevel(Enchantment.KNOCKBACK.id, stack) * 0.1;
        return score + getDurabilityTieBreaker(stack);
    }

    public static double getBowDamage(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BowItem) {
            int powerLevel = EnchantmentHelper.getLevel(Enchantment.POWER.id, stack);
            int flameLevel = EnchantmentHelper.getLevel(Enchantment.FLAME.id, stack);
            double projectileDamage = 2.0 + powerLevel * 0.5 + 0.5;
            double impactDamage = 3.0 * projectileDamage;
            double damage = impactDamage + impactDamage * 0.25 + 0.5;
            return damage + flameLevel * 4.0;
        } else {
            return 0.0;
        }
    }

    public static double getBowSelectionScore(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }

        double score = getBowDamage(stack);
        score += EnchantmentHelper.getLevel(Enchantment.PUNCH.id, stack) * 0.15;
        return score + getDurabilityTieBreaker(stack);
    }

    public static double getToolSelectionScore(ItemStack stack) {
        return Math.max(
                Math.max(getAxeScore(stack), getPickaxeScore(stack)),
                Math.max(Math.max(getShovelScore(stack), getHoeScore(stack)), getShearsScore(stack)));
    }

    public static double getAxeScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.LOG);
    }

    public static double getPickaxeScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.STONE);
    }

    public static double getShovelScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.DIRT);
    }

    public static double getHoeScore(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof HoeItem) {
            double score = stack.getMaxDamage();
            return score + getDurabilityTieBreaker(stack);
        } else {
            return 0.0;
        }
    }

    public static double getShearsScore(ItemStack stack) {
        return getBlockBreakingScore(stack, Blocks.WEB);
    }

    public static double getBlockBreakingScore(ItemStack stack, Block block) {
        double speed = getBlockBreakingSpeed(stack, block);
        return speed > 0.0 ? speed + getDurabilityTieBreaker(stack) : 0.0;
    }

    public static double getBlockBreakingSpeed(ItemStack stack, Block block) {
        if (stack != null && block != null) {
            float speed = stack.getMiningSpeed(block);
            if (speed > 1.0F) {
                int efficiencyLevel = EnchantmentHelper.getLevel(Enchantment.EFFICIENCY.id, stack);
                if (efficiencyLevel > 0) {
                    speed += efficiencyLevel * efficiencyLevel + 1;
                }
            }

            if (speed <= 1.0F) {
                return 0.0;
            }

            if (!block.getMaterial().isToolNotRequired() && !stack.canMineBlock(block)) {
                speed *= 0.3F;
            }

            return speed;
        } else {
            return 0.0;
        }
    }

    private static double getAttackDamageAttribute(ItemStack stack) {
        if (stack == null) {
            return 0.0;
        }

        for (Entry<String, AttributeModifier> entry :
                stack.getAttributeModifiers().entries()) {
            if (EntityAttributes.ATTACK_DAMAGE.getName().equals(entry.getKey())) {
                return entry.getValue().get();
            }
        }

        return 0.0;
    }

    private static double getDurabilityTieBreaker(ItemStack stack) {
        if (stack != null && stack.isDamageable()) {
            int maxDurability = stack.getMaxDamage();
            if (maxDurability <= 0) {
                return 0.0;
            }

            int remainingDurability = maxDurability - stack.getDamage();
            return (double) remainingDurability / maxDurability / 1000.0;
        } else {
            return 0.0;
        }
    }
}
