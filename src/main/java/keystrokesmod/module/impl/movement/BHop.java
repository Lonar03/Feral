package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PlayerTeleportEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.player.Scaffold;
import keystrokesmod.module.impl.player.Tower;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public class BHop extends Module {
    private static final int STRAFE_B = 5;
    private static final int LOW = 6;
    private static final double BASE_SPEED = 0.28730000691562896;
    public SliderSetting mode;
    public static SliderSetting speedSetting;
    private final SliderSetting steps;
    private final ButtonSetting fastFall;
    private final ButtonSetting strafeFix;
    private ButtonSetting liquidDisable;
    private ButtonSetting sneakDisable;
    private final ButtonSetting jumpMoving;
    private final ButtonSetting disableWhileFlying;
    public ButtonSetting rotateYawOption;
    public ButtonSetting damageBoost;
    public ButtonSetting damageBoostRequireKey;
    public GroupSetting damageBoostGroup;
    public KeySetting damageBoostKey;
    public String[] modes = new String[] {"Strafe A", "Ground", "9 tick", "8 tick", "7 tick", "Strafe B", "Low"};
    public boolean hopping;
    public boolean lowhop;
    public boolean didMove;
    public boolean setRotation;
    private int activeMode = -1;
    private int stage = 1;
    private int hops;
    private int recoveryTicks;
    private double lastDistance;
    private double lastMultiplier = 1.0;
    private float movementYaw;

    public BHop() {
        super("BHop", Module.category.movement);
        this.registerSetting(this.mode = new SliderSetting("Mode", 0, this.modes));
        this.registerSetting(speedSetting = new SliderSetting("Speed", 2.0, 0.7, 2.0, 0.01));
        this.registerSetting(this.steps = new SliderSetting("Steps", 40.3, 1.0, 180.0, 0.1));
        this.registerSetting(this.fastFall = new ButtonSetting("Fast fall", false));
        this.registerSetting(this.strafeFix = new ButtonSetting("Strafe fix", true));
        this.registerSetting(this.liquidDisable = new ButtonSetting("Disable in liquid", true));
        this.registerSetting(this.sneakDisable = new ButtonSetting("Disable while sneaking", true));
        this.registerSetting(this.jumpMoving = new ButtonSetting("Only jump when moving", true));
        this.registerSetting(this.disableWhileFlying = new ButtonSetting("Disable while flying", true));
        this.registerSetting(this.rotateYawOption = new ButtonSetting("Rotate yaw", false));
        this.registerSetting(this.damageBoostGroup = new GroupSetting("Damage boost"));
        this.registerSetting(this.damageBoost = new ButtonSetting(this.damageBoostGroup, "Enable", false));
        this.registerSetting(
                this.damageBoostRequireKey = new ButtonSetting(this.damageBoostGroup, "Require key", false));
        this.registerSetting(this.damageBoostKey = new KeySetting(this.damageBoostGroup, "Enable key", 51));
    }

    @Override
    public void guiUpdate() {
        boolean staged = this.usesStagedMovement();
        this.steps.setVisible(staged, this);
        this.fastFall.setVisible(staged, this);
        this.strafeFix.setVisible(staged, this);
        this.damageBoostKey.setVisible(this.damageBoostRequireKey.isToggled(), this);
    }

    @Override
    public String getInfo() {
        return this.modes[(int) this.mode.getInput()];
    }

    @ForgeEvents.SubscribeEvent
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (Tower.suppressesBhop()) {
            this.resetMovement();
        } else {
            LocalClientPlayerEntity player = mc.player;
            if (player.onGround
                    && !this.shouldPauseForFlight()
                    && (!this.jumpMoving.isToggled()
                            || player.input.movementForward != 0.0F
                            || player.input.movementSideways != 0.0F)) {
                if (this.usesStagedMovement()) {
                    if (this.recoveryTicks == 0 && this.canHop() && !Utils.noSlowingBackWithBow()) {
                        mc.player.input.jumping = false;
                    }
                } else if (this.hopping) {
                    mc.player.input.jumping = false;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (Tower.suppressesBhop() || this.shouldPauseForFlight()) {
            this.resetMovement();
        } else if (this.usesStagedMovement()) {
            this.updateStagedMotion(e);
        } else {
            if (this.activeMode != (int) this.mode.getInput()) {
                this.resetMovement();
                this.activeMode = (int) this.mode.getInput();
            }

            if ((!mc.player.isInWater() && !mc.player.isInLava() || !this.liquidDisable.isToggled())
                    && (!mc.player.isSneaking() || !this.sneakDisable.isToggled())) {
                if (!LongJump.function) {
                    if (this.mode.getInput() >= 1.0) {
                        if (mc.player.onGround && (!this.jumpMoving.isToggled() || Utils.isMoving())) {
                            if (this.rotateYawOption.isToggled()
                                    && mc.player.forwardSpeed <= -0.5
                                    && mc.player.sidewaysSpeed == 0.0F
                                    && KillAura.target == null
                                    && !Utils.noSlowingBackWithBow()
                                    && !mc.player.collidingHorizontally) {
                                this.setRotation = true;
                            }

                            mc.player.jump();
                            double speed = speedSetting.getInput() - 0.52;
                            double speedModifier = speed;
                            int speedAmplifier = Utils.getSpeedAmplifier();
                            switch (speedAmplifier) {
                                case 1:
                                    speedModifier = speed + 0.02;
                                    break;
                                case 2:
                                    speedModifier = speed + 0.04;
                                    break;
                                case 3:
                                    speedModifier = speed + 0.1;
                            }

                            if (Utils.isMoving() && !Utils.noSlowingBackWithBow()) {
                                Utils.setSpeed(speedModifier - Utils.randomizeDouble(3.0E-4, 1.0E-4));
                                this.didMove = true;
                            }

                            this.hopping = true;
                        }

                        if (mc.player.forwardSpeed <= 0.5 && this.hopping) {
                            ModuleUtils.handleSlow();
                        }

                        if (!mc.player.onGround) {
                            this.hopping = false;
                        }
                    }

                    switch ((int) this.mode.getInput()) {
                        case 0:
                            if (Utils.isMoving()) {
                                if (mc.player.onGround) {
                                    mc.player.jump();
                                }

                                mc.player.setSprinting(true);
                                Utils.setSpeed(Utils.getHorizontalSpeed() + 0.005 * speedSetting.getInput());
                                this.hopping = true;
                            } else {
                                this.hopping = false;
                                if (mc.player.onGround && !this.jumpMoving.isToggled()) {
                                    mc.player.jump();
                                }
                            }
                    }
                }
            }
        }
    }

    public boolean usesStagedMovement() {
        int selected = (int) this.mode.getInput();
        return selected == 5 || selected == 6;
    }

    private boolean shouldPauseForFlight() {
        return this.disableWhileFlying.isToggled() && (mc.player.abilities.flying || ModuleManager.fly.isEnabled());
    }

    private boolean canHop() {
        LocalClientPlayerEntity player = mc.player;
        return !player.removed
                && !player.isRiding()
                && !player.isClimbing()
                && !LongJump.function
                && (!this.liquidDisable.isToggled() || !player.isInWater() && !player.isInLava())
                && (!this.sneakDisable.isToggled() || !player.isSneaking() && !player.input.sneaking);
    }

    public void applyStagedMovement(float forward, float strafe, float yaw) {
        if (!Tower.suppressesBhop() && !this.shouldPauseForFlight()) {
            LocalClientPlayerEntity player = mc.player;
            int selected = (int) this.mode.getInput();
            if (this.activeMode != selected) {
                this.resetMovement();
                this.activeMode = selected;
            }

            if (this.recoveryTicks > 0) {
                this.recoveryTicks--;
            } else if (!this.canHop() || Utils.noSlowingBackWithBow()) {
                this.resetMovement();
            } else if (forward == 0.0F && strafe == 0.0F) {
                this.resetMovement();
                if (!this.jumpMoving.isToggled() && player.onGround) {
                    player.jump();
                    this.hopping = player.velocityY > 0.0;
                }
            } else {
                boolean low = selected == 6;
                boolean scaffoldJump = low && player.onGround && Scaffold.consumeKeepYJump();
                boolean liquid = player.isInWater() || player.isInLava();
                boolean damageBoostActive = this.damageBoost.isToggled()
                        && (!this.damageBoostRequireKey.isToggled() || this.damageBoostKey.isPressed());
                double naturalSpeed = Math.hypot(player.velocityX, player.velocityZ);
                double base = this.getBaseSpeed(player, low, liquid);
                if (player.onGround && this.stage > 2) {
                    this.stage = 2;
                }

                if (this.stage == 1 || this.stage == 2 && !player.onGround) {
                    this.movementYaw = yaw;
                    if (player.onGround) {
                        this.stage = 2;
                    }

                    if (!scaffoldJump) {
                        return;
                    }
                }

                double speed;
                if (this.stage == 2 && player.onGround) {
                    if (!scaffoldJump) {
                        double previousY = player.velocityY;
                        player.jump();
                        if (player.velocityY <= previousY) {
                            this.resetMovement();
                            return;
                        }
                    }

                    if (this.strafeFix.isToggled() && !liquid) {
                        if (low) {
                            player.velocityY += -0.01999999999999813;
                        } else if (this.hops > 0) {
                            player.velocityY = player.velocityY + (0.004F + 1.0E-4F * Math.random());
                        }
                    }

                    if (this.rotateYawOption.isToggled()
                            && forward <= -0.49F
                            && strafe == 0.0F
                            && KillAura.target == null
                            && !player.collidingHorizontally) {
                        this.setRotation = true;
                    }

                    this.movementYaw = yaw
                            + (forward < 0.0F ? 180.0F : 0.0F)
                            - Math.signum(strafe) * (forward == 0.0F ? 88.58F : Math.signum(forward) * 43.51F);
                    speed = (low ? this.lastDistance : base) * 2.13050398;
                    this.hopping = true;
                } else if (this.stage == 3) {
                    double floor = !low && this.hops != 0 ? base * 1.15 : base;
                    speed = this.lastDistance - (low ? 0.819999 : 0.666) * (this.lastDistance - floor);
                } else {
                    if (this.stage == 7 && player.velocityY <= 0.0) {
                        if (low) {
                            player.velocityY = Math.min(player.velocityY, -0.18517061160392762);
                        }

                        if (this.fastFall.isToggled()) {
                            player.velocityY -= 0.18999;
                        }
                    }

                    speed = low ? this.lastDistance * 0.98 : base * 1.255 * Math.pow(0.99, this.stage - 1);
                }

                float turn = (float) this.steps.getInput();
                float direction = yaw
                        + (forward < 0.0F ? 179.83F : 0.0F)
                        - Math.signum(strafe) * (forward == 0.0F ? 89.453F : Math.signum(forward) * 44.5933F);
                this.movementYaw = this.movementYaw
                        + MathHelper.clamp(MathHelper.wrapDegrees(direction - this.movementYaw), -turn, turn);
                double directionScale =
                        forward == 0.0F ? 0.75 : (strafe != 0.0F ? 0.985 : (forward < 0.0F ? 0.85 : 1.0));
                this.lastMultiplier = Math.max(speedSetting.getInput(), 0.01);
                double horizontalSpeed = Math.max(base, speed) * directionScale * this.lastMultiplier;
                if (damageBoostActive && player.damagedTimer > 0) {
                    horizontalSpeed = Math.max(horizontalSpeed, naturalSpeed);
                }

                double radians = Math.toRadians(this.movementYaw);
                player.velocityX = -Math.sin(radians) * horizontalSpeed;
                player.velocityZ = Math.cos(radians) * horizontalSpeed;
                this.stage++;
            }
        } else {
            this.resetMovement();
        }
    }

    private double getBaseSpeed(LocalClientPlayerEntity player, boolean low, boolean liquid) {
        double base = 0.28730000691562896;
        StatusEffectInstance effect = player.getEffectInstance(StatusEffect.SPEED);
        if (effect != null && (low || effect.getDuration() > 10)) {
            base *= 1.0 + 0.15 * (effect.getAmplifier() + 1);
        }

        if (player.isSubmergedIn(Material.REPLACEABLE_PLANT)) {
            return base * 0.5;
        } else if (player.isSneaking()) {
            return base * 0.8;
        } else if (liquid) {
            return base * 0.54;
        } else {
            BlockPos below = new BlockPos(player.x, player.y - 0.1, player.z);
            if (mc.world.getBlockState(below).getBlock().slipperiness == 0.98F) {
                return base * 2.4;
            } else if (player.getHungerManager().getFoodLevel() < 6) {
                return base * 0.765;
            } else {
                return low && ModuleManager.getModule(Scaffold.class).isEnabled() ? base * 0.8 : base;
            }
        }
    }

    private void updateStagedMotion(PreMotionEvent event) {
        LocalClientPlayerEntity player = mc.player;
        if (this.canHop() && this.recoveryTicks <= 0 && this.activeMode == (int) this.mode.getInput()) {
            double dx = player.x - player.lastX;
            double dz = player.z - player.lastZ;
            this.lastDistance = Math.hypot(dx, dz) / this.lastMultiplier;
            if (this.hopping && this.lastDistance != 0.0) {
                boolean grounded = event.isOnGround();
                boolean low = this.activeMode == 6;
                if (this.strafeFix.isToggled() && this.hops > 0 && !player.isInWater() && !player.isInLava()) {
                    if (low) {
                        this.applyLowStrafeFix(event, player);
                    } else if (grounded) {
                        event.posY = event.posY + (0.005F + 1.0E-4F * Math.random());
                        player.setPosition(player.x, event.posY, player.z);
                    }
                }

                if (grounded) {
                    this.hops++;
                    this.hopping = false;
                }
            }
        }
    }

    private void applyLowStrafeFix(PreMotionEvent event, LocalClientPlayerEntity player) {
        double roundedY = Math.round(event.posY * 64.0) / 64.0;
        if (!ModuleManager.getModule(Scaffold.class).isEnabled()) {
            if (event.isOnGround() && Math.round((player.prevY - player.y) * 1000.0) == 375L) {
                event.setOnGround(false);
            } else {
                if (this.stage < 5 || player.fallDistance == 0.0F) {
                    return;
                }

                double fraction = Math.round(event.posY * 1000.0) / 1000.0 % 1.0;
                if (fraction % 0.015625 == 0.0
                        && !mc.world
                                .getCollisions(
                                        player,
                                        player.getShape().grown(0.2, 0.0, 0.2).moved(0.0, -0.6, 0.0))
                                .isEmpty()) {
                    event.posY = roundedY;
                    event.setOnGround(true);
                }
            }
        }

        if (!event.isOnGround() && roundedY % 1.0 == 0.015625 && player.fallDistance > 0.0F) {
            event.posY = roundedY;
            event.setOnGround(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onTeleport(PlayerTeleportEvent event) {
        if (this.usesStagedMovement()) {
            this.resetMovement();
            this.recoveryTicks = 15;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload event) {
        if (event.world == mc.world) {
            this.onDisable();
        }
    }

    private void resetMovement() {
        this.hopping = false;
        this.lowhop = false;
        this.didMove = false;
        this.setRotation = false;
        this.stage = 1;
        this.hops = 0;
        this.lastDistance = 0.0;
        this.lastMultiplier = 1.0;
    }

    @Override
    public void onDisable() {
        this.resetMovement();
        this.activeMode = -1;
        this.recoveryTicks = 0;
    }
}
