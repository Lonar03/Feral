package keystrokesmod.module.impl.movement;

import java.util.Iterator;
import keystrokesmod.event.CollisionEvent;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.s2c.play.AddEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class LongJump extends Module {
    private static final int LAUNCH_MODE = 2;
    private SliderSetting mode;
    private SliderSetting boostSetting;
    private SliderSetting verticalMotion;
    private SliderSetting motionDecay;
    private SliderSetting launchVerticalTicks;
    private SliderSetting launchHorizontalTicks;
    private ButtonSetting manual;
    private ButtonSetting onlyWithVelocity;
    private KeySetting disableKey;
    private ButtonSetting allowStrafe;
    private ButtonSetting invertYaw;
    private ButtonSetting stopMovement;
    private ButtonSetting hideExplosion;
    public ButtonSetting spoofItem;
    private KeySetting temporaryFlightKey;
    public String[] modes = new String[] {"Float", "Boost", "Launch"};
    private boolean manualWasOn;
    private float yaw;
    private float pitch;
    private boolean notMoving;
    private boolean enabled;
    public static boolean function;
    private Double startY;
    private int boostTicks;
    public int lastSlot = -1;
    private int stopTime;
    private int rotateTick;
    private int motionDecayVal;
    private long fireballTime;
    private long MAX_EXPLOSION_DIST_SQ = 9L;
    private long FIREBALL_TIMEOUT = 750L;
    public static boolean stopVelocity;
    public static boolean stopModules;
    public static boolean slotReset;
    public static int slotResetTicks;
    private int firstSlot = -1;
    private boolean launchActive;
    private boolean launchAimed;
    private boolean launchItemSpoofed;
    private boolean launchBoostStarted;
    private int launchVerticalRemaining;
    private int launchHorizontalRemaining;
    private double launchPreviousY;

    public LongJump() {
        super("Long Jump", Module.category.movement);
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, this.modes));
        this.registerSetting(this.boostSetting = new SliderSetting("Horizontal boost", 1.7, 0.0, 2.0, 0.05));
        this.registerSetting(this.verticalMotion = new SliderSetting("Vertical motion", 0.0, 0.4, 0.9, 0.01));
        this.registerSetting(this.motionDecay = new SliderSetting("Motion decay", 17.0, 1.0, 40.0, 1.0));
        this.registerSetting(
                this.launchVerticalTicks = new SliderSetting("Activation time (v)", " tick", 45.0, 1.0, 60.0, 1.0));
        this.registerSetting(
                this.launchHorizontalTicks = new SliderSetting("Activation time (h)", " tick", 2.0, 1.0, 50.0, 1.0));
        this.registerSetting(this.manual = new ButtonSetting("Manual", false));
        this.registerSetting(this.onlyWithVelocity = new ButtonSetting("Only while velocity enabled", false));
        this.registerSetting(this.allowStrafe = new ButtonSetting("Allow strafe", false));
        this.registerSetting(this.invertYaw = new ButtonSetting("Invert yaw", true));
        this.registerSetting(this.stopMovement = new ButtonSetting("Stop movement", false));
        this.registerSetting(this.hideExplosion = new ButtonSetting("Hide explosion", false));
        this.registerSetting(this.spoofItem = new ButtonSetting("Spoof item", false));
        this.registerSetting(this.disableKey = new KeySetting("Disable key", 57));
        this.registerSetting(this.temporaryFlightKey = new KeySetting("Vertical key", 57));
    }

    @Override
    public String getInfo() {
        return this.modes[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean launch = this.isLaunchMode();
        this.onlyWithVelocity.setVisible(this.manual.isToggled(), this);
        this.disableKey.setVisible(!launch && this.manual.isToggled(), this);
        this.boostSetting.setVisible(!launch, this);
        this.allowStrafe.setVisible(!launch, this);
        this.invertYaw.setVisible(!launch, this);
        this.launchVerticalTicks.setVisible(launch, this);
        this.launchHorizontalTicks.setVisible(launch, this);
        this.verticalMotion.setVisible(this.mode.getInput() == 0.0, this);
        this.motionDecay.setVisible(this.mode.getInput() == 0.0, this);
        this.temporaryFlightKey.setVisible(this.mode.getInput() == 0.0, this);
    }

    @Override
    public void onEnable() {
        if (this.isLaunchMode()) {
            if (!this.manual.isToggled()) {
                this.beginLaunch();
            }
        } else {
            if (!this.manual.isToggled()) {
                if (Utils.getTotalHealth(mc.player) <= 3.0F) {
                    Utils.sendMessage("&cPrevented throwing fireball due to low health");
                    this.disable();
                    return;
                }

                this.enabled();
            }
        }
    }

    @Override
    public void onDisable() {
        if (!this.launchActive && (!this.isLaunchMode() || function)) {
            this.disabled();
        } else {
            this.resetLaunch();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onCollision(CollisionEvent event) {
        if (!this.isLaunchMode() && this.startY != null && !this.manual.isToggled() && this.boostTicks <= 0) {
            BlockPos blockPos = event.blockPos;
            if (event.block.canWalkThrough(mc.world, blockPos) && blockPos.getY() <= this.startY) {
                event.boundingBox = new Box(
                        blockPos.getX() - 2.0,
                        blockPos.getY() - 1.0,
                        blockPos.getZ() - 2.0,
                        blockPos.getX() + 2.0,
                        blockPos.getY() + 1.0,
                        blockPos.getZ() + 2.0);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.launchActive && !this.isLaunchMode()) {
            this.finishLaunch();
        } else if (this.isLaunchMode()) {
            if (function && !this.launchActive) {
                this.disabled();
            } else {
                if (this.launchActive) {
                    this.updateLaunch();
                }
            }
        } else {
            if (this.manual.isToggled()) {
                this.manualWasOn = true;
            } else {
                if (this.manualWasOn) {
                    this.disabled();
                }

                this.manualWasOn = false;
            }

            if (this.manual.isToggled() && this.disableKey.isPressed() && Utils.jumpDown()) {
                function = false;
                this.disabled();
            }

            if (!function) {
                if (this.manual.isToggled() && !this.enabled && ModuleUtils.threwFireballLow) {
                    ModuleManager.velocity.disable = true;
                    ModuleManager.antiKnockback.disable = true;
                    this.enabled();
                }
            } else {
                if (this.spoofItem.isToggled()) {
                    ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelUpdate(true);
                    ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelReset(true);
                }

                if (this.enabled) {
                    if (!Utils.isMoving()) {
                        this.notMoving = true;
                    }

                    if (this.boostSetting.getInput() == 0.0 && this.verticalMotion.getInput() == 0.0) {
                        Utils.sendMessage("&cValues are set to 0!");
                        this.disabled();
                        return;
                    }

                    int fireballSlot = this.setupFireballSlot(true);
                    if (fireballSlot != -1) {
                        if (!this.manual.isToggled()) {
                            this.lastSlot = mc.player.inventory.selectedSlot;
                            if (mc.player.inventory.selectedSlot != fireballSlot) {
                                mc.player.inventory.selectedSlot = fireballSlot;
                            }
                        }

                        this.rotateTick = 1;
                        if (this.stopMovement.isToggled()) {
                            this.stopTime = 1;
                        }
                    }

                    this.enabled = false;
                }

                if (this.notMoving) {
                    this.motionDecayVal = 21;
                } else {
                    this.motionDecayVal = (int) this.motionDecay.getInput();
                }

                if (this.stopTime == -1
                        && ++this.boostTicks > (!this.temporaryFlightKey() ? 33 : (!this.notMoving ? 32 : 33))) {
                    this.disabled();
                } else if (this.fireballTime > 0L
                        && System.currentTimeMillis() - this.fireballTime > this.FIREBALL_TIMEOUT) {
                    Utils.sendMessage("&cFireball timed out.");
                    this.disabled();
                } else {
                    if (this.boostTicks > 0) {
                        if (this.mode.getInput() == 0.0) {
                            this.modifyVertical();
                        }

                        if (this.allowStrafe.isToggled() && this.boostTicks < 32) {
                            Utils.setSpeed(Utils.getHorizontalSpeed(mc.player));
                        }
                    }

                    if (this.stopMovement.isToggled() && !this.notMoving && this.stopTime > 0) {
                        this.stopTime++;
                    }

                    if (mc.player.onGround && this.boostTicks > 2) {
                        this.disabled();
                    }

                    if (this.firstSlot != -1) {
                        mc.player.inventory.selectedSlot = this.firstSlot;
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        if (Utils.nullCheck()) {
            if (!this.isLaunchMode() && !this.launchActive) {
                if (this.rotateTick == 1) {
                    if ((this.invertYaw.isToggled() || this.stopMovement.isToggled()) && !this.notMoving) {
                        if (!this.stopMovement.isToggled()) {
                            this.yaw = mc.player.yaw - 180.0F;
                            this.pitch = 90.0F;
                        } else {
                            this.yaw = mc.player.yaw - 180.0F;
                            this.pitch = 66.3F;
                        }
                    } else {
                        this.yaw = mc.player.yaw;
                        this.pitch = 90.0F;
                    }

                    e.setRotations(this.yaw, this.pitch);
                }

                if (this.rotateTick > 0 && ++this.rotateTick >= 3) {
                    this.rotateTick = 0;
                    int fireballSlot = this.setupFireballSlot(false);
                    if (fireballSlot != -1) {
                        this.fireballTime = System.currentTimeMillis();
                        if (!this.manual.isToggled()) {
                            mc.getNetworkHandler().sendPacket(new PlayerUseC2SPacket(mc.player.getDisplayItemInHand()));
                        }

                        mc.player.swingArm();
                        mc.getItemInHandRenderer().onBlockUsed();
                        stopVelocity = true;
                    }
                }

                if (this.boostTicks == 1) {
                    if (this.invertYaw.isToggled()) {}

                    this.modifyHorizontal();
                    stopVelocity = false;
                    if (!this.manual.isToggled() && !this.allowStrafe.isToggled() && this.mode.getInput() == 1.0) {
                        this.disabled();
                    }
                }
            } else {
                if (this.isLaunchMode() && this.launchActive && this.fireballTime == 0L && !this.launchBoostStarted) {
                    e.setRotations(mc.player.yaw, 90.0F);
                    this.launchAimed = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onMoveInput(PrePlayerInputEvent e) {
        if (function) {
            if (!this.isLaunchMode() && !this.launchActive) {
                mc.player.input.jumping = false;
                if (this.rotateTick > 0 || this.fireballTime > 0L) {
                    if (Utils.isMoving()) {
                        e.setForward(1.0F);
                    }

                    e.setStrafe(0.0F);
                }

                if (this.notMoving && this.boostTicks < 3) {
                    e.setForward(0.0F);
                    e.setStrafe(0.0F);
                    Utils.setSpeed(0.0);
                }

                if (this.stopMovement.isToggled() && !this.notMoving && this.stopTime >= 1) {
                    e.setForward(0.0F);
                    e.setStrafe(0.0F);
                    Utils.setSpeed(0.0);
                }
            } else {
                if (this.launchActive && this.stopMovement.isToggled() && !this.launchBoostStarted) {
                    e.setForward(0.0F);
                    e.setStrafe(0.0F);
                    mc.player.input.jumping = false;
                    Utils.setSpeed(0.0);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (function && !this.launchActive && !this.isLaunchMode()) {
            Packet packet = e.getPacket();
            if (packet instanceof ExplosionS2CPacket) {
                ExplosionS2CPacket s27 = (ExplosionS2CPacket) packet;
                if (this.fireballTime == 0L
                        || mc.player.getCommandSourceBlockPos().squaredDistanceTo(s27.getX(), s27.getY(), s27.getZ())
                                > this.MAX_EXPLOSION_DIST_SQ) {
                    e.setCanceled(true);
                }

                this.stopTime = -1;
                this.fireballTime = 0L;
                this.resetSlot();
                this.boostTicks = 0;
            } else if (packet instanceof PlayerMoveS2CPacket) {
                Utils.sendMessage("&cReceived setback, disabling.");
                this.disabled();
            }

            if (this.hideExplosion.isToggled()
                    && this.fireballTime != 0L
                    && (packet instanceof AddEntityS2CPacket
                            || packet instanceof ParticleS2CPacket
                            || packet instanceof SoundEventS2CPacket)) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRightClick(RightClickMouseEvent e) {
        if (!e.isCanceled()
                && this.isLaunchMode()
                && this.manual.isToggled()
                && Utils.nullCheck()
                && Utils.holdingFireball()
                && (!this.onlyWithVelocity.isToggled() || ModuleManager.velocity.isEnabled())) {
            e.setCanceled(true);
            if (!this.launchActive) {
                this.beginLaunch();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (this.launchAimed && this.isLaunchMode() && this.launchActive && Utils.nullCheck()) {
            this.launchAimed = false;
            if (!Utils.holdingFireball()) {
                this.finishLaunch();
            } else {
                ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
                this.fireballTime = System.currentTimeMillis();
                this.launchPreviousY = mc.player.velocityY;
                stopVelocity = true;
                mc.getNetworkHandler().sendPacket(new PlayerUseC2SPacket(mc.player.getDisplayItemInHand()));
                mc.player.swingArm();
                mc.getItemInHandRenderer().onBlockUsed();
            }
        }
    }

    public boolean shouldHideLaunchExplosion(ExplosionS2CPacket packet) {
        return this.isLaunchMode()
                && this.launchActive
                && this.fireballTime != 0L
                && !this.launchBoostStarted
                && this.hideExplosion.isToggled()
                && Utils.nullCheck()
                && mc.player.getCommandSourceBlockPos().squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ())
                        <= this.MAX_EXPLOSION_DIST_SQ;
    }

    private boolean isLaunchMode() {
        return this.mode.getInput() == 2.0;
    }

    private void beginLaunch() {
        if (!Utils.nullCheck()) {
            this.finishLaunch();
        } else if (Utils.getTotalHealth(mc.player) <= 3.0F) {
            Utils.sendMessage("&cPrevented throwing fireball due to low health");
            this.finishLaunch();
        } else {
            int fireballSlot = this.setupFireballSlot(true);
            if (fireballSlot != -1) {
                stopModules = true;
                function = true;
                this.launchActive = true;
                slotReset = false;
                slotResetTicks = 0;
                if (this.spoofItem.isToggled()) {
                    this.setLaunchItemSpoof(true);
                }

                if (!this.manual.isToggled()) {
                    this.lastSlot = mc.player.inventory.selectedSlot;
                    mc.player.inventory.selectedSlot = fireballSlot;
                }
            }
        }
    }

    private void updateLaunch() {
        if (!Utils.nullCheck()) {
            this.finishLaunch();
        } else {
            if (this.launchItemSpoofed && !this.launchBoostStarted) {
                this.setLaunchItemSpoof(true);
            }

            if (this.fireballTime != 0L || this.launchBoostStarted) {
                double motionY = mc.player.velocityY;
                if (motionY > 0.7 && motionY > this.launchPreviousY) {
                    if (!this.launchBoostStarted) {
                        this.launchBoostStarted = true;
                        stopVelocity = false;
                        this.restoreLaunchItem();
                    }

                    this.launchVerticalRemaining = (int) this.launchVerticalTicks.getInput();
                    this.launchHorizontalRemaining = (int) this.launchHorizontalTicks.getInput();
                }

                if (!this.launchBoostStarted
                        && System.currentTimeMillis() - this.fireballTime > this.FIREBALL_TIMEOUT) {
                    Utils.sendMessage("&cFireball timed out.");
                    this.finishLaunch();
                } else {
                    if (this.launchVerticalRemaining > 0) {
                        mc.player.velocityY = 2.54;
                        this.launchVerticalRemaining--;
                    }

                    if (this.launchHorizontalRemaining > 0) {
                        double direction = Math.toRadians(mc.player.yaw);
                        mc.player.velocityX = -Math.sin(direction) * 1.63;
                        mc.player.velocityZ = Math.cos(direction) * 1.63;
                        this.launchHorizontalRemaining--;
                    }

                    this.launchPreviousY = mc.player.velocityY;
                    if (this.launchBoostStarted
                            && this.launchVerticalRemaining == 0
                            && this.launchHorizontalRemaining == 0) {
                        this.finishLaunch();
                    }
                }
            }
        }
    }

    private void setLaunchItemSpoof(boolean spoof) {
        IMixinItemRenderer renderer = (IMixinItemRenderer) mc.getItemInHandRenderer();
        renderer.setCancelUpdate(spoof);
        renderer.setCancelReset(spoof);
        this.launchItemSpoofed = spoof;
    }

    private void restoreLaunchItem() {
        if (this.launchItemSpoofed) {
            this.setLaunchItemSpoof(false);
        }

        if (this.lastSlot != -1) {
            if (mc.player != null) {
                mc.player.inventory.selectedSlot = this.lastSlot;
            }

            this.lastSlot = -1;
        }
    }

    private void resetLaunch() {
        this.restoreLaunchItem();
        this.launchActive = this.launchAimed = this.launchBoostStarted = false;
        stopVelocity = false;
        stopModules = false;
        function = false;
        this.fireballTime = 0L;
        this.launchVerticalRemaining = this.launchHorizontalRemaining = 0;
    }

    private void finishLaunch() {
        this.resetLaunch();
        if (!this.manual.isToggled()) {
            this.disable();
        }
    }

    private int getFireballSlot() {
        return Utils.findHotbarSlot(stack -> stack != null && stack.getItem() == Items.FIRE_CHARGE);
    }

    private void enabled() {
        slotReset = false;
        slotResetTicks = 0;
        function = true;
        this.enabled = true;
        stopModules = true;
        this.startY = mc.player.y;
    }

    private void disabled() {
        this.fireballTime = this.rotateTick = this.stopTime = 0;
        this.boostTicks = -1;
        this.resetSlot();
        stopModules = false;
        stopVelocity = false;
        this.enabled = function = this.notMoving = false;
        if (!this.manual.isToggled()) {
            this.disable();
        }

        this.startY = null;
    }

    private int setupFireballSlot(boolean pre) {
        int fireballSlot = this.getFireballSlot();
        if (fireballSlot == -1) {
            Utils.sendMessage("&cFireball not found.");
            this.disabled();
        } else if (pre && Utils.distanceToGround(mc.player) > 3.0) {
            Utils.sendMessage("&cCan't throw fireball right now.");
            this.disabled();
            fireballSlot = -1;
        }

        return fireballSlot;
    }

    private void resetSlot() {
        if (this.lastSlot != -1 && !this.manual.isToggled()) {
            if (this.spoofItem.isToggled()) {
                ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelUpdate(false);
                ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelReset(false);
            }

            mc.player.inventory.selectedSlot = this.lastSlot;
            this.lastSlot = -1;
            this.firstSlot = -1;
        }

        slotReset = true;
    }

    private int getSpeedLevel() {
        Iterator var1 = mc.player.getStatusEffects().iterator();
        if (var1.hasNext()) {
            StatusEffectInstance potionEffect = (StatusEffectInstance) var1.next();
            return potionEffect.getName().equals("potion.moveSpeed") ? potionEffect.getAmplifier() + 1 : 0;
        } else {
            return 0;
        }
    }

    void modifyHorizontal() {
        if (this.boostSetting.getInput() != 0.0) {
            double speed = this.boostSetting.getInput() - Utils.randomizeDouble(1.0E-4, 0.0);
            if (Utils.isMoving()) {
                Utils.setSpeed(speed);
            }
        }
    }

    private void modifyVertical() {
        if (this.verticalMotion.getInput() != 0.0) {
            double ver = (!this.notMoving ? this.verticalMotion.getInput() : 1.16)
                            * (1.0 / (1.0 + 0.05 * this.getSpeedLevel()))
                    + Utils.randomizeDouble(1.0E-4, 0.1);
            double decay = this.motionDecay.getInput() / 1000.0;
            if (this.boostTicks > 1 && !this.temporaryFlightKey()) {
                if (this.boostTicks > 1 || this.boostTicks <= (!this.notMoving ? 32 : 33)) {
                    mc.player.velocityY = Utils.randomizeDouble(0.0101, 0.01);
                }
            } else if (this.boostTicks >= 1 && this.boostTicks <= (!this.notMoving ? 32 : 33)) {
                mc.player.velocityY = ver - this.boostTicks * decay;
            } else if (this.boostTicks >= (!this.notMoving ? 32 : 33) + 3) {
                mc.player.velocityY += 0.028;
                Utils.sendMessage("?");
            }
        }
    }

    private boolean temporaryFlightKey() {
        return this.notMoving ? true : this.temporaryFlightKey.isPressed();
    }
}
