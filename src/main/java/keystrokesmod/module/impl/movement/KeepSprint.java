package keystrokesmod.module.impl.movement;

import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public class KeepSprint extends Module {
    public static SliderSetting slow;
    public static ButtonSetting stopSprint;
    public static ButtonSetting disableWhileJump;
    public static ButtonSetting reduceReachHits;

    public KeepSprint() {
        super("Keep Sprint", Module.category.movement, 0);
        this.registerSetting(new DescriptionSetting(new String("Default is 40% motion reduction.")));
        this.registerSetting(slow = new SliderSetting("Slow %", 40.0, 0.0, 40.0, 1.0));
        this.registerSetting(stopSprint = new ButtonSetting("Stop Sprint", true));
        this.registerSetting(disableWhileJump = new ButtonSetting("Disable while jumping", false));
        this.registerSetting(reduceReachHits = new ButtonSetting("Only reduce reach hits", false));
    }

    public static void keepSprint(Entity en) {
        boolean vanilla = false;
        if (disableWhileJump.isToggled() && !mc.player.onGround) {
            vanilla = true;
        } else if (reduceReachHits.isToggled() && !mc.player.abilities.creativeMode) {
            double distance = -1.0;
            Vec3d getPositionEyes = mc.player.getEyePosition(1.0F);
            if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
                distance = getPositionEyes.distanceTo(KillAura.target.getEyePosition(1.0F));
            } else if (ModuleManager.reach != null && ModuleManager.reach.isEnabled()) {
                distance = getPositionEyes.distanceTo(mc.crosshairTarget.facePos);
            }

            if (distance != -1.0 && distance <= 3.0) {
                vanilla = true;
            }
        }

        if (vanilla) {
            Velocity.applyAttackSlowdown(mc.player, 0.6, 0.6);
        } else {
            float mult = (100.0F - (float) slow.getInput()) / 100.0F;
            Velocity.applyAttackSlowdown(mc.player, mult, mult);
        }

        if (stopSprint.isToggled()) {
            Velocity.applyAttackSlowdown(mc.player, 0.5, 1.0);
        }
    }
}
