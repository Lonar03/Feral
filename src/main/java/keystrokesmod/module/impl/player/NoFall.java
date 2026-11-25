package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;

public class NoFall extends Module {
    public SliderSetting mode;
    private SliderSetting minFallDistance;
    private ButtonSetting disableAdventure;
    private ButtonSetting ignoreVoid;
    private String[] modes = new String[] {"Spoof", "NoGround", "Packet A", "Packet B"};
    private double initialY;
    private double dynamic;
    private boolean isFalling;

    public NoFall() {
        super("NoFall", Module.category.player);
        this.registerSetting(this.mode = new SliderSetting("Mode", 2, this.modes));
        this.registerSetting(this.minFallDistance = new SliderSetting("Minimum fall distance", 3.0, 0.0, 10.0, 0.1));
        this.registerSetting(this.disableAdventure = new ButtonSetting("Disable adventure", false));
        this.registerSetting(this.ignoreVoid = new ButtonSetting("Ignore void", true));
    }

    @Override
    public void onDisable() {
        Utils.resetTimer();
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.reset()) {
            Utils.resetTimer();
            this.initialY = mc.player.y;
            this.isFalling = false;
        } else {
            if (mc.player.fallDistance >= this.minFallDistance.getInput()) {
                this.isFalling = true;
            }

            double predictedY = mc.player.y + mc.player.velocityY;
            double distanceFallen = this.initialY - predictedY;
            if (mc.player.velocityY >= -1.0) {
                this.dynamic = 3.0;
            }

            if (mc.player.velocityY < -1.0) {
                this.dynamic = 4.0;
            }

            if (mc.player.velocityY < -2.0) {
                this.dynamic = 5.0;
            }

            if (this.isFalling && this.mode.getInput() == 2.0 && distanceFallen >= this.dynamic) {
                ((IAccessorMinecraft) mc).getTimer().tpsScale =
                        0.7399789F + (float) Utils.randomizeDouble(-0.012, 0.012);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket(true));
                this.initialY = mc.player.y;
            }

            if (this.isFalling && this.mode.getInput() == 3.0) {
                if (mc.player.ticks % 2 == 0) {
                    ((IAccessorMinecraft) mc).getTimer().tpsScale = (float) Utils.randomizeDouble(0.5, 0.50201);
                } else {
                    ((IAccessorMinecraft) mc).getTimer().tpsScale = 1.0F;
                }

                if (distanceFallen >= 3.0) {
                    mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket(true));
                    this.initialY = mc.player.y;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        switch ((int) this.mode.getInput()) {
            case 0:
                e.setOnGround(true);
                break;
            case 1:
                e.setOnGround(false);
        }
    }

    @Override
    public String getInfo() {
        return this.modes[(int) this.mode.getInput()];
    }

    private boolean isVoid() {
        return Utils.overVoid(mc.player.x, mc.player.y, mc.player.z);
    }

    private boolean reset() {
        if (this.disableAdventure.isToggled()
                && mc.interactionManager.getGameMode().restrictsWorldModification()) {
            return true;
        } else if (this.ignoreVoid.isToggled() && this.isVoid()) {
            return true;
        } else if (Utils.isBedwarsPracticeOrReplay()) {
            return true;
        } else if (Utils.spectatorCheck()) {
            return true;
        } else if (mc.player.onGround) {
            return true;
        } else if (BlockUtils.getBlock(new BlockPos(mc.player.x, mc.player.y - 1.0, mc.player.z)) != Blocks.AIR) {
            return true;
        } else if (mc.player.velocityY > -0.0784) {
            return true;
        } else if (mc.player.abilities.creativeMode) {
            return true;
        } else {
            return this.isVoid() && mc.player.y <= 41.0 ? true : mc.player.abilities.flying;
        }
    }
}
