package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.options.KeyBinding;

public class AntiAFK extends Module {
    private SliderSetting afk;
    private ButtonSetting jump;
    private ButtonSetting jumpWhenCollided;
    private ButtonSetting randomClicks;
    private ButtonSetting swapItem;
    private SliderSetting spin;
    private ButtonSetting randomizeDelta;
    private ButtonSetting randomizePitch;
    private SliderSetting minDelay;
    private SliderSetting maxDelay;
    private String[] afkModes = new String[] {"None", "Wander", "Lateral shuffle", "Forward", "Backward"};
    private String[] spinModes = new String[] {"None", "Random", "Right", "Left"};
    private int ticks;
    private boolean random;
    public boolean stop = false;

    public AntiAFK() {
        super("AntiAFK", Module.category.player);
        this.registerSetting(this.afk = new SliderSetting("AFK", 0, this.afkModes));
        this.registerSetting(this.jump = new ButtonSetting("Jump", false));
        this.registerSetting(this.jumpWhenCollided = new ButtonSetting("Jump only when collided", false));
        this.registerSetting(this.randomClicks = new ButtonSetting("Random clicks", false));
        this.registerSetting(this.swapItem = new ButtonSetting("Swap item", false));
        this.registerSetting(this.spin = new SliderSetting("Spin", 0, this.spinModes));
        this.registerSetting(this.randomizeDelta = new ButtonSetting("Randomize delta", true));
        this.registerSetting(this.randomizePitch = new ButtonSetting("Randomize pitch", true));
        this.registerSetting(this.minDelay = new SliderSetting("Minimum delay ticks", 10.0, 4.0, 160.0, 2.0));
        this.registerSetting(this.maxDelay = new SliderSetting("Maximum delay ticks", 80.0, 4.0, 160.0, 2.0));
    }

    @Override
    public void onEnable() {
        Utils.sendMessage("Bye");
        this.ticks = this.h();
        this.random = Utils.getRandom().nextBoolean();
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.stop) {
            if (mc.screen == null || mc.screen instanceof ChatScreen) {
                this.ticks--;
                switch ((int) this.afk.getInput()) {
                    case 1:
                        if (this.random) {
                            KeyBinding.set(
                                    mc.options.forwardKey.getKeyCode(),
                                    Utils.getRandom().nextBoolean());
                            KeyBinding.set(
                                    mc.options.rightKey.getKeyCode(),
                                    Utils.getRandom().nextBoolean());
                        } else {
                            KeyBinding.set(
                                    mc.options.backKey.getKeyCode(),
                                    Utils.getRandom().nextBoolean());
                            KeyBinding.set(
                                    mc.options.leftKey.getKeyCode(),
                                    Utils.getRandom().nextBoolean());
                        }
                        break;
                    case 2:
                        KeyBinding.set(mc.options.rightKey.getKeyCode(), this.random);
                        KeyBinding.set(mc.options.leftKey.getKeyCode(), !this.random);
                        break;
                    case 3:
                        KeyBinding.set(mc.options.forwardKey.getKeyCode(), true);
                        break;
                    case 4:
                        KeyBinding.set(mc.options.backKey.getKeyCode(), true);
                }

                switch ((int) this.spin.getInput()) {
                    case 1:
                        mc.player.yaw = (float) (mc.player.yaw + this.c(this.random));
                        this.d();
                        break;
                    case 2:
                        mc.player.yaw = (float) (mc.player.yaw + this.c(true));
                        this.d();
                        break;
                    case 3:
                        mc.player.yaw = (float) (mc.player.yaw + this.c(false));
                        this.d();
                }

                if (this.jump.isToggled()
                        && mc.player.onGround
                        && (!this.jumpWhenCollided.isToggled() || mc.player.collidingHorizontally)) {
                    mc.player.jump();
                }

                if (this.ticks == 0) {
                    if (this.swapItem.isToggled()) {
                        mc.player.inventory.selectedSlot = Utils.randomizeInt(0, 8);
                    }

                    if (this.randomClicks.isToggled()) {
                        ((IAccessorMinecraft) mc).callClickMouse();
                    }

                    this.ticks = this.h();
                    this.random = !this.random;
                }
            }
        }
    }

    private double a() {
        int n = Utils.getRandom().nextBoolean() ? 1 : -1;
        if (!this.randomizeDelta.isToggled()) {
            return 2 * n;
        }

        double n2 = Utils.randomizeInt(100, 500) / 100.0;
        if (n2 % 1.0 == 0.0) {
            n2 += Utils.randomizeInt(1, 10) / 10.0 * n;
        }

        return n2 * n;
    }

    @Override
    public void onDisable() {
        this.b(0);
        this.stop = false;
    }

    private void b(int n) {
        switch (n) {
            case 1:
                KeyBinding.set(mc.options.forwardKey.getKeyCode(), false);
                KeyBinding.set(mc.options.backKey.getKeyCode(), false);
                KeyBinding.set(mc.options.rightKey.getKeyCode(), false);
                KeyBinding.set(mc.options.leftKey.getKeyCode(), false);
                break;
            case 2:
                KeyBinding.set(mc.options.rightKey.getKeyCode(), false);
                KeyBinding.set(mc.options.leftKey.getKeyCode(), false);
                break;
            case 3:
                KeyBinding.set(mc.options.forwardKey.getKeyCode(), false);
                break;
            case 4:
                KeyBinding.set(mc.options.backKey.getKeyCode(), false);
        }
    }

    private int h() {
        return this.minDelay.getInput() == this.maxDelay.getInput()
                ? (int) this.minDelay.getInput()
                : Utils.randomizeInt((int) this.minDelay.getInput(), (int) this.maxDelay.getInput());
    }

    private void d() {
        if (this.randomizePitch.isToggled()) {
            mc.player.pitch = RotationUtils.clampPitch((float) (mc.player.pitch + this.a()));
        }
    }

    private double c(boolean b) {
        int n = b ? 1 : -1;
        if (!this.randomizeDelta.isToggled()) {
            return 3 * n;
        }

        double n2 = Utils.randomizeInt(100, 1000) / 100.0;
        if (n2 % 1.0 == 0.0) {
            n2 += Utils.randomizeInt(1, 10) / 10.0 * n;
        }

        return n2 * n;
    }
}
