package keystrokesmod.module.impl.zombies;

import java.util.Arrays;
import keystrokesmod.event.KeyEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.ClientTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.RenderTickEvent;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.zombies.ZombiesHotbar;
import net.minecraft.client.options.KeyBinding;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class QuickSwitch extends Module {
    /** Each profile registers its own settings, hidden unless it's the active one. */
    private static final int MAX_PRESETS = 5;

    /** Must have MAX_PRESETS entries: the Profile slider's range comes from this array's length. */
    private static final String[] PROFILE_NAMES = {"P1", "P2", "P3", "P4", "P5"};

    private static final long KEY_PRESS_DURATION_NS = 50000000L;
    private static final long MIN_INTERVAL_NS = 5_000_000L;
    /**
     * The active profile; the cycle key steps through all of them. Kept as the first slider so HUDs that show a
     * module's first slider as its info (e.g. prettyGUI) display the profile.
     */
    public static SliderSetting activeProfile = new SliderSetting("Profile", 0, PROFILE_NAMES);

    /** Pauses switching while looking at a button, lever or chest, so the right-click uses it. */
    public static ButtonSetting disableOnInteract = new ButtonSetting("Disable on interact", true, "ButtonPause");

    public static ButtonSetting reverse = new ButtonSetting("Reverse", false);
    public static SliderSetting[] presetDelay = new SliderSetting[MAX_PRESETS];
    public static ButtonSetting[] presetS2 = new ButtonSetting[MAX_PRESETS];
    public static ButtonSetting[] presetS3 = new ButtonSetting[MAX_PRESETS];
    public static ButtonSetting[] presetS4 = new ButtonSetting[MAX_PRESETS];
    public static KeySetting cyclePresetKey;
    /** Hotbar slots holding one of these are skipped. Defaults to light gray dye, Zombies' empty weapon slot. */
    private final ItemListSetting ignoredItems = new ItemListSetting("Ignored items");

    private boolean cycleKeyWasDown = false;
    private boolean holdCtrl = false;
    private volatile long intervalNs;
    private volatile double lastDelayValue = -1.0;
    private KeyBinding currentPressedKey;
    private int pressCounter = 0;
    private boolean releaseNextTick = false;
    private boolean keyJustPressed = false;
    private volatile int[] validSlots = new int[3];
    private volatile int validSlotCount = 0;
    private static final long MANUAL_GRACE_NS = 0L;
    private volatile long manualOverrideUntilNs = 0L;
    private boolean manualWasActive = false;
    private int lastHotbarMask = 0;
    private long lastTickNs = 0L;
    private long accNs = 0L;
    /** Cached HUD info, so the HUD only re-sorts when the text actually changes. */
    private String cachedInfo = "";

    public QuickSwitch() {
        super("Quick Switch", Module.category.zombies);
        this.registerSetting(activeProfile);
        this.registerSetting(cyclePresetKey = new KeySetting("Cycle profile", Keyboard.KEY_P));
        this.registerSetting(disableOnInteract);
        this.registerSetting(reverse);
        this.ignoredItems.addItem("minecraft:dye:7");
        this.registerSetting(this.ignoredItems);

        for (int i = 0; i < MAX_PRESETS; i++) {
            int idx = i + 1;
            presetDelay[i] = new SliderSetting("Delay " + idx, 50.0, 50.0, 80.0, 1.0);
            presetS2[i] = new ButtonSetting("Slot2 " + idx, true);
            presetS3[i] = new ButtonSetting("Slot3 " + idx, true);
            presetS4[i] = new ButtonSetting("Slot4 " + idx, true);
            this.registerSetting(presetDelay[i]);
            this.registerSetting(presetS2[i]);
            this.registerSetting(presetS3[i]);
            this.registerSetting(presetS4[i]);
        }

        // ClickGui doesn't exist yet, so set visibility directly instead of via setVisible().
        for (int i = 1; i < MAX_PRESETS; i++) {
            presetDelay[i].visible = false;
            presetS2[i].visible = false;
            presetS3[i].visible = false;
            presetS4[i].visible = false;
        }
    }

    /** Index (0-based) of the active profile. */
    private int currentPreset() {
        return Math.max(0, Math.min(MAX_PRESETS - 1, (int) Math.round(activeProfile.getInput())));
    }

    /** Shows only the active profile's settings. */
    private void syncProfileSettings() {
        int current = this.currentPreset();
        for (int i = 0; i < MAX_PRESETS; i++) {
            boolean visible = i == current;
            presetDelay[i].setVisible(visible, this);
            presetS2[i].setVisible(visible, this);
            presetS3[i].setVisible(visible, this);
            presetS4[i].setVisible(visible, this);
        }
    }

    @Override
    public void guiUpdate() {
        this.syncProfileSettings();
    }

    @Override
    public String getInfo() {
        // Active slots plus the switch delay, e.g. "234 50ms".
        int i = this.currentPreset();
        StringBuilder sb = new StringBuilder();
        if (presetS2[i].isToggled()) sb.append('2');
        if (presetS3[i].isToggled()) sb.append('3');
        if (presetS4[i].isToggled()) sb.append('4');
        if (sb.length() == 0) sb.append('-');
        sb.append(' ').append((int) presetDelay[i].getInput()).append("ms");
        String info = sb.toString();
        // HUD compares info by reference: hand back the same String while the text is unchanged.
        if (!info.equals(this.cachedInfo)) {
            this.cachedInfo = info;
        }
        return this.cachedInfo;
    }

    @Override
    public void onEnable() {
        this.intervalNs = Math.max(msToNanos(presetDelay[this.currentPreset()].getInput()), MIN_INTERVAL_NS);
        this.lastDelayValue = presetDelay[this.currentPreset()].getInput();
        this.manualOverrideUntilNs = 0L;
        this.manualWasActive = false;
        this.lastHotbarMask = 0;
        this.keyJustPressed = false;
        this.lastTickNs = 0L;
        this.accNs = 0L;
        this.releaseNextTick = false;
        if (this.isReady()) {
            this.updateValidSlots();
        } else {
            this.validSlotCount = 0;
        }
    }

    @Override
    public void onDisable() {
        this.releaseCurrentKey();
        this.manualOverrideUntilNs = 0L;
        this.manualWasActive = false;
        this.lastHotbarMask = 0;
        this.keyJustPressed = false;
        this.lastTickNs = 0L;
        this.accNs = 0L;
        this.releaseNextTick = false;
    }

    private static long msToNanos(double ms) {
        return (long) (ms * 1000000.0);
    }

    private static boolean isKeyboardKeyDown(int code) {
        if (code > 0 && code <= 255) {
            try {
                return Keyboard.isKeyDown(code);
            } catch (IndexOutOfBoundsException ignored) {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean isReady() {
        return Utils.nullCheck();
    }

    private boolean shouldPause() {
        return ZombiesHotbar.isLookingAtNearbyArmorStand()
                || (disableOnInteract.isToggled() && ZombiesHotbar.isLookingAtInteractable())
                || ZombiesHotbar.isHoldingKnife();
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        if (event.phase == Phase.START) {
            boolean cycleKeyDown = mc.screen == null && cyclePresetKey.isPressed();
            boolean cyclePressed = cycleKeyDown && !this.cycleKeyWasDown;
            this.cycleKeyWasDown = cycleKeyDown;
            if (cyclePressed) {
                activeProfile.setValue((this.currentPreset() + 1) % MAX_PRESETS);
                this.syncProfileSettings();
                this.refreshInterval(true);
                this.announceProfile();
            }

            double v = presetDelay[this.currentPreset()].getInput();
            if (Double.doubleToLongBits(v) != Double.doubleToLongBits(this.lastDelayValue)) {
                this.lastDelayValue = v;
                this.refreshInterval(false);
                this.updateValidSlots();
            }

            this.updateManualOverride();
            this.handleKeybindAdjustments();
            this.updateValidSlots();
            if (this.releaseNextTick) {
                this.releaseCurrentKey();
                this.releaseNextTick = false;
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(RenderTickEvent event) {
        if (!this.isReady()) {
            this.lastTickNs = 0L;
            this.accNs = 0L;
        } else if (event.phase == Phase.START && mc.screen == null) {
            if (!this.shouldPause()) {
                long now = System.nanoTime();
                if (this.lastTickNs == 0L) {
                    this.lastTickNs = now;
                } else {
                    long dt = now - this.lastTickNs;
                    this.lastTickNs = now;
                    if (now >= this.manualOverrideUntilNs) {
                        if (Mouse.isButtonDown(1)) {
                            if (this.keyJustPressed) {
                                this.keyJustPressed = false;
                            } else {
                                this.accNs += dt;
                                if (this.accNs >= this.intervalNs) {
                                    this.accNs = this.accNs - this.intervalNs;
                                    this.doSwitch();
                                    this.releaseNextTick = true;
                                }
                            }
                        }
                    }
                }
            } else {
                this.lastTickNs = System.nanoTime();
            }
        }
    }

    @SubscribeEvent
    public void onKeyInput(KeyEvent e) {
        if (!this.isReady() || mc.screen != null || !Keyboard.getEventKeyState()) {
            return;
        }

        int k = Keyboard.getEventKey();
        KeyBinding[] hotbar = mc.options.hotbarKeyBindings;
        for (int i = 0; i < hotbar.length; i++) {
            if (hotbar[i].getKeyCode() != k) {
                continue;
            }

            int slot = i + 1;
            boolean wasInSlot1 = ZombiesHotbar.isHoldingKnife();
            boolean validWeapon = slot != 1 && this.isSlotValid(slot);
            this.keyJustPressed = true;
            if (validWeapon && !wasInSlot1) {
                this.accNs = 0L;
            }

            this.forceSelectSlot(slot);
            if (validWeapon) {
                this.manualOverrideUntilNs = 0L;
                this.manualWasActive = false;
            } else {
                this.manualOverrideUntilNs = System.nanoTime() + MANUAL_GRACE_NS;
                this.manualWasActive = true;
            }

            this.releaseCurrentKey();
            break;
        }
    }

    private void announceProfile() {
        int i = this.currentPreset();
        StringBuilder slots = new StringBuilder();
        if (presetS2[i].isToggled()) slots.append(" 2");
        if (presetS3[i].isToggled()) slots.append(" 3");
        if (presetS4[i].isToggled()) slots.append(" 4");
        Utils.sendMessage("&7Quick Switch: &bProfile " + (i + 1) + "&7/" + MAX_PRESETS + " &8| &7"
                + (int) presetDelay[i].getInput() + "ms &8| &7slots"
                + (slots.length() == 0 ? " none" : slots.toString()));
    }

    private void refreshInterval(boolean snapToNow) {
        long newInterval = Math.max(msToNanos(presetDelay[this.currentPreset()].getInput()), MIN_INTERVAL_NS);
        this.intervalNs = newInterval;
        if (snapToNow) {
            this.accNs = 0L;
            this.lastTickNs = 0L;
        }
    }

    private void handleKeybindAdjustments() {
        boolean ctrlDown = Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
        if (!ctrlDown) {
            this.holdCtrl = false;
        } else {
            if (!this.holdCtrl) {
                double value = presetDelay[this.currentPreset()].getInput();
                boolean updated = false;
                if (Keyboard.isKeyDown(2)) {
                    presetDelay[this.currentPreset()].setValue(
                            Math.min(presetDelay[this.currentPreset()].getMax(), value + 1.0));
                    updated = true;
                }

                if (Keyboard.isKeyDown(199)) {
                    presetDelay[this.currentPreset()].setValue(
                            Math.max(presetDelay[this.currentPreset()].getMin(), value - 1.0));
                    updated = true;
                }

                for (int i = 0; i < mc.options.hotbarKeyBindings.length; i++) {
                    if (isKeyboardKeyDown(mc.options.hotbarKeyBindings[i].getKeyCode())) {
                        switch (i + 1) {
                            case 2:
                                presetS2[this.currentPreset()].toggle();
                                updated = true;
                                break;
                            case 3:
                                presetS3[this.currentPreset()].toggle();
                                updated = true;
                                break;
                            case 4:
                                presetS4[this.currentPreset()].toggle();
                                updated = true;
                        }
                    }
                }

                if (updated) {
                    this.lastDelayValue = presetDelay[this.currentPreset()].getInput();
                    this.refreshInterval(false);
                    this.updateValidSlots();
                }
            }

            this.holdCtrl = true;
        }
    }

    private void updateManualOverride() {
        if (mc.player != null) {
            if (mc.screen == null) {
                long now = System.nanoTime();
                int mask = this.hotbarDownMask();
                if (mask != 0) {
                    int slot = Integer.numberOfTrailingZeros(mask) + 1;
                    if (slot != 1 && this.isSlotValid(slot)) {
                        this.manualOverrideUntilNs = now + MANUAL_GRACE_NS;
                        this.releaseCurrentKey();
                    }
                }

                if (this.manualWasActive && now >= this.manualOverrideUntilNs && mask == 0) {
                    this.manualOverrideUntilNs = 0L;
                    this.manualWasActive = false;
                }

                this.lastHotbarMask = mask;
            }
        }
    }

    private int hotbarDownMask() {
        KeyBinding[] hotbar = mc.options.hotbarKeyBindings;
        int m = 0;

        for (int i = 0; i < hotbar.length; i++) {
            int code = hotbar[i].getKeyCode();
            if (isKeyboardKeyDown(code)) {
                m |= 1 << i;
            }
        }

        return m;
    }

    private void forceSelectSlot(int slot1to9) {
        if (this.isReady()) {
            if (slot1to9 >= 1 && slot1to9 <= 9) {
                KeyBinding key = mc.options.hotbarKeyBindings[slot1to9 - 1];
                int code = key.getKeyCode();
                if (code != 0) {
                    this.releaseCurrentKey();
                    KeyBinding.set(code, true);
                    KeyBinding.click(code);
                    this.currentPressedKey = key;
                    this.pressCounter++;
                }
            }
        }
    }

    private void doSwitch() {
        if (this.isReady()) {
            int currentSlot = mc.player.inventory.selectedSlot + 1;
            int count = this.validSlotCount;
            if (count != 0) {
                boolean currentValid = false;
                int indexInValid = -1;

                for (int i = 0; i < count; i++) {
                    if (this.validSlots[i] == currentSlot) {
                        currentValid = true;
                        indexInValid = i;
                        break;
                    }
                }

                int nextSlot;
                if (!currentValid) {
                    if (currentSlot == 1) {
                        return;
                    }

                    nextSlot = this.validSlots[0];
                } else {
                    nextSlot = this.validSlots[(indexInValid + 1) % count];
                }

                if (nextSlot != currentSlot) {
                    KeyBinding key = mc.options.hotbarKeyBindings[nextSlot - 1];
                    int code = key.getKeyCode();
                    if (code != 0) {
                        this.releaseCurrentKey();
                        KeyBinding.set(code, true);
                        KeyBinding.click(code);
                        this.currentPressedKey = key;
                    }
                }
            }
        }
    }

    private boolean isSlotValid(int slot1to9) {
        return ZombiesHotbar.isWeaponSlot(slot1to9 - 1, this.ignoredItems);
    }

    private void updateValidSlots() {
        if (!this.isReady()) {
            this.validSlotCount = 0;
        } else {
            int count = 0;
            int[] order = reverse.isToggled() ? new int[] {4, 3, 2} : new int[] {2, 3, 4};

            for (int slot : order) {
                ButtonSetting slotSetting;
                switch (slot) {
                    case 2:
                        slotSetting = presetS2[this.currentPreset()];
                        break;
                    case 3:
                        slotSetting = presetS3[this.currentPreset()];
                        break;
                    case 4:
                        slotSetting = presetS4[this.currentPreset()];
                        break;
                    default:
                        continue;
                }

                if (slotSetting.isToggled() && this.isSlotValid(slot)) {
                    if (this.validSlots.length <= count) {
                        this.validSlots = Arrays.copyOf(this.validSlots, count + 1);
                    }

                    this.validSlots[count++] = slot;
                }
            }

            this.validSlotCount = count;
        }
    }

    public static double getDelay() {
        return presetDelay[ModuleManager.quickSwitch.currentPreset()].getInput();
    }

    public static boolean getS2() {
        return presetS2[ModuleManager.quickSwitch.currentPreset()].isToggled();
    }

    public static boolean getS3() {
        return presetS3[ModuleManager.quickSwitch.currentPreset()].isToggled();
    }

    public static boolean getS4() {
        return presetS4[ModuleManager.quickSwitch.currentPreset()].isToggled();
    }

    private void releaseCurrentKey() {
        if (this.currentPressedKey != null) {
            int code = this.currentPressedKey.getKeyCode();
            // No isEnabled() check: onDisable runs after the module is marked disabled and must still release.
            if (code != 0) {
                KeyBinding.set(code, false);
            }

            this.currentPressedKey = null;
        }
    }
}
