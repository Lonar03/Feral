package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Autoblock;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SwordItem;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class NoSlow extends Module {
    public static SliderSetting mode;
    public static SliderSetting slowed;
    public static ButtonSetting disableBow;
    public static ButtonSetting disablePotions;
    public static ButtonSetting swordOnly;
    public static ButtonSetting vanillaSword;
    private final String[] NOSLOW_MODES = new String[] {"Vanilla", "Beta"};
    public boolean noSlowing;
    private boolean setJump;

    public NoSlow() {
        super("NoSlow", Module.category.movement, 0);
        this.registerSetting(new DescriptionSetting("Default is 80% motion reduction."));
        this.registerSetting(mode = new SliderSetting("Mode", 0, this.NOSLOW_MODES));
        this.registerSetting(slowed = new SliderSetting("Slow %", 80.0, 0.0, 80.0, 1.0));
        this.registerSetting(disableBow = new ButtonSetting("Disable bow", false));
        this.registerSetting(disablePotions = new ButtonSetting("Disable potions", false));
        this.registerSetting(swordOnly = new ButtonSetting("Sword only", false));
        this.registerSetting(vanillaSword = new ButtonSetting("Vanilla sword", false));
    }

    @Override
    public void onDisable() {
        this.noSlowing = false;
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!vanillaSword.isToggled() || !Utils.holdingSword()) {
            boolean apply = getSlowed() != 0.2F;
            if (apply && isUsingItemForMovement(mc.player)) {
                switch ((int) mode.getInput()) {
                    case 1:
                        mc.player.networkHandler.sendPacket(
                                new SelectSlotC2SPacket(mc.player.inventory.selectedSlot % 8 + 1));
                        mc.player.networkHandler.sendPacket(new SelectSlotC2SPacket(mc.player.inventory.selectedSlot));
                        mc.player.networkHandler.sendPacket(new PlayerHandActionC2SPacket(
                                Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (this.setJump) {
            mc.player.input.jumping = true;
            this.setJump = false;
        }
    }

    public static boolean isUsingItemForMovement(PlayerEntity player) {
        if (player == null) {
            return false;
        }

        if (player.hasItemInUse()) {
            return true;
        }

        Autoblock autoblock = (Autoblock) ModuleManager.getModule(Autoblock.class);
        return player == mc.player && autoblock != null && autoblock.shouldApplyNoSlow();
    }

    public static float getSlowed() {
        if (mc.player.getDisplayItemInHand() == null
                || ModuleManager.noSlow == null
                || !ModuleManager.noSlow.isEnabled()) {
            return 0.2F;
        } else if (swordOnly.isToggled() && !(mc.player.getDisplayItemInHand().getItem() instanceof SwordItem)) {
            return 0.2F;
        } else if (mc.player.getDisplayItemInHand().getItem() instanceof BowItem && disableBow.isToggled()) {
            return 0.2F;
        } else {
            return mc.player.getDisplayItemInHand().getItem() instanceof PotionItem
                            && !PotionItem.isSplashPotion(
                                    mc.player.getDisplayItemInHand().getDamage())
                            && disablePotions.isToggled()
                    ? 0.2F
                    : (100.0F - (float) slowed.getInput()) / 100.0F;
        }
    }

    @Override
    public String getInfo() {
        return this.NOSLOW_MODES[(int) mode.getInput()];
    }
}
