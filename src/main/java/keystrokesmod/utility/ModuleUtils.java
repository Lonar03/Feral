package keystrokesmod.utility;

import keystrokesmod.event.NoEventPacketEvent;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.combat.Velocity;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.impl.player.Tower;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.BlockPos;

public class ModuleUtils implements IMinecraftInstance {
    public static boolean isBreaking;
    public static boolean threwFireball;
    public static boolean threwFireballLow;
    private int isBreakingTick;
    public static long MAX_EXPLOSION_DIST_SQ = 10L;
    private long FIREBALL_TIMEOUT = 500L;
    private long fireballTime = 0L;
    public static int inAirTicks;
    public static int groundTicks;
    public static int stillTicks;
    public static int fadeEdge;
    public static int lastFaceDifference;
    private int lastFace;
    public static double offsetValue = 2.01E-11;
    public static boolean isAttacking;
    private int attackingTicks;
    public static int profileTicks = -1;
    public static boolean lastTickOnGround;
    public static boolean lastTickPos1;
    private boolean thisTickOnGround;
    private boolean thisTickPos1;
    public static boolean firstDamage;
    public static boolean isBlocked;
    public static boolean damage;
    private int damageTicks;
    private boolean lowhopAir;
    private static boolean allowFriction;
    public static boolean canSlow;
    public static boolean didSlow;
    public static boolean setSlow;

    @ForgeEvents.SubscribeEvent
    public void onSendPacketNoEvent(NoEventPacketEvent e) {
        this.handleAllPacket(e.getPacket());
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        this.handleAllPacket(e.getPacket());
        if (e.getPacket() instanceof PlayerHandActionC2SPacket) {
            isBreaking = true;
        }

        if (e.getPacket() instanceof PlayerUseC2SPacket
                && Utils.holdingFireball()
                && Utils.isBindDown(mc.options.useKey)) {
            this.fireballTime = System.currentTimeMillis();
            threwFireball = true;
            if (mc.player.pitch > 50.0F) {
                threwFireballLow = true;
            }
        }

        if (e.getPacket() instanceof PlayerUseC2SPacket
                && Utils.scaffoldDiagonal(false)
                && ((PlayerUseC2SPacket) e.getPacket()).getFace() != 1) {
            int currentFace = ((PlayerUseC2SPacket) e.getPacket()).getFace();
            if (currentFace == this.lastFace) {
                lastFaceDifference++;
            } else {
                lastFaceDifference = 0;
            }

            this.lastFace = currentFace;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (Utils.nullCheck() && !e.isCanceled()) {
            if (e.getPacket() instanceof ExplosionS2CPacket) {
                ExplosionS2CPacket s27 = (ExplosionS2CPacket) e.getPacket();
                if (threwFireball
                        && mc.player.getCommandSourceBlockPos().squaredDistanceTo(s27.getX(), s27.getY(), s27.getZ())
                                <= MAX_EXPLOSION_DIST_SQ) {
                    ModuleManager.velocity.disable = false;
                    ModuleManager.antiKnockback.disable = false;
                    threwFireball = false;
                    e.setCanceled(false);
                }
            }
        }
    }

    private void handleAllPacket(Packet<?> packet) {
        if (Utils.nullCheck()) {
            if (packet instanceof PlayerUseC2SPacket
                    && Utils.holdingSword()
                    && !BlockUtils.isInteractable(mc.crosshairTarget)
                    && !isBlocked) {
                isBlocked = true;
            } else if (packet instanceof PlayerHandActionC2SPacket && isBlocked) {
                if (((PlayerHandActionC2SPacket) packet).getAction() == Action.RELEASE_USE_ITEM) {
                    isBlocked = false;
                }
            } else if (packet instanceof SelectSlotC2SPacket && isBlocked) {
                isBlocked = false;
            }

            if (packet instanceof PlayerInteractEntityC2SPacket) {
                isAttacking = true;
                this.attackingTicks = 5;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (damage && ++this.damageTicks >= 8) {
            firstDamage = false;
            damage = false;
            this.damageTicks = 0;
        }

        profileTicks++;
        if (isAttacking) {
            if (this.attackingTicks <= 0) {
                isAttacking = false;
            } else {
                this.attackingTicks--;
            }
        }

        if (LongJump.slotReset && ++LongJump.slotResetTicks >= 2) {
            LongJump.stopModules = false;
            LongJump.slotResetTicks = 0;
            LongJump.slotReset = false;
        }

        if (!ModuleManager.bHop.hopping) {
            allowFriction = false;
        } else if (!mc.player.onGround) {
            allowFriction = true;
        }

        if (this.fireballTime > 0L && System.currentTimeMillis() - this.fireballTime > this.FIREBALL_TIMEOUT / 3L) {
            threwFireballLow = false;
            ModuleManager.velocity.disable = false;
            ModuleManager.antiKnockback.disable = false;
        }

        if (this.fireballTime > 0L && System.currentTimeMillis() - this.fireballTime > this.FIREBALL_TIMEOUT) {
            threwFireballLow = false;
            threwFireball = false;
            this.fireballTime = 0L;
            ModuleManager.velocity.disable = false;
            ModuleManager.antiKnockback.disable = false;
        }

        if (isBreaking && ++this.isBreakingTick >= 1) {
            isBreaking = false;
            this.isBreakingTick = 0;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (this.bHopBoostConditions() && firstDamage) {
            Utils.setSpeed(Utils.getHorizontalSpeed());
            firstDamage = false;
        }
    }

    private boolean bHopBoostConditions() {
        return !Tower.suppressesBhop()
                && ModuleManager.bHop.isEnabled()
                && !ModuleManager.bHop.usesStagedMovement()
                && ModuleManager.bHop.damageBoost.isToggled()
                && (!ModuleManager.bHop.damageBoostRequireKey.isToggled()
                        || ModuleManager.bHop.damageBoostKey.isPressed());
    }

    public static double applyFrictionMulti() {
        int speedAmplifier = Utils.getSpeedAmplifier();
        return speedAmplifier > 1 && allowFriction ? 1.0 : 1.0;
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        int simpleY = (int) Math.round(e.posY % 1.0 * 10000.0);
        lastTickOnGround = this.thisTickOnGround;
        this.thisTickOnGround = mc.player.onGround;
        lastTickPos1 = this.thisTickPos1;
        this.thisTickPos1 = mc.player.y % 1.0 == 0.0;
        inAirTicks = mc.player.onGround ? 0 : ++inAirTicks;
        groundTicks = !mc.player.onGround ? 0 : ++groundTicks;
        stillTicks = Utils.isMoving() ? 0 : ++stillTicks;
        Block blockBelow = BlockUtils.getBlock(new BlockPos(mc.player.x, mc.player.y - 1.0, mc.player.z));
        Block blockBelow2 = BlockUtils.getBlock(new BlockPos(mc.player.x, mc.player.y - 2.0, mc.player.z));
        Block block = BlockUtils.getBlock(new BlockPos(mc.player.x, mc.player.y, mc.player.z));
        if (ModuleManager.bHop.didMove
                && (!damage || Velocity.vertical.getInput() == 0.0)
                && !mc.player.collidingHorizontally) {
            if (!(block instanceof AirBlock) || blockBelow instanceof AirBlock && blockBelow2 instanceof AirBlock) {
                this.resetLowhop();
            }

            label90:
            switch ((int) ModuleManager.bHop.mode.getInput()) {
                case 2:
                    switch (simpleY) {
                        case 13:
                            mc.player.velocityY -= 0.02483;
                            break;
                        case 2000:
                            mc.player.velocityY -= 0.1913;
                            break;
                        case 7016:
                            mc.player.velocityY += 0.08;
                    }

                    if (inAirTicks > 6 && Utils.isMoving()) {
                        Utils.setSpeed(Utils.getHorizontalSpeed(mc.player));
                    }

                    if (inAirTicks > 8) {
                        this.resetLowhop();
                    }
                    break;
                case 3:
                    switch (simpleY) {
                        case 13:
                            mc.player.velocityY -= 0.045;
                            break label90;
                        case 2000:
                            mc.player.velocityY -= 0.175;
                            this.resetLowhop();
                        default:
                            break label90;
                    }
                case 4:
                    switch (simpleY) {
                        case 1138:
                            mc.player.velocityY -= 0.13;
                            break;
                        case 2031:
                            mc.player.velocityY -= 0.2;
                            this.resetLowhop();
                            break;
                        case 4200:
                            mc.player.velocityY = 0.39;
                    }
            }
        }

        if (!mc.player.onGround) {
            this.lowhopAir = true;
        } else if (this.lowhopAir) {
            this.resetLowhop();
        }

        if (ModuleManager.bHop.setRotation) {
            if (ModuleManager.bHop.rotateYawOption.isToggled() && KillAura.target == null) {
                float yaw = mc.player.yaw - 55.0F;
                e.setYaw(yaw);
            }

            if (mc.player.onGround) {
                ModuleManager.bHop.setRotation = false;
            }
        }

        if (canSlow && !mc.player.onGround) {
            double motionVal = 0.9 - inAirTicks / 10000.0 - Utils.randomizeDouble(1.0E-5, 6.0E-5);
            if (mc.player.damagedTimer == 0 && inAirTicks > 4 && !setSlow) {
                mc.player.velocityX *= motionVal;
                mc.player.velocityZ *= motionVal;
                setSlow = true;
            }

            didSlow = true;
        } else if (didSlow) {
            didSlow = false;
            canSlow = false;
        }

        if (mc.player.onGround) {
            setSlow = false;
        }
    }

    private void resetLowhop() {
        ModuleManager.bHop.lowhop = false;
        ModuleManager.bHop.didMove = false;
        this.lowhopAir = false;
    }

    public static void handleSlow() {
        didSlow = false;
        canSlow = true;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (ModuleManager.killAura.rotationMode.getInput() == 0.0 && KillAura.target != null) {
                mc.player.lastEasedYaw = mc.player.yaw;
                mc.player.easedYaw = mc.player.yaw;
            }
        }
    }
}
