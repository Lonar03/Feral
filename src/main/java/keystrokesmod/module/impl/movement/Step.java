package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.StepHeightEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;

public class Step extends Module {
    private static final double[] OFFSETS = new double[] {0.42F, 0.7531999805212024};
    private final SliderSetting delay = new SliderSetting("Delay", "ms", 150.0, 0.0, 500.0, 50.0);
    private final ButtonSetting timer = new ButtonSetting("Timer", false);
    private final ButtonSetting speedCheck = new ButtonSetting("Speed check", false);
    private long delayStart;
    private boolean slowed;
    private double startX;
    private double startY;
    private double startZ;

    public Step() {
        super("Step", Module.category.movement);
        this.registerSetting(this.delay);
        this.registerSetting(this.timer);
        this.registerSetting(this.speedCheck);
    }

    @Override
    public void onEnable() {
        this.delayStart = System.nanoTime();
    }

    @Override
    public void onDisable() {
        this.releaseTimer();
    }

    @ForgeEvents.SubscribeEvent
    public void onStepHeight(StepHeightEvent event) {
        if (event.entity == mc.player && mc.player != null) {
            event.stepHeight = 0.6F;
            if ((!this.speedCheck.isToggled() || !ModuleManager.bHop.isEnabled()) && !mc.options.jumpKey.isPressed()) {
                if (ModuleManager.longJump.isEnabled()) {
                    event.stepHeight = 0.0F;
                } else {
                    if (mc.player.onGround
                            && mc.player.collidingVertically
                            && System.nanoTime() - this.delayStart >= this.delay.getInput() * 1000000.0) {
                        event.stepHeight = 1.0F;
                    }
                }
            }
        }
    }

    public void beginStep() {
        this.startX = mc.player.x;
        this.startY = mc.player.y;
        this.startZ = mc.player.z;
    }

    public void finishStep() {
        if (!(mc.player.y - this.startY <= 0.6F)) {
            if (this.timer.isToggled()) {
                ((IAccessorMinecraft) mc).getTimer().tpsScale = 0.5F;
                this.slowed = true;
            }

            for (double offset : OFFSETS) {
                mc.player.networkHandler.sendPacket(
                        new Position(this.startX, this.startY + offset, this.startZ, false));
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (this.slowed) {
            this.releaseTimer();
            this.delayStart = System.nanoTime();
        }
    }

    private void releaseTimer() {
        if (this.slowed) {
            if (((IAccessorMinecraft) mc).getTimer().tpsScale == 0.5F) {
                ((IAccessorMinecraft) mc).getTimer().tpsScale = 1.0F;
            }

            this.slowed = false;
        }
    }
}
