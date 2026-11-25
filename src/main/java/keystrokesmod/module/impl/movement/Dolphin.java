package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import org.lwjgl.input.Keyboard;

public class Dolphin extends Module {
    public SliderSetting horSpeed;
    public SliderSetting verSpeed;
    public ButtonSetting buoyant;
    public ButtonSetting disableUsing;
    public ButtonSetting disableVerticalWhileMoving;
    public ButtonSetting forwardOnly;

    public Dolphin() {
        super("Dolphin", Module.category.movement, 0);
        this.registerSetting(this.horSpeed = new SliderSetting("Horizontal speed", 1.0, 1.0, 8.0, 0.1));
        this.registerSetting(this.verSpeed = new SliderSetting("Vertical speed", 1.0, 1.0, 8.0, 0.1));
        this.registerSetting(this.buoyant = new ButtonSetting("Buoyant", false));
        this.registerSetting(this.disableUsing = new ButtonSetting("Disable while using", true));
        this.registerSetting(
                this.disableVerticalWhileMoving = new ButtonSetting("Disable vertical while moving", false));
        this.registerSetting(this.forwardOnly = new ButtonSetting("Forward only", true));
    }

    @ForgeEvents.SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        if (mc.player.isInWater() && !mc.player.abilities.flying) {
            if (!this.disableUsing.isToggled() || !mc.player.hasItemInUse()) {
                if (!this.forwardOnly.isToggled() || Keyboard.isKeyDown(mc.options.forwardKey.getKeyCode())) {
                    if (this.buoyant.isToggled()) {
                        mc.player.velocityY = 0.0;
                    }

                    if (Utils.isUserMoving()) {
                        double horizontalSpeed = 0.078 * this.horSpeed.getInput();
                        Utils.setSpeed(horizontalSpeed);
                        if (this.disableVerticalWhileMoving.isToggled()) {
                            return;
                        }
                    }

                    if (Keyboard.isKeyDown(mc.options.jumpKey.getKeyCode())) {
                        mc.player.velocityY = 0.02 + 0.04 * this.verSpeed.getInput();
                    } else if (!mc.player.onGround && Keyboard.isKeyDown(mc.options.sneakKey.getKeyCode())) {
                        mc.player.input.sneaking = false;
                        mc.player.velocityY = -0.1 - 0.03 * this.verSpeed.getInput();
                    } else if (this.buoyant.isToggled()) {
                        mc.player.velocityY = 0.0;
                    }
                }
            }
        }
    }
}
