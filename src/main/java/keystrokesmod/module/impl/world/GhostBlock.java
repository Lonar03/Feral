package keystrokesmod.module.impl.world;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;

/**
 * The module bind only toggles Ghost Block on or off; ghosting happens while the "Ghost key" is held (like Clutch's
 * select keybind).
 */
public class GhostBlock extends Module {
    public static ButtonSetting d;
    public static ButtonSetting fpsDebugInfo;
    private final KeySetting ghostKey;
    private boolean keyWasDown;

    public GhostBlock() {
        super("Ghost Block", Module.category.world);
        this.registerSetting(this.ghostKey = new KeySetting("Ghost key", 0));
        this.registerSetting(d = new ButtonSetting("Hold", true));
        this.registerSetting(fpsDebugInfo = new ButtonSetting("Debug", false));
    }

    @Override
    public void onEnable() {
        // Don't fire on the tick the module is enabled if the key happens to be held already.
        this.keyWasDown = this.ghostKey.isPressed();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !Utils.nullCheck()) {
            return;
        }

        boolean keyDown = mc.screen == null && this.ghostKey.isPressed();
        boolean justPressed = keyDown && !this.keyWasDown;
        this.keyWasDown = keyDown;
        if (d.isToggled() ? keyDown : justPressed) {
            this.ghostTargetedBlock();
        }
    }

    private void ghostTargetedBlock() {
        HitResult ray = mc.crosshairTarget;
        if (ray == null || ray.type != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos blockpos = ray.getPos();
        Block block = mc.world.getBlockState(blockpos).getBlock();
        if (block instanceof ChestBlock
                || block == Blocks.STONE_BUTTON
                || block == Blocks.WOODEN_BUTTON
                || block == Blocks.DISPENSER
                || block == Blocks.CAULDRON) {
            return;
        }

        if (fpsDebugInfo.isToggled()) {
            mc.player.sendMessage(
                    new LiteralText("[GhostBlock] " + blockpos.getX() + " " + blockpos.getY() + " " + blockpos.getZ()));
        }

        mc.world.removeBlock(blockpos);
    }
}
