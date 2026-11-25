package keystrokesmod.module.impl.player;

import keystrokesmod.Feral;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class WaterBucket extends Module {
    public ButtonSetting pickupWater;
    public ButtonSetting silentAim;
    public ButtonSetting switchToItem;
    private final long PLACE_DELAY = 500L;
    private final long PICKUP_WAIT = 150L;
    private long lastPlace = 0L;
    private boolean shouldPickup = false;
    private int lastSlot = -1;

    public WaterBucket() {
        super("Water Bucket", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.pickupWater = new ButtonSetting("Pickup water", true));
        this.registerSetting(this.silentAim = new ButtonSetting("Silent aim", true));
        this.registerSetting(this.switchToItem = new ButtonSetting("Switch to item", true));
    }

    @Override
    public void onDisable() {
        this.lastPlace = 0L;
        this.shouldPickup = false;
        this.lastSlot = -1;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && !mc.isPaused() && !mc.player.abilities.flying && !mc.player.abilities.creativeMode) {
            if (this.fallCheck()) {
                HitResult mop = Utils.getTarget(
                        mc.interactionManager.getReach(),
                        mc.player.yaw,
                        this.silentAim.isToggled() ? 90.0F : mc.player.pitch);
                if (mop != null && mop.type == Type.BLOCK && mop.face == Direction.UP) {
                    long now = System.currentTimeMillis();
                    if (Utils.timeBetween(this.lastPlace, now) >= 500L) {
                        if (!this.isItem(mc.player.getDisplayItemInHand(), Items.WATER_BUCKET)
                                && this.switchToItem.isToggled()) {
                            this.attemptSwitch();
                        }

                        if (this.silentAim.isToggled() || !(mc.player.pitch < 80.0F)) {
                            this.lastPlace = now;
                            this.useCurrentItem();
                            if (!(this.shouldPickup = this.pickupWater.isToggled())) {
                                this.lastSlot = -1;
                            }

                            if (Feral.DEBUG) {
                                Utils.sendModuleMessage(
                                        this,
                                        "&7Placed with motionY &d"
                                                + Utils.round(mc.player.velocityY, 2)
                                                + " &7and fall distance &d"
                                                + Utils.round(mc.player.fallDistance, 2));
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!mc.isPaused()) {
            if (this.shouldPickup
                    && Utils.timeBetween(this.lastPlace, System.currentTimeMillis()) > 150L
                    && this.isItem(mc.player.getDisplayItemInHand(), Items.BUCKET)) {
                this.shouldPickup = false;
                this.useCurrentItem();
                if (this.lastSlot != -1) {
                    Utils.switchSlot(this.lastSlot, true);
                    this.lastSlot = -1;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
            if (this.silentAim.isToggled()
                    && (this.fallCheck() || Utils.timeBetween(this.lastPlace, System.currentTimeMillis()) < 500L)
                    && this.getWaterBucketSlot() != -1) {
                e.setYaw(mc.player.yaw);
                e.setPitch(90.0F);
            }
        }
    }

    private void attemptSwitch() {
        int slot = this.getWaterBucketSlot();
        if (slot != -1) {
            this.lastSlot = mc.player.inventory.selectedSlot;
            Utils.switchSlot(slot, true);
        }
    }

    private int getWaterBucketSlot() {
        for (int slot = 0; slot < PlayerInventory.getHotbarSize(); slot++) {
            if (this.isItem(mc.player.inventory.getItem(slot), Items.WATER_BUCKET)) {
                return slot;
            }
        }

        return -1;
    }

    private void useCurrentItem() {
        mc.getNetworkHandler().sendPacket(new PlayerUseC2SPacket(mc.player.getDisplayItemInHand()));
    }

    private boolean isItem(ItemStack itemStack, Item item) {
        return itemStack != null && itemStack.getItem() == item;
    }

    private boolean fallCheck() {
        return !mc.player.onGround && mc.player.fallDistance >= 3.3;
    }
}
