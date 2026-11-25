package keystrokesmod.module.impl.movement;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import keystrokesmod.event.CollisionEvent;
import keystrokesmod.event.PlayerMoveEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.event.StrafeEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.PacketUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Position;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.util.math.Box;
import org.apache.commons.lang3.RandomUtils;

public class Fly extends Module {
    private SliderSetting mode;
    public static SliderSetting horizontalSpeed;
    private SliderSetting verticalSpeed;
    private SliderSetting customDecay;
    private SliderSetting customBoostTimer;
    private SliderSetting customBoostTicks;
    private ButtonSetting customWaitForDamage;
    private ButtonSetting customUseTimer;
    private ButtonSetting showBPS;
    private ButtonSetting stopMotion;
    private ButtonSetting keepY;
    private String[] modes = new String[] {"Vanilla", "Fast", "Fast 2", "Walk", "Hover", "Damage"};
    private ClientWorld customWorld;
    private double customMoveSpeed;
    private int customStage;
    private int customBoostTick;
    private boolean customDamage;
    private boolean canFly;
    private int maxY;
    private final Queue<Packet<?>> motionPackets = new LinkedBlockingQueue<>();
    private ClientWorld motionWorld;
    private long lastAntiKickTime;
    private Box FULL_ABB = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public Fly() {
        super("Fly", Module.category.movement);
        this.registerSetting(this.mode = new SliderSetting("Fly", 0, this.modes));
        this.registerSetting(horizontalSpeed = new SliderSetting("Horizontal speed", 2.0, 1.0, 9.0, 0.1));
        this.registerSetting(this.verticalSpeed = new SliderSetting("Vertical speed", 2.0, 1.0, 9.0, 0.1));
        this.registerSetting(this.customDecay = new SliderSetting("Decay", 159.0, 159.0, 600.0, 1.0));
        this.registerSetting(this.customBoostTimer = new SliderSetting("Boost timer", "x", 2.0, 1.0, 3.5, 0.05));
        this.registerSetting(this.customBoostTicks = new SliderSetting("Boost ticks", 15.0, 1.0, 40.0, 1.0));
        this.registerSetting(this.customWaitForDamage = new ButtonSetting("Wait for damage", false));
        this.registerSetting(this.customUseTimer = new ButtonSetting("Use timer", false));
        this.registerSetting(this.showBPS = new ButtonSetting("Show BPS", false));
        this.registerSetting(this.stopMotion = new ButtonSetting("Stop motion", false));
        this.registerSetting(this.keepY = new ButtonSetting("Keep-Y", false));
    }

    @Override
    public void guiUpdate() {
        horizontalSpeed.setVisible(this.mode.getInput() != 3.0, this);
        this.verticalSpeed.setVisible(this.mode.getInput() != 3.0 && this.mode.getInput() != 5.0, this);
        this.customDecay.setVisible(this.mode.getInput() == 5.0, this);
        this.customBoostTimer.setVisible(this.mode.getInput() == 5.0, this);
        this.customBoostTicks.setVisible(this.mode.getInput() == 5.0, this);
        this.customWaitForDamage.setVisible(this.mode.getInput() == 5.0, this);
        this.customUseTimer.setVisible(this.mode.getInput() == 5.0, this);
        this.keepY.setVisible(this.mode.getInput() == 3.0, this);
    }

    @Override
    public String getInfo() {
        return this.modes[(int) this.mode.getInput()];
    }

    @Override
    public void onEnable() {
        this.releaseCustomFlight();
        if (Utils.nullCheck()) {
            this.canFly = mc.player.abilities.flying;
            this.maxY = mc.player.getCommandSourceBlockPos().getY();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onCollision(CollisionEvent e) {
        if (this.mode.getInput() == 3.0 && !Utils.isBindDown(mc.options.sneakKey)) {
            if (e.blockPos.getY() < (this.keepY.isToggled() ? this.maxY : mc.player.y)) {
                e.boundingBox = this.FULL_ABB.moved(e.blockPos.getX(), e.blockPos.getY(), e.blockPos.getZ());
            }
        } else {
            this.maxY = mc.player.getCommandSourceBlockPos().getY();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.mode.getInput() != 5.0 || !Utils.nullCheck()) {
            this.releaseCustomFlight();
        }

        if (this.mode.getInput() == 4.0 && Utils.nullCheck()) {
            if (this.motionWorld != mc.world) {
                this.releaseMotionPackets();
                this.motionWorld = mc.world;
                this.lastAntiKickTime = System.currentTimeMillis();
            }

            mc.player.abilities.flying = false;
            mc.player.abilities.setFlySpeed(0.05F);
            mc.player.velocityX = 0.0;
            mc.player.velocityY = 0.0;
            mc.player.velocityZ = 0.0;
            if (mc.focused) {
                if (Utils.isBindDown(mc.options.jumpKey)) {
                    mc.player.velocityY = this.verticalSpeed.getInput() / 2.0;
                }

                if (Utils.isBindDown(mc.options.sneakKey)) {
                    mc.player.velocityY = -this.verticalSpeed.getInput() / 2.0;
                }
            }

            double groundDistance = this.findGroundDistance();
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket(true));
            long now = System.currentTimeMillis();
            if (now - this.lastAntiKickTime >= (groundDistance <= 290.0 ? 500L : 100L)) {
                this.sendAntiKickPackets(groundDistance);
                this.lastAntiKickTime = now;
            }
        } else {
            this.releaseMotionPackets();
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onStrafe(StrafeEvent e) {
        if (this.mode.getInput() == 4.0 && Utils.nullCheck()) {
            e.setFriction(0.0F);
            this.setSpeed(horizontalSpeed.getInput() * 0.99, 88.3883F);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (this.mode.getInput() == 4.0
                && Utils.nullCheck()
                && this.motionWorld == mc.world
                && e.getPacket() instanceof PlayerMoveC2SPacket
                && Utils.isHypixel()) {
            if (this.motionPackets.size() >= 40) {
                this.flushMotionPackets();
            }

            if (((PlayerMoveC2SPacket) e.getPacket()).hasPos()) {
                this.motionPackets.offer(e.getPacket());
                e.setCanceled(true);
            }
        }
    }

    @Override
    public void onUpdate() {
        if (this.mode.getInput() != 5.0) {
            this.releaseCustomFlight();
        }

        if (this.mode.getInput() != 4.0) {
            this.releaseMotionPackets();
        }

        if (this.mode.getInput() != 3.0 && this.mode.getInput() != 4.0 && this.mode.getInput() != 5.0) {
            switch ((int) this.mode.getInput()) {
                case 0:
                    mc.player.velocityY = 0.0;
                    mc.player.abilities.setFlySpeed((float) (0.05F * horizontalSpeed.getInput()));
                    mc.player.abilities.flying = true;
                    break;
                case 1:
                    mc.player.onGround = true;
                    if (mc.screen == null) {
                        if (Utils.jumpDown()) {
                            mc.player.velocityY = 0.3 * this.verticalSpeed.getInput();
                        } else if (Utils.jumpDown()) {
                            mc.player.velocityY = -0.3 * this.verticalSpeed.getInput();
                        } else {
                            mc.player.velocityY = 0.0;
                        }
                    } else {
                        mc.player.velocityY = 0.0;
                    }

                    mc.player.abilities.setFlySpeed(0.2F);
                    mc.player.abilities.flying = true;
                    this.setSpeed(0.85 * horizontalSpeed.getInput());
                    break;
                case 2:
                    double nextDouble = RandomUtils.nextDouble(1.0E-7, 1.2E-7);
                    if (mc.player.ticks % 2 == 0) {
                        nextDouble = -nextDouble;
                    }

                    if (!mc.player.onGround) {
                        mc.player.setPosition(mc.player.x, mc.player.y + nextDouble, mc.player.z);
                    }

                    mc.player.velocityY = 0.0;
                    this.setSpeed(0.4 * horizontalSpeed.getInput());
            }
        }
    }

    @Override
    public void onDisable() {
        this.releaseMotionPackets();
        this.releaseCustomFlight();
        if (!Utils.nullCheck()) {
            this.canFly = false;
        } else {
            if (mc.player.abilities.canFly) {
                mc.player.abilities.flying = this.canFly;
            } else {
                mc.player.abilities.flying = false;
            }

            this.canFly = false;
            switch ((int) this.mode.getInput()) {
                case 0:
                case 1:
                case 4:
                    mc.player.abilities.setFlySpeed(0.05F);
                case 2:
                case 3:
                default:
                    if (this.stopMotion.isToggled()) {
                        mc.player.velocityZ = 0.0;
                        mc.player.velocityY = 0.0;
                        mc.player.velocityX = 0.0;
                    }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (this.showBPS.isToggled() && e.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            if (mc.screen == null && !mc.options.debugEnabled) {
                RenderUtils.renderBPS(true, false);
            }
        }
    }

    private void setSpeed(double speed) {
        this.setSpeed(speed, 90.0F);
    }

    @ForgeEvents.SubscribeEvent
    public void onCustomMove(PlayerMoveEvent event) {
        if (this.mode.getInput() == 5.0 && Utils.nullCheck()) {
            this.prepareCustomFlight();
            if (!this.customDamage && this.customStage == 1) {
                event.x = 0.0;
                event.z = 0.0;
            } else {
                double baseSpeed = this.getCustomBaseSpeed(0.2873);
                if (mc.player.collidingHorizontally || !Utils.isMoving()) {
                    this.customMoveSpeed = baseSpeed;
                }

                switch (this.customStage) {
                    case 0:
                        if (mc.player.fallDistance < 3.75F) {
                            for (int i = 0; i < 60; i++) {
                                this.sendCustomPosition(0.0625, false);
                                this.sendCustomPosition(0.0, false);
                            }
                        } else {
                            this.customStage = 2;
                        }

                        this.sendCustomPosition(0.0, true);
                        if (!this.customWaitForDamage.isToggled()) {
                            this.customDamage = true;
                        }
                        break;
                    case 1:
                        this.customMoveSpeed = this.getCustomBaseSpeed(0.3);
                        break;
                    case 2:
                        event.y = mc.player.velocityY = 0.3999;
                        this.customMoveSpeed = baseSpeed * horizontalSpeed.getInput();
                        this.customBoostTick = 0;
                        break;
                    case 3:
                        this.customMoveSpeed = baseSpeed * horizontalSpeed.getInput();
                        this.customBoostTick = 0;
                        break;
                    default:
                        this.customMoveSpeed =
                                this.customMoveSpeed - this.customMoveSpeed / this.customDecay.getInput();
                        event.y = mc.player.velocityY = 0.0;
                        if (mc.world
                                .getCollisions(mc.player, mc.player.getShape().moved(0.0, -1.0E-4, 0.0))
                                .isEmpty()) {
                            mc.player.setPosition(mc.player.x, mc.player.y - 1.0E-14, mc.player.z);
                        }
                }

                if (this.customStage >= 3) {
                    float normal = this.customUseTimer.isToggled() ? 1.0766F : 1.0F;
                    float boost = (float) this.customBoostTimer.getInput();
                    int ticks = (int) this.customBoostTicks.getInput();
                    ((IAccessorMinecraft) mc).getTimer().tpsScale = this.customBoostTick < ticks && boost > normal
                            ? boost - (boost - normal) * this.customBoostTick / ticks
                            : normal;
                    if (this.customBoostTick < Integer.MAX_VALUE) {
                        this.customBoostTick++;
                    }
                }

                this.customStage++;
                mc.player.speedInAir = 0.0F;
                double velocityX = mc.player.velocityX;
                double velocityZ = mc.player.velocityZ;
                this.setSpeed(Math.max(this.customMoveSpeed, baseSpeed));
                event.x = mc.player.velocityX;
                event.z = mc.player.velocityZ;
                mc.player.velocityX = velocityX;
                mc.player.velocityZ = velocityZ;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onCustomReceivePacket(ReceivePacketEvent event) {
        if (this.mode.getInput() == 5.0 && Utils.nullCheck()) {
            this.prepareCustomFlight();
            if (event.getPacket() instanceof EntityVelocityS2CPacket
                    && ((EntityVelocityS2CPacket) event.getPacket()).getId() == mc.player.getNetworkId()) {
                this.customDamage = true;
                event.setCanceled(true);
            } else if (event.getPacket() instanceof PlayerMoveS2CPacket) {
                this.customMoveSpeed = this.getCustomBaseSpeed(0.2873);
                this.customBoostTick = Integer.MAX_VALUE;
            }
        }
    }

    private void prepareCustomFlight() {
        if (this.customWorld != mc.world) {
            this.releaseCustomFlight();
            this.customWorld = mc.world;
            this.customMoveSpeed = 0.0;
            this.customStage = 0;
            this.customBoostTick = 0;
            this.customDamage = false;
            mc.player.abilities.flying = false;
            mc.player.abilities.setFlySpeed(0.05F);
        }
    }

    private void releaseCustomFlight() {
        if (this.customWorld != null) {
            Utils.resetTimer();
            if (Utils.nullCheck() && this.customWorld == mc.world) {
                this.setSpeed(0.0);
                mc.player.speedInAir = mc.player.isSprinting() ? 0.026F : 0.02F;
            }

            this.customWorld = null;
        }
    }

    private double getCustomBaseSpeed(double base) {
        StatusEffectInstance speed = mc.player.getEffectInstance(StatusEffect.SPEED);
        if (speed != null) {
            base *= 1.0 + 0.2 * (speed.getAmplifier() + 1);
        }

        StatusEffectInstance slowness = mc.player.getEffectInstance(StatusEffect.SLOWNESS);
        if (slowness != null) {
            base /= 1.0 + 0.2 * (slowness.getAmplifier() + 1);
        }

        return base;
    }

    private void sendCustomPosition(double offset, boolean onGround) {
        mc.player.networkHandler.sendPacket(new Position(mc.player.x, mc.player.y + offset, mc.player.z, onGround));
    }

    boolean isDamageFlightActive() {
        return this.isEnabled() && this.mode.getInput() == 5.0 && this.customWorld == mc.world && this.customStage >= 2;
    }

    private void setSpeed(double speed, float yawOffset) {
        if (speed == 0.0) {
            mc.player.velocityZ = 0.0;
            mc.player.velocityX = 0.0;
        } else {
            Float orbitYaw = ModuleManager.targetStrafe == null ? null : ModuleManager.targetStrafe.getMovementYaw();
            if (orbitYaw != null) {
                double radians = Math.toRadians(orbitYaw.floatValue());
                mc.player.velocityX = -Math.sin(radians) * speed;
                mc.player.velocityZ = Math.cos(radians) * speed;
            } else {
                double moveForward = mc.player.input.movementForward;
                double moveStrafe = mc.player.input.movementSideways;
                float yaw = mc.player.yaw;
                if (moveForward == 0.0 && moveStrafe == 0.0) {
                    mc.player.velocityZ = 0.0;
                    mc.player.velocityX = 0.0;
                } else {
                    if (moveForward != 0.0) {
                        if (moveStrafe > 0.0) {
                            yaw += moveForward > 0.0 ? -45 : 45;
                        } else if (moveStrafe < 0.0) {
                            yaw += moveForward > 0.0 ? 45 : -45;
                        }

                        moveStrafe = 0.0;
                        if (moveForward > 0.0) {
                            moveForward = 1.0;
                        } else if (moveForward < 0.0) {
                            moveForward = -1.0;
                        }
                    }

                    double radians = Math.toRadians(yaw + yawOffset);
                    double sin = Math.sin(radians);
                    double cos = Math.cos(radians);
                    mc.player.velocityX = moveForward * speed * cos + moveStrafe * speed * sin;
                    mc.player.velocityZ = moveForward * speed * sin - moveStrafe * speed * cos;
                }
            }
        }
    }

    private double findGroundDistance() {
        double step = 1.0;
        double distance = 0.0;
        Box bounds = mc.player.getShape().grown(0.0625, 0.0625, 0.0625);

        while (distance < mc.player.y) {
            if (mc.world.containsNonAir(bounds.moved(0.0, -distance, 0.0))) {
                if (step < 0.0625) {
                    break;
                }

                distance -= step;
                step /= 2.0;
            }

            distance += step;
        }

        return distance;
    }

    private void sendAntiKickPackets(double groundDistance) {
        double groundY = mc.player.y - groundDistance;
        if (!(groundDistance > 300.0) && !(groundY <= 0.0)) {
            double y = mc.player.y;

            while (y > groundY) {
                y = Math.max(y - 8.0, groundY);
                mc.player.networkHandler.sendPacket(new Position(mc.player.x, y, mc.player.z, true));
            }

            while (y < mc.player.y) {
                y = Math.min(y + 8.0, mc.player.y);
                mc.player.networkHandler.sendPacket(new Position(mc.player.x, y, mc.player.z, true));
            }
        }
    }

    private void flushMotionPackets() {
        Packet<?> packet;
        while ((packet = this.motionPackets.poll()) != null) {
            PacketUtils.sendPacketNoEvent(packet);
        }
    }

    private void releaseMotionPackets() {
        if (this.motionWorld != null && Utils.nullCheck() && this.motionWorld == mc.world) {
            this.flushMotionPackets();
        } else {
            this.motionPackets.clear();
        }

        this.motionWorld = null;
    }
}
