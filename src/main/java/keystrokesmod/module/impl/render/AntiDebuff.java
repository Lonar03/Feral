package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import net.minecraft.entity.living.effect.StatusEffect;

public class AntiDebuff extends Module {
    public ButtonSetting removeBlindness;
    public ButtonSetting removeNausea;
    public ButtonSetting removeSideEffects;

    public AntiDebuff() {
        super("Anti Debuff", Module.category.render);
        this.liteModule = true;
        this.registerSetting(this.removeBlindness = new ButtonSetting("Remove blindness", true));
        this.registerSetting(this.removeNausea = new ButtonSetting("Remove nausea", true));
        this.registerSetting(this.removeSideEffects = new ButtonSetting("Remove side effects", false));
    }

    public boolean canRemoveBlindness(StatusEffect potion) {
        return this.isEnabled() && potion == StatusEffect.BLINDNESS && this.removeBlindness.isToggled();
    }

    public boolean canRemoveNausea(StatusEffect potion) {
        return this.isEnabled() && potion == StatusEffect.NAUSEA && this.removeNausea.isToggled();
    }
}
