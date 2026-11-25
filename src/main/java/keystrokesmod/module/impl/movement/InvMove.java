package keystrokesmod.module.impl.movement;

import com.google.gson.JsonObject;
import java.util.concurrent.ConcurrentLinkedQueue;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.event.JumpEvent;
import keystrokesmod.event.PreMovementPacketEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.gui.screen.game.ChatScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Status;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import org.lwjgl.input.Keyboard;

public class InvMove extends Module {
    private static final int INVENTORY_MODE_LEGIT = 1;
    private static final int INVENTORY_MODE_BLINK = 2;
    private static final int INVENTORY_MODE_CLOSE = 3;
    private static final double LEGACY_SLOW_DELAY_MS = 250.0;
    private static final double TICK_DURATION_MS = 50.0;
    public SliderSetting inventory;
    private SliderSetting interactDelay;
    private ButtonSetting delayOpenPacket;
    private SliderSetting chestAndOthers;
    private SliderSetting motion;
    private ButtonSetting modifyMotionPost;
    private ButtonSetting slowWhenNecessary;
    private ButtonSetting allowJumping;
    private ButtonSetting allowSneak;
    private ButtonSetting allowMidair;
    private ButtonSetting allowSprinting;
    private ButtonSetting allowRotating;
    public int ticks;
    public boolean setMotion;
    private int movementReleaseTicks;
    private int legitCloseReleaseTicks;
    private boolean legitInventorySession;
    private boolean restoreMovementAfterLegitClose;
    private boolean restoreRestrictedInput;
    private boolean inventoryInputActive;
    private boolean inventorySneak;
    private ClientStatusC2SPacket delayedOpenPacket;
    private Packet legitClosePacket;
    private final ConcurrentLinkedQueue<Packet> blinkedPackets = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Packet> legitPackets = new ConcurrentLinkedQueue<>();
    private final String[] INVENTORY_MODES = new String[] {"Vanilla", "Legit", "Blink", "Close"};
    private final String[] CHEST_AND_OTHER_MODES = new String[] {"Vanilla", "Blink"};

    public InvMove() {
        super("InvMove", Module.category.movement);
        this.registerSetting(
                this.inventory = new SliderSetting("Inventory", true, 0, this.INVENTORY_MODES) {
                    @Override
                    public void loadProfile(JsonObject data) {
                        if (data.has(this.getProfileKey())
                                && data.get(this.getProfileKey()).isJsonPrimitive()) {
                            if (data.getAsJsonPrimitive(this.getProfileKey()).isString()
                                    && "Legit (Slow)"
                                            .equalsIgnoreCase(data.getAsJsonPrimitive(this.getProfileKey())
                                                    .getAsString())) {
                                this.setValue(1.0);
                                InvMove.this.interactDelay.setValue(250.0);
                                return;
                            }

                            if (data.getAsJsonPrimitive(this.getProfileKey()).isNumber()) {
                                int legacyMode = data.getAsJsonPrimitive(this.getProfileKey())
                                        .getAsInt();
                                if (legacyMode == 2) {
                                    this.setValue(1.0);
                                    InvMove.this.interactDelay.setValue(250.0);
                                    return;
                                }

                                if (legacyMode >= 3) {
                                    this.setValue(legacyMode - 1);
                                    return;
                                }
                            }
                        }

                        super.loadProfile(data);
                    }
                });
        this.registerSetting(this.interactDelay = new SliderSetting("Interact delay", "ms", 50.0, 50.0, 1000.0, 50.0));
        this.registerSetting(
                this.chestAndOthers = new SliderSetting("Chest & others", true, 0, this.CHEST_AND_OTHER_MODES));
        this.registerSetting(this.motion = new SliderSetting("Motion", "x", 1.0, 0.05, 1.0, 0.01));
        this.registerSetting(this.delayOpenPacket = new ButtonSetting("Delay open packet", false));
        this.registerSetting(this.modifyMotionPost = new ButtonSetting("Modify motion after click", false));
        this.registerSetting(this.slowWhenNecessary = new ButtonSetting("Slow motion when necessary", false));
        this.registerSetting(this.allowJumping = new ButtonSetting("Allow jumping", true));
        this.registerSetting(this.allowSneak = new ButtonSetting("Allow sneak", true));
        this.registerSetting(this.allowMidair = new ButtonSetting("Allow midair", true));
        this.registerSetting(this.allowRotating = new ButtonSetting("Allow rotating", true));
        this.registerSetting(this.allowSprinting = new ButtonSetting("Allow sprinting", true));
    }

    @Override
    public void guiUpdate() {
        this.interactDelay.setVisible(this.inventory.getInput() == 1.0, this);
        this.delayOpenPacket.setVisible(this.inventory.getInput() == 1.0, this);
    }

    @Override
    public String getInfo() {
        int mode = (int) this.inventory.getInput();
        return mode >= 0 && mode < this.INVENTORY_MODES.length ? this.INVENTORY_MODES[mode] : "Disabled";
    }

    @Override
    public void onDisable() {
        this.reset();
        this.releasePackets();
        this.releaseLegitPackets();
        this.releaseLegitClosePacket();
        this.releaseDelayedOpenPacket();
        if (this.restoreMovementAfterLegitClose && mc.screen == null) {
            this.restoreMovementKeys();
        }

        this.legitInventorySession = false;
        this.restoreMovementAfterLegitClose = false;
        this.inventoryInputActive = false;
        this.inventorySneak = false;
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.delayedOpenPacket != null && !this.shouldDelayOpenPacket()) {
            this.releaseDelayedOpenPacket();
        }

        if (this.isLegitInventoryMode() && mc.screen instanceof SurvivalInventoryScreen) {
            this.legitInventorySession = true;
        } else if (this.legitClosePacket == null && this.legitPackets.isEmpty()) {
            this.legitInventorySession = false;
        }

        if (this.legitClosePacket != null) {
            if (this.legitCloseReleaseTicks <= 0 && this.movementReleaseTicks <= 0) {
                this.releaseCloseActionKeys();
            } else {
                this.releaseMovementKeys();
            }
        }

        if (this.restoreMovementAfterLegitClose && mc.screen == null && this.movementReleaseTicks > 0) {
            this.ticks = 0;
            this.setMotion = false;
            this.releaseMovementKeys();
            this.movementReleaseTicks--;
        } else {
            if (this.restoreMovementAfterLegitClose && mc.screen == null && this.legitClosePacket == null) {
                this.restoreMovementAfterLegitClose = false;
                this.restoreMovementKeys();
            }

            if (!this.guiCheck()) {
                this.inventoryInputActive = false;
                this.inventorySneak = false;
                if (this.restoreRestrictedInput && mc.screen == null) {
                    this.restoreMovementKeys();
                    this.restoreRestrictedInput = false;
                }

                this.reset();
            } else {
                if (!(mc.screen instanceof ClickGui)) {
                    if (this.setMotion && ++this.ticks == 10) {
                        this.ticks = 0;
                        this.setMotion = false;
                    }

                    if (this.setMotion
                            && !this.isSneakRequested()
                            && (this.motion.getInput() != 1.0 || this.slowWhenNecessary.isToggled())) {
                        int speedAmplifier = Utils.getSpeedAmplifier();
                        double slowedMotion = 0.65;
                        switch (speedAmplifier) {
                            case 1:
                                slowedMotion = 0.615;
                                break;
                            case 2:
                                slowedMotion = 0.3;
                        }

                        Utils.setSpeed(Utils.getHorizontalSpeed()
                                * (this.slowWhenNecessary.isToggled() ? slowedMotion : this.motion.getInput()));
                    }
                } else {
                    this.reset();
                }

                boolean releaseMovement = this.shouldReleaseMovement();
                if (releaseMovement) {
                    this.releaseMovementKeys();
                    if (this.movementReleaseTicks > 0) {
                        this.movementReleaseTicks--;
                    }
                } else {
                    this.restoreMovementKeys();
                }

                boolean applyAllowSettings = this.shouldApplyAllowSettings();
                this.restoreRestrictedInput = applyAllowSettings
                        && (!this.allowSneak.isToggled() || !this.allowMidair.isToggled() && !mc.player.onGround);
                boolean sneakRequested = this.isSneakRequested();
                boolean sprintingAllowed = !applyAllowSettings || this.allowSprinting.isToggled();
                boolean foodLvlMet = mc.player.getHungerManager().getFoodLevel() > 6.0F || mc.player.abilities.canFly;
                if (!releaseMovement
                        && !sneakRequested
                        && (Utils.isBindDown(mc.options.sprintKey) || ModuleManager.sprint.isEnabled())
                        && mc.player.input.movementForward >= 0.8F
                        && foodLvlMet
                        && !mc.player.isSprinting()
                        && sprintingAllowed) {
                    mc.player.setSprinting(true);
                }

                if (releaseMovement || sneakRequested || !sprintingAllowed) {
                    mc.player.setSprinting(false);
                }

                if (!applyAllowSettings || this.allowRotating.isToggled()) {
                    if (Keyboard.isKeyDown(208) && mc.player.pitch < 90.0F) {
                        mc.player.pitch += 6.0F;
                    }

                    if (Keyboard.isKeyDown(200) && mc.player.pitch > -90.0F) {
                        mc.player.pitch -= 6.0F;
                    }

                    if (Keyboard.isKeyDown(205)) {
                        mc.player.yaw += 6.0F;
                    }

                    if (Keyboard.isKeyDown(203)) {
                        mc.player.yaw -= 6.0F;
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onJump(JumpEvent e) {
        if (e.entity == mc.player
                && this.shouldApplyAllowSettings()
                && !this.allowJumping.isToggled()
                && mc.screen != null
                && !(mc.screen instanceof ClickGui)) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!this.guiCheck()) {
            this.inventoryInputActive = false;
            this.inventorySneak = false;
        } else {
            boolean applyAllowSettings = this.shouldApplyAllowSettings();
            boolean sneak = (!applyAllowSettings || this.allowSneak.isToggled()) && e.isSneak();
            if (applyAllowSettings && !this.allowMidair.isToggled() && !mc.player.onGround) {
                e.setForward(0.0F);
                e.setStrafe(0.0F);
                e.setJump(false);
                sneak = false;
            }

            e.setSneak(sneak);
            if (sneak) {
                e.setSneakSlowDownMultiplier(0.3);
                mc.player.setSprinting(false);
            }

            if (this.inventoryInputActive && sneak != this.inventorySneak) {
                e.setForward(0.0F);
                e.setStrafe(0.0F);
                e.setJump(false);
            }

            this.inventoryInputActive = true;
            this.inventorySneak = sneak;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMovementPacket(PreMovementPacketEvent e) {
        this.releaseLegitPackets();
        boolean releasedClose = false;
        if (this.legitClosePacket != null) {
            if (this.legitCloseReleaseTicks > 0) {
                this.legitCloseReleaseTicks--;
            } else {
                releasedClose = this.releaseLegitClosePacket();
            }
        }

        if (releasedClose && this.restoreMovementAfterLegitClose && this.movementReleaseTicks <= 0) {
            this.restoreMovementAfterLegitClose = false;
            if (mc.screen == null) {
                this.restoreMovementKeys();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof ClientStatusC2SPacket) {
            ClientStatusC2SPacket packet = (ClientStatusC2SPacket) e.getPacket();
            if (this.shouldDelayOpenPacket() && packet.getStatus() == Status.OPEN_INVENTORY_ACHIEVEMENT) {
                this.delayedOpenPacket = packet;
                e.setCanceled(true);
            }
        } else if (e.getPacket() instanceof InventoryMenuClickSlotC2SPacket) {
            boolean inventoryManagerClick =
                    ModuleManager.inventory != null && ModuleManager.inventory.isSendingInventoryClick();
            if (this.isLegitInventoryMode() && mc.screen instanceof SurvivalInventoryScreen) {
                this.legitInventorySession = true;
                this.movementReleaseTicks = Math.max(this.movementReleaseTicks, this.getMovementReleaseTicks());
                this.releaseMovementKeys();
                this.legitPackets.add(e.getPacket());
                e.setCanceled(true);
                this.releaseDelayedOpenPacket();
            }

            if (this.modifyMotionPost.isToggled() || this.slowWhenNecessary.isToggled() && !this.canBlink()) {
                this.setMotion = true;
                this.ticks = 0;
            }

            if (!inventoryManagerClick && this.canBlink()) {
                this.blinkedPackets.add(e.getPacket());
                e.setCanceled(true);
            }
        } else if (e.getPacket() instanceof CloseInventoryMenuC2SPacket) {
            if (this.delayedOpenPacket != null
                    && this.shouldDelayOpenPacket()
                    && mc.screen instanceof SurvivalInventoryScreen) {
                this.delayedOpenPacket = null;
                this.legitInventorySession = false;
                e.setCanceled(true);
                return;
            }

            if (this.delayedOpenPacket != null) {
                this.releaseDelayedOpenPacket();
            }

            boolean pendingLegitClick = !this.legitPackets.isEmpty();
            boolean legitInventoryClose = this.isLegitInventoryMode()
                    && (mc.screen instanceof SurvivalInventoryScreen || this.legitInventorySession);
            if (!legitInventoryClose && !pendingLegitClick && this.legitClosePacket == null) {
                this.legitInventorySession = false;
                if (this.canBlink()) {
                    if (this.inventory.getInput() == 3.0) {
                        PacketUtils.sendPacketNoEvent(new ClientStatusC2SPacket(Status.OPEN_INVENTORY_ACHIEVEMENT));
                    }

                    this.releasePackets();
                }
            } else {
                boolean activeMovementRelease = this.movementReleaseTicks > 0;
                if (!pendingLegitClick && !activeMovementRelease) {
                    this.releaseCloseActionKeys();
                } else {
                    if (pendingLegitClick) {
                        this.movementReleaseTicks = Math.max(this.movementReleaseTicks, this.getMovementReleaseTicks());
                    }

                    this.releaseMovementKeys();
                }

                this.restoreMovementAfterLegitClose = true;
                if (this.legitClosePacket == null) {
                    this.legitClosePacket = e.getPacket();
                    this.legitCloseReleaseTicks = pendingLegitClick ? 1 : 0;
                }

                e.setCanceled(true);
            }
        }
    }

    private void reset() {
        this.ticks = 0;
        this.setMotion = false;
        this.movementReleaseTicks = 0;
    }

    private boolean guiCheck() {
        if (mc.screen == null) {
            return false;
        }

        if (Settings.inInventory()) {
            if (this.inventory.getInput() == -1.0) {
                return false;
            }
        } else if (this.chestAndOthers.getInput() == -1.0 && !(mc.screen instanceof ClickGui)
                || mc.screen instanceof ChatScreen) {
            return false;
        }

        return true;
    }

    private boolean canBlink() {
        if (mc.screen == null && this.inventory.getInput() != 3.0) {
            return false;
        } else {
            return (!(mc.screen instanceof SurvivalInventoryScreen) || this.inventory.getInput() != 2.0)
                            && (this.inventory.getInput() != 3.0 || mc.screen != null)
                    ? this.chestAndOthers.getInput() == 1.0
                            && !(mc.screen instanceof ClickGui)
                            && !(mc.screen instanceof ChatScreen)
                    : true;
        }
    }

    private boolean isLegitInventoryMode() {
        return this.inventory.getInput() == 1.0;
    }

    private boolean shouldDelayOpenPacket() {
        return this.isLegitInventoryMode() && this.delayOpenPacket.isToggled();
    }

    private boolean shouldApplyAllowSettings() {
        return !(mc.screen instanceof SurvivalInventoryScreen)
                || !this.shouldDelayOpenPacket()
                || this.delayedOpenPacket == null;
    }

    private int getMovementReleaseTicks() {
        return (int) (this.interactDelay.getInput() / 50.0);
    }

    public boolean isCloseInventoryMode() {
        return this.inventory.getInput() == 3.0;
    }

    private boolean shouldReleaseMovement() {
        return this.shouldApplyAllowSettings() && !this.allowMidair.isToggled() && !mc.player.onGround
                || mc.screen instanceof SurvivalInventoryScreen
                        && this.isLegitInventoryMode()
                        && (this.movementReleaseTicks > 0 || mc.player.inventory.getCursorItem() != null);
    }

    private boolean isSneakRequested() {
        return (!this.shouldApplyAllowSettings() || this.allowSneak.isToggled())
                && (Utils.isBindDown(mc.options.sneakKey) || mc.player.input.sneaking);
    }

    private void restoreMovementKeys() {
        KeyBinding.set(mc.options.forwardKey.getKeyCode(), Utils.isBindDown(mc.options.forwardKey));
        KeyBinding.set(mc.options.backKey.getKeyCode(), Utils.isBindDown(mc.options.backKey));
        KeyBinding.set(mc.options.rightKey.getKeyCode(), Utils.isBindDown(mc.options.rightKey));
        KeyBinding.set(mc.options.leftKey.getKeyCode(), Utils.isBindDown(mc.options.leftKey));
        KeyBinding.set(mc.options.jumpKey.getKeyCode(), Utils.jumpDown());
        boolean applyAllowSettings = this.shouldApplyAllowSettings();
        boolean sneak = (mc.screen == null || !applyAllowSettings || this.allowSneak.isToggled())
                && Utils.isBindDown(mc.options.sneakKey);
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), sneak);
        boolean sprint = (mc.screen == null || !applyAllowSettings || this.allowSprinting.isToggled() && !sneak)
                && Utils.isBindDown(mc.options.sprintKey);
        KeyBinding.set(mc.options.sprintKey.getKeyCode(), sprint);
    }

    private void releaseMovementKeys() {
        KeyBinding.set(mc.options.forwardKey.getKeyCode(), false);
        KeyBinding.set(mc.options.backKey.getKeyCode(), false);
        KeyBinding.set(mc.options.rightKey.getKeyCode(), false);
        KeyBinding.set(mc.options.leftKey.getKeyCode(), false);
        KeyBinding.set(mc.options.jumpKey.getKeyCode(), false);
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), false);
        KeyBinding.set(mc.options.sprintKey.getKeyCode(), false);
        if (mc.player != null && mc.player.input != null) {
            mc.player.input.movementForward = 0.0F;
            mc.player.input.movementSideways = 0.0F;
            mc.player.input.jumping = false;
            mc.player.input.sneaking = false;
            mc.player.setSprinting(false);
        }
    }

    private void releaseCloseActionKeys() {
        KeyBinding.set(mc.options.forwardKey.getKeyCode(), Utils.isBindDown(mc.options.forwardKey));
        KeyBinding.set(mc.options.backKey.getKeyCode(), Utils.isBindDown(mc.options.backKey));
        KeyBinding.set(mc.options.rightKey.getKeyCode(), Utils.isBindDown(mc.options.rightKey));
        KeyBinding.set(mc.options.leftKey.getKeyCode(), Utils.isBindDown(mc.options.leftKey));
        KeyBinding.set(mc.options.jumpKey.getKeyCode(), Utils.jumpDown());
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), false);
        KeyBinding.set(mc.options.sprintKey.getKeyCode(), false);
        if (mc.player != null && mc.player.input != null) {
            mc.player.input.sneaking = false;
            mc.player.setSprinting(false);
        }
    }

    private void releasePackets() {
        synchronized (this.blinkedPackets) {
            for (Packet packet : this.blinkedPackets) {
                PacketUtils.sendPacketNoEvent(packet);
            }
        }

        this.blinkedPackets.clear();
    }

    private void releaseLegitPackets() {
        Packet packet;
        while ((packet = this.legitPackets.poll()) != null) {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    private void releaseDelayedOpenPacket() {
        ClientStatusC2SPacket packet = this.delayedOpenPacket;
        if (packet != null) {
            this.delayedOpenPacket = null;
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    private boolean releaseLegitClosePacket() {
        Packet packet = this.legitClosePacket;
        if (packet == null) {
            return false;
        }

        this.legitClosePacket = null;
        this.legitCloseReleaseTicks = 0;
        this.legitInventorySession = false;
        PacketUtils.sendPacketNoEvent(packet);
        return true;
    }
}
