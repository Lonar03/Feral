package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.SliderSetting;

public class ExtraBobbing extends Module {
    public SliderSetting level;
    private boolean viewBobbingEnabled;

    public ExtraBobbing() {
        super("Extra Bobbing", Module.category.render);
        this.registerSetting(this.level = new SliderSetting("Level", 1.0, 0.0, 8.0, 0.1));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.options.viewBobbing;
        if (!this.viewBobbingEnabled) {
            mc.options.viewBobbing = true;
        }
    }

    @Override
    public void onDisable() {
        mc.options.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (!mc.options.viewBobbing) {
            mc.options.viewBobbing = true;
        }

        if (mc.player.input.movementForward != 0.0F || mc.player.input.movementSideways != 0.0F) {
            mc.player.bob = (float) (mc.player.bob + this.level.getInput() / 2.0);
        }
    }
}
