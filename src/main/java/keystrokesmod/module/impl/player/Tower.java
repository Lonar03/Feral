package keystrokesmod.module.impl.player;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityLivingBase;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.scaffold.ScaffoldSupport;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Mouse;

public class Tower extends Module {
    private static final double DEFAULT_SPEED = 1.2;
    public static SliderSetting modeSetting;
    public static SliderSetting speed;
    public static SliderSetting diagonalSpeed;
    public static SliderSetting speedBoost;
    public static SliderSetting slowedSpeed;
    public static SliderSetting slowedTicks;
    public static ButtonSetting disableInLiquid;
    public static ButtonSetting disableWhileCollided;
    public static ButtonSetting disableSafewalk;
    public static ButtonSetting disableWhileHurt;
    public static ButtonSetting onlyWhileRightClicking;
    public static ButtonSetting setbackRecovery;
    public static ButtonSetting skipSlowWithFastScaffold;
    private static ButtonSetting towerCentered;
    private static ButtonSetting disableBhop;
    private static ButtonSetting allowSprinting;
    public static Tower.Mode mode = Tower.Mode.Vanilla;
    public static boolean towering;
    public static boolean enabledByScaffold;
    private static int ticks;
    private static int diagonalTicks;
    private static int slowedLeft;
    private static boolean diagonal;
    private static int stage;
    private static volatile boolean dropPacketReady;

    public Tower() {
        super("Tower", Module.category.player);
        this.canBeEnabled = false;
        this.registerSetting(new DescriptionSetting("Enable Tower in Scaffold."));
        this.registerSetting(
                modeSetting =
                        new SliderSetting("Mode", 0, new String[] {
                            "Vanilla",
                            "Normal",
                            "Fly",
                            "2 tick",
                            "2.3 tick",
                            "2.5 tick",
                            "4 tick",
                            "5 tick",
                            "6 tick",
                            "Drop",
                            "Stairs"
                        }) {
                            @Override
                            public boolean setSelectedOption(String option) {
                                return super.setSelectedOption("Tower Move".equals(option) ? "Stairs" : option);
                            }
                        });
        this.registerSetting(speed = new SliderSetting("Speed", "", 1.2, 0.5, 7.0, 0.1));
        this.registerSetting(diagonalSpeed = new SliderSetting("Diagonal speed", "", 1.2, 0.4, 7.0, 0.1));
        this.registerSetting(speedBoost = new SliderSetting("Speed boost", "", 1.3, 0.0, 2.0, 0.05));
        this.registerSetting(slowedSpeed = new SliderSetting("Slowed speed", "", 1.0, 0.0, 1.0, 0.02));
        this.registerSetting(slowedTicks = new SliderSetting("Slowed ticks", "", 0.0, 0.0, 20.0, 1.0));
        diagonalSpeed.setMinString("§cDisabled");
        this.registerSetting(allowSprinting = new ButtonSetting("Allow sprinting", false));
        this.registerSetting(disableBhop = new ButtonSetting("Disable bhop", false));
        this.registerSetting(disableInLiquid = new ButtonSetting("Disable in liquid", true));
        this.registerSetting(disableSafewalk = new ButtonSetting("Disable safewalk", false));
        this.registerSetting(disableWhileCollided = new ButtonSetting("Disable while collided", false));
        this.registerSetting(disableWhileHurt = new ButtonSetting("Disable while hurt", false));
        this.registerSetting(onlyWhileRightClicking = new ButtonSetting("Only while right clicking", false));
        this.registerSetting(setbackRecovery = new ButtonSetting("Setback recovery", false));
        this.registerSetting(skipSlowWithFastScaffold = new ButtonSetting("Skip slow with fast scaffold", false));
        this.registerSetting(towerCentered = new ButtonSetting("Tower centered", false));
        towerCentered.visible = false;
    }

    @Override
    public void guiUpdate() {
        towerCentered.setVisible(Tower.Mode.values()[(int) modeSetting.getInput()] == Tower.Mode.Stairs, this);
    }

    private static boolean centeredStairs() {
        return mode == Tower.Mode.Stairs && towerCentered.isToggled() && !ScaffoldSupport.PlayerUtils.isMoving();
    }

    private static boolean canTower() {
        if (mc.player.abilities.flying || mc.screen != null) {
            return false;
        }

        if (Scaffold.instance == null || !Scaffold.instance.isEnabled() || !Scaffold.towerAllowed()) {
            return false;
        }

        if (onlyWhileRightClicking.isToggled() && !Mouse.isButtonDown(1)) {
            return false;
        }

        ItemStack itemStack = mc.player.getDisplayItemInHand();
        if (itemStack != null && itemStack.getItem() instanceof BlockItem) {
            boolean moving = ScaffoldSupport.PlayerUtils.isMoving();
            if (!mc.player.isSneaking() && (moving || mode == Tower.Mode.Drop || centeredStairs())) {
                if (ScaffoldSupport.PlayerUtils.jumpKeyDown()
                        || mode != Tower.Mode.Stairs && stage != 0 && Scaffold.legitTicks != 0) {
                    if (disableInLiquid.isToggled() && ScaffoldSupport.PlayerUtils.inLiquid()) {
                        return false;
                    } else if (disableWhileCollided.isToggled() && mc.player.collidingHorizontally) {
                        return false;
                    } else if (disableWhileHurt.isToggled() && mc.player.damagedTimer > 0) {
                        return false;
                    } else if (diagonalSpeed.getInput() == diagonalSpeed.getMin()
                            && ScaffoldSupport.PlayerUtils.isDiagonal()) {
                        return false;
                    } else if (setbackRecovery.isToggled() && ScaffoldSupport.sinceSetback(2000L)) {
                        ScaffoldSupport.PlayerUtils.setJump(false);
                        return false;
                    } else {
                        return true;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static void preMotion(PreMotionEvent e) {
        if (!towering) {
            ticks = 0;
        } else {
            ticks++;
            diagonal = ScaffoldSupport.PlayerUtils.isDiagonal();
            if (diagonal) {
                diagonalTicks = 1;
            } else if (diagonalTicks > 0 && ++diagonalTicks < 8) {
                diagonal = true;
            }

            if (!allowSprinting.isToggled()) {
                mc.player.setSprinting(false);
            }

            if (mode == Tower.Mode.Vanilla) {
                if (Scaffold.jumpFacingForward.isToggled()) {
                    if (stage == 0) {
                        if (mc.player.onGround) {
                            if (Scaffold.legitTicks == 0) {
                                stage = 1;
                                Scaffold.legitTicks = Scaffold.legitMotion.isToggled() ? 5 : 1;
                            } else {
                                stage = 2;
                            }
                        }
                    } else if (stage == 1) {
                        Scaffold.delay = Scaffold.legitTicks;
                        stage = 2;
                    }
                }
            } else {
                boolean faceBack;
                faceBack = false;
                label161:
                switch (mode) {
                    case Normal:
                        if (mc.player.onGround) {
                            double d = speed(diagonal);
                            ScaffoldSupport.PlayerUtils.jump(d);
                            if (d > 0.17) {
                                faceBack = true;
                            }
                        }
                        break;
                    case Fly:
                        if (mc.player.ticks % 2 == 0) {
                            mc.player.velocityY = 0.21 + speed.getInput() * 0.0525;
                            double d = Math.min(0.17 + speed.getInput() * 0.015, 0.27)
                                    + speedBonus()
                                    + 1.0E-4 * Math.random();
                            ScaffoldSupport.MoveUtils.strafe(d);
                        }
                        break;
                    case _2_tick:
                    case _2__3_tick:
                    case _2__5_tick:
                        switch (ScaffoldSupport.PlayerUtils.posYDecimals()) {
                            case 0:
                                if (stage <= 2) {
                                    double d;
                                    if (ticks == 1) {
                                        d = 0.124;
                                    } else if (mode == Tower.Mode._2__5_tick) {
                                        if (stage == 0) {
                                            d = speed(diagonal);
                                            if (d > 0.17) {
                                                faceBack = true;
                                            }
                                        } else {
                                            d = ticks == 4 ? 0.14 : ScaffoldSupport.MoveUtils.speed();
                                        }
                                    } else {
                                        d = 0.14;
                                    }

                                    if (d != ScaffoldSupport.MoveUtils.speed()) {
                                        mc.player.onGround = true;
                                    }

                                    e.setOnGround(true);
                                    ScaffoldSupport.PlayerUtils.jump(d);
                                    if (mode == Tower.Mode._2__3_tick) {
                                        stage++;
                                    } else if (mode == Tower.Mode._2__5_tick) {
                                        stage += 2;
                                    }
                                } else {
                                    if (Utils.isHypixel()) {
                                        e.posY = e.posY
                                                + Double.parseDouble("0.0003" + ScaffoldSupport.PlayerUtils.idOffset());
                                    } else {
                                        e.setOnGround(true);
                                    }

                                    mc.player.velocityY = 0.0;
                                    stage = 0;
                                }
                                break label161;
                            case 4200:
                                mc.player.velocityY = 0.33F;
                                if (mode != Tower.Mode._2__5_tick) {
                                    ScaffoldSupport.MoveUtils.strafe(speed(diagonal));
                                } else if (ticks == 2 || ticks == 5) {
                                    ScaffoldSupport.MoveUtils.strafe(0.28 + speedBonus());
                                }
                                break label161;
                            case 7500:
                                mc.player.velocityY = 1.0 - mc.player.y % 1.0;
                            default:
                                break label161;
                        }
                    case _4_tick:
                    case _5_tick:
                        switch (ScaffoldSupport.PlayerUtils.posYDecimals()) {
                            case 0:
                                ScaffoldSupport.PlayerUtils.jump(speed(diagonal));
                                break label161;
                            case 13:
                            case 1661:
                            case 2492:
                                e.posY = Math.floor(e.posY);
                                if (Utils.isHypixel()) {
                                    e.posY = e.posY
                                            + Double.parseDouble("0.0003" + ScaffoldSupport.PlayerUtils.idOffset());
                                } else {
                                    e.setOnGround(true);
                                }

                                if (ScaffoldSupport.PlayerUtils.posYDecimals()
                                        == (mode == Tower.Mode._4_tick ? 1661 : 2492)) {
                                    mc.player.velocityY--;
                                }
                            default:
                                break label161;
                        }
                    case _6_tick:
                        if (mc.player.onGround) {
                            double d = speed(diagonal);
                            ScaffoldSupport.PlayerUtils.jump(d);
                            if (d > 0.17) {
                                faceBack = true;
                            }

                            if (Utils.isHypixel()) {
                                mc.player.velocityY = mc.player.velocityY
                                        - (9.0E-4
                                                - Double.parseDouble(
                                                        "0.00000" + ScaffoldSupport.PlayerUtils.idOffset()));
                            }

                            stage = 1;
                        } else if (stage == 1) {
                            int n = ScaffoldSupport.PlayerUtils.posYDecimals();
                            if (n == 4191) {
                                mc.player.velocityY = 0.3275;
                            } else if (n > 2200 && n < 2500) {
                                stage = 2;
                            }
                        } else if (stage == 2) {
                            mc.player.velocityY--;
                            stage = 0;
                        }
                        break;
                    case Drop:
                        if (mc.player.onGround) {
                            ScaffoldSupport.PlayerUtils.jump();
                        }
                        break;
                    case Stairs:
                        if (!centeredStairs()) {
                            climbStairs();
                        }
                }

                if (faceBack && Scaffold.jumpFacingForward.isToggled()) {
                    e.setYaw(ScaffoldSupport.PlayerUtils.movementAngle()
                            - 180.0F
                            + ScaffoldSupport.RotationUtils.randomOffset());
                    Scaffold.delay = 2;
                }
            }
        }
    }

    public static void reset() {
        diagonal = false;
        towering = false;
        enabledByScaffold = false;
        stage = 0;
        slowedLeft = 0;
        diagonalTicks = 0;
        ticks = 0;
        dropPacketReady = false;
    }

    private static void climbStairs() {
        double y = mc.player.y;
        double roundedY = Math.round(y);
        if (mc.player.velocityY > 0.23 && mc.player.velocityY < 0.25 && hasSupport(0.76) && !hasSupport(0.75)) {
            mc.player.velocityY = roundedY - y;
        }

        if (hasSupport(1.0E-4)) {
            mc.player.velocityY = 0.41965F;
        } else if (Math.abs(y - roundedY) <= 1.0E-4) {
            mc.player.velocityY = 0.0;
        }
    }

    private static boolean hasSupport(double distance) {
        return !mc.world
                .getCollisions(mc.player, mc.player.getShape().moved(0.0, -distance, 0.0))
                .isEmpty();
    }

    private static void centerPlayer() {
        double x = Math.floor(mc.player.x) + 0.5;
        double z = Math.floor(mc.player.z) + 0.5;
        if (mc.world
                .getCollisions(mc.player, mc.player.getShape().moved(x - mc.player.x, 0.0, z - mc.player.z))
                .isEmpty()) {
            mc.player.setPosition(x, mc.player.y, z);
        }
    }

    static void onBlockPlaced() {
        if (towering
                && centeredStairs()
                && ScaffoldSupport.PlayerUtils.jumpKeyDown()
                && !mc.player.onGround
                && !(mc.player.velocityY <= 0.0)
                && !mc.player.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
            mc.player.velocityY = 0.41986F;
            mc.player.incrementStat(Stats.JUMPS);
            ((IAccessorEntityLivingBase) mc.player).setJumpTicks(0);
            centerPlayer();
        }
    }

    private static double speed(boolean diagonal, double d) {
        double d2 = ((diagonal ? diagonalSpeed.getInput() : speed.getInput()) + d) / 10.0 + 1.0E-4 * Math.random();
        double var5;
        return (var5 = d2 + speedBonus()) > 0.0 ? var5 : 0.0;
    }

    private static double speed(boolean diagonal) {
        return speed(diagonal, 0.0);
    }

    public static void stop() {
        towering = false;
        if (ticks > 1
                && (mode == Tower.Mode._2__3_tick || mode == Tower.Mode._2__5_tick)
                && stage >= 2
                && mc.player.velocityY == 0.42F) {
            mc.player.velocityY = 0.16659951058455766;
        }

        diagonalTicks = 0;
        stage = 0;
    }

    static boolean allowsSprinting() {
        return allowSprinting.isToggled();
    }

    public static boolean suppressesBhop() {
        return towering && disableBhop.isToggled();
    }

    private static double speedBonus() {
        int n = ScaffoldSupport.PlayerUtils.speedLevel();
        switch (n) {
            case 0:
                return 0.0;
            case 1:
                return 0.05;
            case 2:
                return speedBoost.getInput() / 10.0;
            default:
                return 0.06 * n;
        }
    }

    public static void setFromScaffold(ButtonSetting tower) {
        if (tower.isToggled()) {
            if (!enabledByScaffold) {
                enabledByScaffold = true;
            }
        } else if (enabledByScaffold) {
            reset();
        }
    }

    private static void postPlayerInput() {
        if (canTower()) {
            towering = true;
            slowedLeft = 0;
            if (mode == Tower.Mode.Vanilla) {
                if (Scaffold.jumpFacingForward.isToggled()) {
                    ScaffoldSupport.PlayerUtils.setJump(true);
                    if (stage == 2) {
                        stage = 0;
                        ScaffoldSupport.PlayerUtils.setJump(true);
                    } else {
                        ScaffoldSupport.PlayerUtils.setJump(false);
                    }

                    if (Scaffold.legitMotion.isToggled() && diagonal && ScaffoldSupport.PlayerUtils.airTicks == 1L) {
                        ScaffoldSupport.PlayerUtils.setForward(0.0F);
                        ScaffoldSupport.PlayerUtils.setStrafe(0.0F);
                    }
                }
            } else if (!centeredStairs()) {
                ScaffoldSupport.PlayerUtils.setJump(false);
            }
        } else if (towering) {
            towering = false;
            if (slowedLeft == 0) {
                slowedLeft = (int) slowedTicks.getInput();
            }

            stage = 0;
            Scaffold.resetKeepY();
        }

        if (slowedLeft > 0) {
            slowedLeft--;
            if (ScaffoldSupport.PlayerUtils.isMoving()) {
                boolean slow = false;
                if (Scaffold.fastScaffold.jump) {
                    if (ScaffoldSupport.PlayerUtils.perSpeedLevel(1.0) == 0.0
                            && Scaffold.fastScaffold.canFastScaffold()) {
                        ScaffoldSupport.PlayerUtils.multiplyMotion(0.4);
                    } else {
                        slow = true;
                    }
                } else if (!skipSlowWithFastScaffold.isToggled() || !Scaffold.fastScaffold.canFastScaffold()) {
                    slow = true;
                }

                if (slow) {
                    ScaffoldSupport.MoveUtils.setSpeed(slowedSpeed.getInput() / 10.0);
                }
            }
        }
    }

    public static void handleInput() {
        if (enabledByScaffold) {
            Tower.Mode selected = Tower.Mode.values()[(int) modeSetting.getInput()];
            if (selected != mode) {
                stop();
            }

            mode = selected;
            postPlayerInput();
        }
    }

    public static void handleMotion(PreMotionEvent event) {
        if (enabledByScaffold) {
            preMotion(event);
        }
    }

    public static void handleMovementPacket(PlayerMoveC2SPacket packet) {
        dropPacketReady = packet.getClass() == PlayerMoveC2SPacket.class || packet instanceof Angles;
    }

    public static float scaleStairsFriction(float friction) {
        if (enabledByScaffold && towering && mode == Tower.Mode.Stairs && ScaffoldSupport.PlayerUtils.isMoving()) {
            SliderSetting movementSpeed = ScaffoldSupport.PlayerUtils.isDiagonal() ? diagonalSpeed : speed;
            return (float) (friction * (movementSpeed.getInput() / 1.2));
        } else {
            return friction;
        }
    }

    public static void handleMoveFlying() {
        boolean canDrop = dropPacketReady;
        dropPacketReady = false;
        if (enabledByScaffold
                && towering
                && mode == Tower.Mode.Drop
                && Utils.nullCheck()
                && !mc.player.onGround
                && ScaffoldSupport.PlayerUtils.airTicks == 6L
                && canDrop) {
            BlockPos under = ScaffoldSupport.BlockUtils.below(2.0);
            if (!ScaffoldSupport.BlockUtils.isReplaceable(under)) {
                mc.player.velocityY = mc.player.velocityY - (0.25 - Math.random() * 0.001);
            }
        }
    }

    public enum Mode {
        Vanilla,
        Normal,
        Fly,
        _2_tick,
        _2__3_tick,
        _2__5_tick,
        _4_tick,
        _5_tick,
        _6_tick,
        Drop,
        Stairs;
    }
}
