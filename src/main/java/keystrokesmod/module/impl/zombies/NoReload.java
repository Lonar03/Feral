package keystrokesmod.module.impl.zombies;

import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesHotbar;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import org.lwjgl.input.Keyboard;

/**
 * Puts the held gun into a no-reload state, automating the manual trick: open the inventory, hover
 * a spare hotbar slot, press the gun's hotbar key then the spare slot's key, and close; Hypixel
 * then switches you to the spare slot. The gun keeps firing without reloading until you switch to
 * another slot; press the key again after switching back.
 *
 * <p>The inventory really opens and each step waits a few ticks, like doing it by hand: the open
 * screen releases the movement keys, so the player has stopped sprinting before anything is clicked.
 */
public class NoReload extends Module {
    /** Hotbar indices of the gun slots (2-4); slot 1 is the knife. */
    private static final int FIRST_GUN_SLOT = 1;

    private static final int LAST_GUN_SLOT = 3;
    /**
     * Hotbar indices that can hold the gun instead (7-9). Slot 5 is skipped: on Prison and Alien Arcadium it holds
     * the skill item, which plays a sound and a chat message when the gun is right-clicked from there.
     */
    private static final int FIRST_SPARE_SLOT = 6;

    private static final int LAST_SPARE_SLOT = 8;
    /** Container slot of hotbar index 0 in the player inventory window. */
    private static final int HOTBAR_CONTAINER_OFFSET = 36;
    /** Window click mode for "swap with hotbar key". */
    private static final int MODE_HOTBAR_SWAP = 2;

    private final KeySetting applyKey = new KeySetting("Apply key", Keyboard.KEY_NONE);
    private final SliderSetting stepDelay = new SliderSetting("Step delay", " ticks", 2.0, 1.0, 10.0, 1.0);
    /** Hotbar slots holding one of these are not guns. Defaults to light gray dye, Zombies' empty weapon slot. */
    private final ItemListSetting ignoredItems = new ItemListSetting("Ignored items");
    /** Never moved into the gun's slot. Defaults to the "leave game" bed. */
    private final ItemListSetting neverSwapWith = new ItemListSetting("Never swap with");
    /**
     * After closing, re-press movement keys that are still held. Vanilla 1.8.9 waits for a new key
     * press after any screen closes (newer versions re-sync like this).
     */
    private final ButtonSetting resumeMovement = new ButtonSetting("Resume movement", true);

    private boolean keyWasDown;
    private Step step = Step.IDLE;
    private int ticksUntilNextStep;
    private int gunSlot;
    private int spareSlot;

    public NoReload() {
        super("No Reload", Module.category.zombies);
        this.registerSetting(new DescriptionSetting("Hold a gun and press the key. Switching slots undoes it."));
        this.registerSetting(this.applyKey);
        this.registerSetting(this.stepDelay);
        this.ignoredItems.addItem("minecraft:dye:7");
        this.registerSetting(this.ignoredItems);
        this.neverSwapWith.addItem("minecraft:bed");
        this.registerSetting(this.neverSwapWith);
        this.registerSetting(this.resumeMovement);
    }

    @Override
    public void onEnable() {
        this.keyWasDown = true; // ignore a key already held while enabling
        this.step = Step.IDLE;
    }

    @Override
    public void onDisable() {
        if (this.step != Step.IDLE && mc.screen instanceof SurvivalInventoryScreen) {
            mc.player.closeMenu();
        }
        this.step = Step.IDLE;
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase != Phase.START || !Utils.nullCheck()) {
            return;
        }

        if (this.step != Step.IDLE) {
            this.advance();
            return;
        }

        boolean keyDown = mc.screen == null && this.applyKey.getKey() != 0 && this.applyKey.isPressed();
        if (keyDown && !this.keyWasDown) {
            this.start();
        }
        this.keyWasDown = keyDown;
    }

    /** Checks the hotbar, then opens the inventory the way pressing the inventory key does. */
    private void start() {
        this.gunSlot = mc.player.inventory.selectedSlot;
        if (this.gunSlot < FIRST_GUN_SLOT
                || this.gunSlot > LAST_GUN_SLOT
                || !ZombiesHotbar.isWeaponSlot(this.gunSlot, this.ignoredItems)) {
            Utils.sendModuleMessage(this, "&cHold a gun (slot 2-4) first.");
            return;
        }

        this.spareSlot = this.findSpareSlot();
        if (this.spareSlot == -1) {
            Utils.sendModuleMessage(this, "&cNo usable non-empty slot in 7-9 to swap with.");
            return;
        }

        mc.player.networkHandler.sendPacket(
                new ClientStatusC2SPacket(ClientStatusC2SPacket.Status.OPEN_INVENTORY_ACHIEVEMENT));
        mc.openScreen(new SurvivalInventoryScreen(mc.player));
        this.next(Step.PRESS_GUN_KEY);
    }

    private void advance() {
        // Stop if the player closed the inventory or something else opened a screen.
        if (!(mc.screen instanceof SurvivalInventoryScreen)) {
            this.step = Step.IDLE;
            return;
        }
        if (--this.ticksUntilNextStep > 0) {
            return;
        }

        int containerSlot = HOTBAR_CONTAINER_OFFSET + this.spareSlot;
        int networkId = mc.player.playerMenu.networkId;
        switch (this.step) {
            case PRESS_GUN_KEY:
                // Hovering the spare slot, press the gun's hotbar key: the gun moves there.
                mc.interactionManager.clickSlot(networkId, containerSlot, this.gunSlot, MODE_HOTBAR_SWAP, mc.player);
                this.next(Step.PRESS_SPARE_KEY);
                break;
            case PRESS_SPARE_KEY:
                mc.interactionManager.clickSlot(networkId, containerSlot, this.spareSlot, MODE_HOTBAR_SWAP, mc.player);
                this.next(Step.CLOSE);
                break;
            case CLOSE:
                // Hypixel switches the held slot to the spare slot itself.
                mc.player.closeMenu();
                if (this.resumeMovement.isToggled()) {
                    resyncMovementKeys();
                }
                this.step = Step.IDLE;
                break;
            default:
                this.step = Step.IDLE;
        }
    }

    /** Sets movement, jump, sneak and sprint bindings to whether their key is physically held. */
    private static void resyncMovementKeys() {
        KeyBinding[] keys = {
            mc.options.forwardKey,
            mc.options.backKey,
            mc.options.leftKey,
            mc.options.rightKey,
            mc.options.jumpKey,
            mc.options.sneakKey,
            mc.options.sprintKey
        };
        for (KeyBinding key : keys) {
            if (key.getKeyCode() != 0) {
                KeyBinding.set(key.getKeyCode(), Utils.isBindDown(key));
            }
        }
    }

    private void next(Step nextStep) {
        this.step = nextStep;
        this.ticksUntilNextStep = (int) this.stepDelay.getInput();
    }

    /** First non-empty hotbar slot in 7-9 that isn't in "Never swap with". */
    private int findSpareSlot() {
        for (int slot = FIRST_SPARE_SLOT; slot <= LAST_SPARE_SLOT; slot++) {
            ItemStack stack = mc.player.inventory.getItem(slot);
            if (stack != null && !this.neverSwapWith.matches(stack)) {
                return slot;
            }
        }
        return -1;
    }

    private enum Step {
        IDLE,
        PRESS_GUN_KEY,
        PRESS_SPARE_KEY,
        CLOSE
    }
}
