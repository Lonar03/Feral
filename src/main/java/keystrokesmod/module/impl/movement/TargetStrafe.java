package keystrokesmod.module.impl.movement;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import keystrokesmod.event.JumpEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.StrafeEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.opengl.GL11;

public class TargetStrafe extends Module {
    private static final int MIDDLE_MOUSE_BIND = 1002;
    private static final int RING_SEGMENTS = 96;
    private final SliderSetting radius;
    private final SliderSetting range;
    private final SliderSetting yawOffset;
    private final SliderSetting steps;
    private final KeySetting heldBind;
    private final ButtonSetting strafeBehindTarget;
    private final ButtonSetting reverseOnCollision;
    private final ButtonSetting avoidVoid;
    private final ButtonSetting renderPath;
    private int direction;
    private int flippedAt;
    private boolean manualDirection;
    private LivingEntity steeringTarget;
    private int steeringTick;
    private Float movementYaw;
    private final boolean[] clearRingPoints = new boolean[96];
    private LivingEntity ringTarget;
    private int ringTick;

    public TargetStrafe() {
        super("TargetStrafe", Module.category.movement);
        this.registerSetting(new DescriptionSetting("Only works with KillAura."));
        this.registerSetting(this.radius = new SliderSetting("Radius", 1.5, 0.0, 8.0, 0.1));
        this.registerSetting(this.range = new SliderSetting("Range", 4.0, 3.0, 10.0, 0.1));
        this.registerSetting(this.yawOffset = new SliderSetting("Yaw offset", 45.0, 0.0, 90.0, 5.0));
        this.registerSetting(this.steps = new SliderSetting("Steps", 60.0, 15.0, 180.0, 1.0));
        this.registerSetting(
                this.heldBind = new KeySetting("Held bind", 1002) {
                    @Override
                    public void loadProfile(JsonObject data) {
                        if (data.has(this.getProfileKey())) {
                            super.loadProfile(data);
                        } else {
                            String legacyName = "Only while middle clicking";
                            JsonPrimitive legacy =
                                    data.has(legacyName) && data.get(legacyName).isJsonPrimitive()
                                            ? data.getAsJsonPrimitive(legacyName)
                                            : null;
                            this.setKey(legacy != null && legacy.isBoolean() && !legacy.getAsBoolean() ? 0 : 1002);
                        }
                    }
                });
        this.registerSetting(this.avoidVoid = new ButtonSetting("Avoid void", true));
        this.registerSetting(this.reverseOnCollision = new ButtonSetting("Reverse on collision", true));
        this.registerSetting(this.strafeBehindTarget = new ButtonSetting("Strafe behind target", false));
        this.registerSetting(this.renderPath = new ButtonSetting("Render path", false));
    }

    @Override
    public void onEnable() {
        this.direction = -1;
        this.flippedAt = -5;
        this.manualDirection = false;
        this.steeringTarget = null;
        this.ringTarget = null;
        this.steeringTick = -1;
        this.movementYaw = null;
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload event) {
        if (event.world.isClient) {
            this.onEnable();
        }
    }

    private boolean active() {
        if (this.isEnabled()
                && Utils.nullCheck()
                && Freecam.freeEntity == null
                && ModuleManager.killAura != null
                && ModuleManager.killAura.isEnabled()
                && KillAura.target != null
                && !(mc.player.input.movementForward < 1.0F)
                && (this.heldBind.getKey() == 0 || this.heldBind.isPressed())
                && !RotationHelper.get().fixMovement()) {
            LivingEntity target = KillAura.target;
            return !target.removed
                            && target.deathTicks == 0
                            && !(target.getHealth() <= 0.0F)
                            && mc.world.getEntity(target.getNetworkId()) == target
                    ? this.horizontalDistanceSq() <= this.range.getInput() * this.range.getInput() + 2.0
                    : false;
        } else {
            return false;
        }
    }

    private boolean groundWithinReach(double x, double z) {
        return this.groundWithinReach(x, mc.player.y, z);
    }

    private boolean groundWithinReach(double x, double y, double z) {
        Vec3d start = new Vec3d(x, y + 0.001, z);
        Vec3d end = new Vec3d(x, y - 3.0, z);
        HitResult hit = mc.world.rayTrace(start, end, false, true, false);
        return hit != null && hit.type == Type.BLOCK && y - hit.facePos.y < 3.0;
    }

    private double horizontalDistanceSq() {
        double x = KillAura.target.x - mc.player.x;
        double z = KillAura.target.z - mc.player.z;
        return x * x + z * z;
    }

    public Float getMovementYaw() {
        if (!this.active()) {
            this.steeringTarget = null;
            this.movementYaw = null;
            return null;
        }

        LivingEntity target = KillAura.target;
        int tick = mc.player.ticks;
        if (this.steeringTick == tick && this.steeringTarget == target) {
            return this.movementYaw;
        }

        Float previousYaw = this.steeringTick == tick - 1 && this.steeringTarget == target ? this.movementYaw : null;
        this.steeringTick = tick;
        this.steeringTarget = target;
        if (this.reverseOnCollision.isToggled() && tick - this.flippedAt > 4 && mc.player.collidingHorizontally) {
            this.direction *= -1;
            this.flippedAt = tick;
        }

        double x = mc.player.x;
        double z = mc.player.z;
        double stride = this.horizontalStride();
        boolean seekBack = this.shouldSeekBack(tick);
        if (seekBack) {
            this.direction = this.rearDirection(target, x, z, this.direction);
        }

        float yaw = this.steer(target, x, z, this.direction, seekBack, previousYaw);
        if (this.shouldAvoidDrops() && !this.hasSupportAhead(x, z, yaw, stride)) {
            Float reverseYaw = this.steerAwayFromDrop(target, x, z, stride, this.direction, yaw, previousYaw);
            if (reverseYaw == null) {
                this.movementYaw = null;
                return null;
            }

            this.direction *= -1;
            this.flippedAt = tick;
            yaw = reverseYaw;
        }

        this.movementYaw = yaw;
        return this.movementYaw;
    }

    private float steer(LivingEntity target, double x, double z, int side, boolean seekBack, Float previousYaw) {
        double dx = target.x - x;
        double dz = target.z - z;
        float towardTarget = this.yawTo(dx, dz);
        float yaw = towardTarget;
        double orbitRadius = this.radius.getInput();
        if (dx * dx + dz * dz <= orbitRadius * orbitRadius + 2.0) {
            if (seekBack) {
                float angleToRear = MathHelper.wrapDegrees(target.yaw - towardTarget);
                if (Math.abs(angleToRear) <= 20.0F) {
                    double rear = Math.toRadians(target.yaw);
                    double rearX = dx + Math.sin(rear) * orbitRadius;
                    double rearZ = dz - Math.cos(rear) * orbitRadius;
                    yaw = rearX * rearX + rearZ * rearZ > 0.01 ? this.yawTo(rearX, rearZ) : towardTarget + 90.0F * side;
                } else {
                    float correction = (float) ((orbitRadius - Math.hypot(dx, dz)) * 45.0);
                    float limit = (float) this.yawOffset.getInput();
                    yaw = towardTarget + (90.0F + MathHelper.clamp(correction, -limit, limit)) * side;
                }
            } else {
                yaw = towardTarget + (float) this.yawOffset.getInput() * side;
            }
        }

        if (previousYaw != null) {
            float maxTurn = (float) this.steps.getInput();
            yaw = previousYaw + MathHelper.clamp(MathHelper.wrapDegrees(yaw - previousYaw), -maxTurn, maxTurn);
        }

        return yaw;
    }

    private float yawTo(double x, double z) {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    private int rearDirection(LivingEntity target, double x, double z, int side) {
        float towardTarget = this.yawTo(target.x - x, target.z - z);
        float angle = MathHelper.wrapDegrees(target.yaw - towardTarget);
        if (!(Math.abs(angle) <= 20.0F) && !(Math.abs(angle) >= 175.0F)) {
            return angle > 0.0F ? -1 : 1;
        } else {
            return side;
        }
    }

    private boolean shouldSeekBack(int tick) {
        return this.strafeBehindTarget.isToggled() && !this.manualDirection && tick - this.flippedAt > 4;
    }

    private boolean shouldAvoidDrops() {
        return this.avoidVoid.isToggled()
                && !mc.player.abilities.flying
                && !LongJump.function
                && (ModuleManager.fly == null || !ModuleManager.fly.isDamageFlightActive());
    }

    private double horizontalStride() {
        return Math.max(0.2, Math.hypot(mc.player.velocityX, mc.player.velocityZ));
    }

    private boolean hasSupportAhead(double x, double z, float yaw, double stride) {
        double angle = Math.toRadians(yaw);
        double dx = -Math.sin(angle);
        double dz = Math.cos(angle);
        double lookAhead = Math.max(0.6, stride * 2.0);
        return this.groundWithinReach(x + dx * stride, z + dz * stride)
                && this.groundWithinReach(x + dx * lookAhead, z + dz * lookAhead);
    }

    private Float steerAwayFromDrop(
            LivingEntity target, double x, double z, double stride, int side, float unsafeYaw, Float previousYaw) {
        float reverseYaw = this.steer(target, x, z, -side, false, previousYaw);
        if (this.hasSupportAhead(x, z, reverseYaw, stride)) {
            return reverseYaw;
        }

        float retreatYaw = unsafeYaw + 180.0F;
        return this.hasSupportAhead(x, z, retreatYaw, stride) ? retreatYaw : null;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent event) {
        if (this.renderPath.isToggled() && this.active()) {
            LivingEntity target = KillAura.target;
            if (this.steeringTarget == target && this.movementYaw != null && mc.player.ticks - this.steeringTick <= 1) {
                EntityRenderDispatcher camera = mc.getEntityRenderDispatcher();
                GL11.glPushAttrib(57605);

                try {
                    GL11.glDisable(3553);
                    GL11.glDisable(2896);
                    GL11.glEnable(3042);
                    GL11.glBlendFunc(770, 771);
                    GL11.glEnable(2848);
                    GL11.glHint(3154, 4354);
                    GL11.glDepthMask(false);
                    GL11.glLineWidth(2.0F);
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                    this.drawPreview(camera, target, event.partialTicks);
                } finally {
                    GL11.glPopAttrib();
                }
            }
        }
    }

    private void drawPreview(EntityRenderDispatcher camera, LivingEntity target, float partialTicks) {
        double x = target.prevX + (target.x - target.prevX) * partialTicks - camera.cameraX;
        double y = target.prevY + (target.y - target.prevY) * partialTicks - camera.cameraY + 0.05;
        double z = target.prevZ + (target.z - target.prevZ) * partialTicks - camera.cameraZ;
        double orbitRadius = Math.max(target.width * 0.5, this.radius.getInput());
        if (this.ringTarget != target || this.ringTick != mc.player.ticks) {
            boolean avoidDrops = this.shouldAvoidDrops();

            for (int i = 0; i < 96; i++) {
                double angle = (Math.PI * 2) * i / 96.0;
                double px = target.x + orbitRadius * Math.cos(angle);
                double pz = target.z + orbitRadius * Math.sin(angle);
                this.clearRingPoints[i] = mc.world
                                .getCollisions(
                                        mc.player,
                                        mc.player
                                                .getShape()
                                                .moved(px - mc.player.x, target.y - mc.player.y, pz - mc.player.z))
                                .isEmpty()
                        && (!avoidDrops || this.groundWithinReach(px, target.y, pz));
            }

            this.ringTarget = target;
            this.ringTick = mc.player.ticks;
        }

        GL11.glBegin(1);

        for (int i = 0; i < 96; i++) {
            int next = (i + 1) % 96;
            if (this.clearRingPoints[i] && this.clearRingPoints[next]) {
                double angle = (Math.PI * 2) * i / 96.0;
                double nextAngle = (Math.PI * 2) * next / 96.0;
                GL11.glVertex3d(x + orbitRadius * Math.cos(angle), y, z + orbitRadius * Math.sin(angle));
                GL11.glVertex3d(x + orbitRadius * Math.cos(nextAngle), y, z + orbitRadius * Math.sin(nextAngle));
            }
        }

        GL11.glEnd();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onPostPlayerInput(PostPlayerInputEvent event) {
        this.manualDirection = false;
        if (this.active()) {
            if (mc.player.input.movementSideways != 0.0F) {
                this.manualDirection = true;
                this.direction = -((int) Math.signum(mc.player.input.movementSideways));
                mc.player.input.movementSideways = 0.0F;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onStrafe(StrafeEvent event) {
        Float yaw = this.getMovementYaw();
        if (yaw != null) {
            event.setYaw(yaw);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onJump(JumpEvent event) {
        if (event.entity == mc.player) {
            Float yaw = this.getMovementYaw();
            if (yaw != null) {
                event.setYaw(yaw);
            }
        }
    }
}
