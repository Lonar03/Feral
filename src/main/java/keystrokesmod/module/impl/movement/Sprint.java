package keystrokesmod.module.impl.movement;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.options.KeyBinding;

public class Sprint extends Module {
    private final ButtonSetting allowUsingItem;
    private final ButtonSetting allowBackwards;
    private final ButtonSetting allowSideways;
    private final ButtonSetting allowInInventory;

    public Sprint() {
        super("Sprint", Module.category.movement, 0);
        this.liteModule = true;
        this.registerSetting(new DescriptionSetting("Allow while"));
        this.registerSetting(this.allowUsingItem = new ButtonSetting("Using item", false));
        this.registerSetting(this.allowBackwards = new ButtonSetting("Backwards", false));
        this.registerSetting(this.allowSideways = new ButtonSetting("Sideways", false));
        this.registerSetting(this.allowInInventory = new ButtonSetting("In inventory", false));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        if (Utils.nullCheck()) {
            KeyBinding.set(mc.options.sprintKey.getKeyCode(), false);
        }
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck()) {
            boolean inGame = mc.focused;
            boolean inInv = this.allowInInventory.isToggled()
                    && (mc.screen instanceof SurvivalInventoryScreen || mc.screen instanceof ChestScreen);
            if (inGame || inInv) {
                KeyBinding.set(mc.options.sprintKey.getKeyCode(), true);
            }
        }
    }

    public boolean allowWhileUsingItem() {
        return this.isEnabled() && this.allowUsingItem.isToggled();
    }

    public boolean allowWhileBackwards() {
        return this.isEnabled() && this.allowBackwards.isToggled();
    }

    public boolean allowWhileSideways() {
        return this.isEnabled() && this.allowSideways.isToggled();
    }
}
