package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import net.minecraft.entity.living.LivingEntity;

public class DamageTint extends Module {
    public static DamageTint instance;
    public final ColorSetting color;
    public final ButtonSetting fade;

    public DamageTint() {
        super("Damage Tint", Module.category.render, 0);
        this.registerSetting(this.color = new ColorSetting("Tint color", 255, 0, 0, 76));
        this.registerSetting(this.fade = new ButtonSetting("Fade out", false));
    }

    @Override
    public void onEnable() {
        instance = this;
    }

    @Override
    public void onDisable() {
        instance = null;
    }

    public static float computeAlpha(LivingEntity entity) {
        if (instance != null && instance.fade.isToggled()) {
            float maxHurt = entity.damagedTime;
            if (maxHurt <= 0.0F) {
                return instance.color.getAlpha() / 255.0F;
            }

            float percent = 1.0F - entity.damagedTimer / maxHurt;
            percent = percent < 0.5F ? percent / 0.5F : (1.0F - percent) / 0.5F;
            return instance.color.getAlpha() / 255.0F * percent;
        } else {
            return instance.color.getAlpha() / 255.0F;
        }
    }
}
