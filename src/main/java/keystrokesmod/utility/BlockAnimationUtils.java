package keystrokesmod.utility;

import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.UseAction;

public final class BlockAnimationUtils {
    private static PlayerEntity renderingPlayer;
    private static int renderingDepth;

    private BlockAnimationUtils() {}

    public static void beginRender(PlayerEntity player) {
        if (player == Minecraft.getInstance().player && renderingDepth++ == 0) {
            renderingPlayer = player;
        }
    }

    public static void endRender(PlayerEntity player) {
        if (player == renderingPlayer && renderingDepth > 0) {
            if (--renderingDepth == 0) {
                renderingPlayer = null;
            }
        }
    }

    public static boolean shouldForceModelBlockAnimation(PlayerEntity player) {
        if (player != null && player == renderingPlayer) {
            ItemStack stack = player.inventory.getSelectedItem();
            if (Minecraft.getInstance().options.perspective == 0) {
                stack = Utils.getSpoofedItem(stack);
            }

            return shouldForceBlockAnimation(player, stack);
        } else {
            return false;
        }
    }

    public static boolean shouldForceBlockAnimation(PlayerEntity player, ItemStack stack) {
        if (player != null && player == renderingPlayer && stack != null && stack.getUseAction() == UseAction.BLOCK) {
            Minecraft mc = Minecraft.getInstance();
            return mc.getItemInHandRenderer() != null
                    && ((IMixinItemRenderer) mc.getItemInHandRenderer()).isRenderItemInUse();
        } else {
            return false;
        }
    }
}
