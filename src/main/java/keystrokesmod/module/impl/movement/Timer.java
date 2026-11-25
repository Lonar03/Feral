package keystrokesmod.module.impl.movement;

import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.event.RunGameLoopEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.Scaffold;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.menu.GameMenuScreen;
import net.minecraft.client.options.GameOptions;
import org.lwjgl.input.Mouse;

public class Timer extends Module {
    private final SliderSetting multiplier = new SliderSetting("Multiplier slider", 0, new String[] {"A", "B", "C"});
    private final SliderSetting[] speeds = new SliderSetting[] {
        new SliderSetting("Slider A", "x", 1.0, 0.7, 1.5, 0.01, "Speed").allowOutOfRangeProfileValues(),
        new SliderSetting("Slider B", "x", 0.0, 0.0, 0.7, 0.01).allowOutOfRangeProfileValues(),
        new SliderSetting("Slider C", "x", 3.0, 1.5, 9.0, 0.1).allowOutOfRangeProfileValues()
    };
    private final ButtonSetting disableChatMenu = new ButtonSetting("Disable chat & menu", false);
    private final ButtonSetting disableOnTeleport = new ButtonSetting("Disable on teleport", false);
    private final ButtonSetting resetOnInput = new ButtonSetting("Reset on input", false);
    private final ButtonSetting strafeOnly = new ButtonSetting("Strafe only", false);
    private final ButtonSetting onlyWhileScaffolding = new ButtonSetting("Only while scaffolding", false);
    private final ButtonSetting onlyWhileBhopping = new ButtonSetting("Only while bhopping", false);
    private float appliedSpeed;
    private boolean resetByInput;
    private long lastPoll;

    public Timer() {
        super("Timer", Module.category.movement);
        this.registerSetting(this.multiplier);

        for (SliderSetting speed : this.speeds) {
            this.registerSetting(speed);
        }

        this.registerSetting(this.disableChatMenu);
        this.registerSetting(this.disableOnTeleport);
        this.registerSetting(this.resetOnInput);
        this.registerSetting(this.strafeOnly);
        this.registerSetting(this.onlyWhileScaffolding);
        this.registerSetting(this.onlyWhileBhopping);
    }

    private SliderSetting activeSlider() {
        return this.speeds[(int) this.multiplier.getInput()];
    }

    public double getSpeed() {
        return this.activeSlider().getInput();
    }

    public void setSpeed(double value) {
        this.activeSlider().setValueRawWithEvent(value);
    }

    public void resetSpeed() {
        this.setSpeed(1.0);
    }

    @Override
    public String getInfo() {
        return Utils.asWholeNum(this.getSpeed());
    }

    private boolean inClientGui() {
        return mc.screen instanceof ClickGui || mc.screen instanceof HUD.EditScreen;
    }

    @Override
    public void onDisable() {
        this.releaseSpeed();
        this.resetByInput = false;
        this.lastPoll = 0L;
    }

    private void releaseSpeed() {
        if (this.appliedSpeed > 0.0F) {
            Utils.resetTimer();
            this.appliedSpeed = 0.0F;
        }
    }

    private boolean matchesModuleSettings() {
        return (!this.onlyWhileScaffolding.isToggled() || Scaffold.instance != null && Scaffold.instance.isEnabled())
                && (!this.onlyWhileBhopping.isToggled()
                        || ModuleManager.bHop != null && ModuleManager.bHop.isEnabled());
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck()
                && this.matchesModuleSettings()
                && !this.resetByInput
                && (!this.strafeOnly.isToggled() || mc.player.sidewaysSpeed != 0.0F)) {
            double speed = this.getSpeed();
            this.appliedSpeed = speed <= 0.0 ? 5.0E-4F : (float) speed;
            ((IAccessorMinecraft) mc).getTimer().tpsScale = this.appliedSpeed;
        } else {
            this.releaseSpeed();
            this.resetByInput = false;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGameLoop(RunGameLoopEvent event) {
        long now = System.nanoTime();
        if (this.lastPoll == 0L || now - this.lastPoll >= 50000000L) {
            this.lastPoll = now;
            if (!Utils.nullCheck()) {
                this.disable();
            } else {
                boolean slowed = this.appliedSpeed > 0.0F && this.appliedSpeed < 0.25F;
                if (slowed && mc.screen != null && !this.inClientGui()) {
                    this.disable();
                } else {
                    if (mc.screen == null) {
                        this.onKeyBind();
                        if (!this.isEnabled()) {
                            return;
                        }

                        if (slowed) {
                            Scaffold.instance.onKeyBind();
                            ModuleManager.getModule("Stasis").onKeyBind();
                            if (this.onlyWhileBhopping.isToggled()) {
                                ModuleManager.bHop.onKeyBind();
                            }
                        }
                    }

                    if (!this.matchesModuleSettings()) {
                        this.releaseSpeed();
                    } else if (this.resetOnInput.isToggled() && this.hasResetInput()) {
                        this.releaseSpeed();
                        this.resetByInput = true;
                    }
                }
            }
        }
    }

    private boolean hasResetInput() {
        if (!Mouse.isButtonDown(0) && !Mouse.isButtonDown(1)) {
            for (int i = 0; i < mc.options.hotbarKeyBindings.length; i++) {
                if (GameOptions.isPressed(mc.options.hotbarKeyBindings[i])) {
                    return i != mc.player.inventory.selectedSlot;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGuiOpen(ForgeEvents.GuiOpenEvent event) {
        if (this.matchesModuleSettings()
                && this.disableChatMenu.isToggled()
                && (event.gui instanceof ChatScreen || event.gui instanceof GameMenuScreen)) {
            event.setCanceled(true);
            Utils.sendMessage(event.gui instanceof ChatScreen ? "&cCannot open chat." : "&cCannot open menu.");
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onTeleport(PlayerTeleportEvent event) {
        if (mc.isOnSameThread()
                && this.appliedSpeed > 0.0F
                && this.matchesModuleSettings()
                && this.disableOnTeleport.isToggled()) {
            this.disable();
        }
    }
}
