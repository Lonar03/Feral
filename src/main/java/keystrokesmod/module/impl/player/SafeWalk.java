package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

public class SafeWalk extends Module {
    private SliderSetting sneakDelay;
    private SliderSetting motion;
    public ButtonSetting sneak;
    public static ButtonSetting blocksOnly;
    public static ButtonSetting pitchCheck;
    public static ButtonSetting disableOnForward;
    private int unsneakDelayTicks = 0;
    public boolean isSneaking;

    public SafeWalk() {
        super("SafeWalk", Module.category.player, 0);
        this.liteModule = true;
        this.registerSetting(this.sneakDelay = new SliderSetting("Sneak delay", " tick", 0.0, 0.0, 20.0, 1.0));
        this.registerSetting(this.motion = new SliderSetting("Motion", "x", 1.0, 0.5, 1.2, 0.01));
        this.registerSetting(blocksOnly = new ButtonSetting("Blocks only", true));
        this.registerSetting(disableOnForward = new ButtonSetting("Disable on forward", false));
        this.registerSetting(pitchCheck = new ButtonSetting("Pitch check", false));
        this.registerSetting(this.sneak = new ButtonSetting("Sneak", false));
    }

    @Override
    public void onDisable() {
        if (this.sneak.isToggled() && Utils.isEdgeOfBlock()) {
            this.setSneakState(false);
        }

        this.isSneaking = false;
        this.unsneakDelayTicks = 0;
    }

    @Override
    public void onUpdate() {
        if (this.motion.getInput() != 1.0 && mc.player.onGround && Utils.isMoving() && this.settingsMet()) {
            mc.player.velocityX = mc.player.velocityX * this.motion.getInput();
            mc.player.velocityZ = mc.player.velocityZ * this.motion.getInput();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.sneak.isToggled() && Utils.nullCheck()) {
            boolean edge = mc.player.onGround && Utils.isEdgeOfBlock();
            if (edge) {
                if (!this.settingsMet()) {
                    this.setSneakState(false);
                    return;
                }

                if (!this.isSneaking) {
                    this.setSneakState(true);
                    this.unsneakDelayTicks = (int) this.sneakDelay.getInput();
                }
            } else if (this.isSneaking) {
                if (!this.settingsMet()) {
                    this.setSneakState(false);
                    return;
                }

                if (this.unsneakDelayTicks > 0) {
                    this.unsneakDelayTicks--;
                    return;
                }

                this.setSneakState(false);
            }

            if (this.isSneaking && (mc.player.abilities.flying || !this.settingsMet())) {
                this.setSneakState(false);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGuiOpen(ForgeEvents.GuiOpenEvent e) {
        if (this.sneak.isToggled() && e.gui == null) {
            this.isSneaking = mc.player.isSneaking();
        }
    }

    private void setSneakState(boolean sneakState) {
        if (!sneakState) {
            this.unsneakDelayTicks = 0;
        }

        if (this.isSneaking != sneakState) {
            if (sneakState || !Utils.isBindDown(mc.options.sneakKey)) {
                KeyBinding.set(mc.options.sneakKey.getKeyCode(), sneakState);
                if (sneakState) {
                    KeyBinding.click(mc.options.sneakKey.getKeyCode());
                }

                this.isSneaking = sneakState;
            }
        }
    }

    public static boolean canSafeWalk() {
        if (ScaffoldAdjust.safeWalkRequest
                && ModuleManager.scaffoldAdjust != null
                && ModuleManager.scaffoldAdjust.isEnabled()) {
            return true;
        }

        if (Tower.towering && Tower.disableSafewalk.isToggled()) {
            return false;
        }

        if (Scaffold.canSafeWalk()) {
            return true;
        }

        if (ModuleManager.safeWalk == null || !ModuleManager.safeWalk.isEnabled()) {
            return false;
        }

        if (disableOnForward.isToggled() && Keyboard.isKeyDown(mc.options.forwardKey.getKeyCode())) {
            return false;
        }

        if (pitchCheck.isToggled() && mc.player.pitch < 70.0F) {
            return false;
        }

        if (blocksOnly.isToggled()) {
            ItemStack held = mc.player.getDisplayItemInHand();
            if (held == null || !(held.getItem() instanceof BlockItem)) {
                return false;
            }
        }

        return true;
    }

    private boolean settingsMet() {
        if (blocksOnly.isToggled()) {
            ItemStack held = mc.player.getDisplayItemInHand();
            if (held == null || !(held.getItem() instanceof BlockItem)) {
                return false;
            }
        }

        return disableOnForward.isToggled() && Keyboard.isKeyDown(mc.options.forwardKey.getKeyCode())
                ? false
                : !pitchCheck.isToggled() || !(mc.player.pitch < 70.0F);
    }
}
