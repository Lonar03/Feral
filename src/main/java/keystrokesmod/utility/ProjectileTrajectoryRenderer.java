package keystrokesmod.utility;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityArrow;
import keystrokesmod.module.impl.world.AntiBot;
import net.minecraft.block.Block;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public final class ProjectileTrajectoryRenderer {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Color FIREBALL_COLOR = new Color(255, 150, 0);
    private static final Color PEARL_COLOR = new Color(210, 0, 255);
    private static final float FIREBALL_TRAJECTORY_LINE_WIDTH = 2.0F;
    private static final float FIREBALL_TRAJECTORY_SHADE_ALPHA = 0.18F;
    private static final double FIREBALL_ACCELERATION = 0.1;
    private static final double FIRST_PERSON_RENDER_CLIP_DISTANCE = 0.5;
    private static final double ARROW_GRAVITY = 0.05;
    private static final double THROWABLE_GRAVITY = 0.03;
    private static final double PROJECTILE_DRAG = 0.99;
    private static final double ARROW_WATER_DRAG = 0.6;
    private static final double THROWABLE_WATER_DRAG = 0.8;
    private static final double WATER_FLOW_ACCELERATION = 0.014;
    private static final double WATER_CHECK_EXPAND_Y = -0.4F;
    private static final double WATER_CHECK_CONTRACT = 0.001;
    private static final double COLLISION_EPSILON_SQ = 1.0E-7;
    private static final double ENTITY_HIT_EXPANSION = 0.3;
    private static final int PROJECTILE_TRAJECTORY_MAX_TICKS = 120;
    private static final int PROJECTILE_TRAJECTORY_SUBDIVISIONS = 4;
    private static final int PROJECTILE_IGNORE_OWNER_TICKS = 5;
    private static final double ARROW_TRAJECTORY_MARKER_BACK_OFFSET = 0.12;
    private static final double ARROW_TRAJECTORY_MARKER_INNER_RADIUS = 0.02;
    private static final double ARROW_TRAJECTORY_MARKER_OUTER_RADIUS = 0.15;
    private static final float ARROW_TRAJECTORY_MARKER_LINE_WIDTH = 2.0F;
    private static final float ARROW_TRAJECTORY_MARKER_ALPHA = 1.0F;
    private static final double ARROW_TRAJECTORY_MARKER_SPIN_SPEED = 0.35;
    private static final double ARROW_TRAJECTORY_MARKER_INNER_BACK_SWEEP = 0.01;
    private static final double ARROW_TRAJECTORY_MARKER_OUTER_BACK_SWEEP = 0.05;
    private final Map<FireballEntity, Vec3d> fireballAccelerationOverrides = new ConcurrentHashMap<>();
    private World world;
    private ArrowEntity arrowPreview;
    private EnderPearlEntity pearlPreview;
    private FireballEntity fireballPreview;
    private boolean crossTarget;
    private boolean autoScale;
    private boolean bowDepth;
    private float targetLineWidth = 2.0F;

    public void configureTarget(boolean crossTarget, boolean autoScale, float targetLineWidth, boolean bowDepth) {
        this.crossTarget = crossTarget;
        this.bowDepth = bowDepth;
        this.autoScale = autoScale;
        this.targetLineWidth = targetLineWidth;
    }

    public void clear() {
        this.fireballAccelerationOverrides.clear();
        this.arrowPreview = null;
        this.pearlPreview = null;
        this.fireballPreview = null;
        this.world = null;
    }

    public void updateWorld() {
        if (this.world != mc.world) {
            this.clear();
            this.world = mc.world;
        }

        this.fireballAccelerationOverrides
                .keySet()
                .removeIf(fireball -> fireball.removed
                        || fireball.world != this.world
                        || this.world == null
                        || this.world.getEntity(fireball.getNetworkId()) != fireball);
    }

    public void onVelocityPacket(EntityVelocityS2CPacket packet) {
        if (mc.world != null) {
            Entity entity = mc.world.getEntity(packet.getId());
            if (entity instanceof FireballEntity) {
                Vec3d motion = new Vec3d(
                        packet.getVelocityX() / 8000.0, packet.getVelocityY() / 8000.0, packet.getVelocityZ() / 8000.0);
                double length = motion.length();
                if (length > 0.0) {
                    double scale = 0.1 / length;
                    this.fireballAccelerationOverrides.put(
                            (FireballEntity) entity, new Vec3d(motion.x * scale, motion.y * scale, motion.z * scale));
                }
            }
        }
    }

    public void renderThrown(Entity entity, float partialTicks) {
        if (!entity.removed) {
            if (entity instanceof FireballEntity) {
                this.renderFireballTrajectory((FireballEntity) entity, partialTicks);
            } else if (entity instanceof ArrowEntity && !((IAccessorEntityArrow) entity).getInGround()) {
                this.renderArrowTrajectory((ArrowEntity) entity, partialTicks);
            } else if (entity instanceof EnderPearlEntity) {
                EnderPearlEntity pearl = (EnderPearlEntity) entity;
                this.renderPearlTrajectory(pearl, pearl.getThrower(), partialTicks);
            }
        }
    }

    public void renderHeld(PlayerEntity player, float partialTicks) {
        ItemStack heldStack = player.getDisplayItemInHand();
        if (heldStack != null) {
            Item item = heldStack.getItem();
            double velocity = 1.5;
            if (item == Items.BOW) {
                if (!player.hasItemInUse() || player.getItemInUse().getItem() != Items.BOW) {
                    return;
                }

                float charge = (player.getRemainingItemUseDuration() + partialTicks) / 20.0F;
                charge = Math.min(1.0F, (charge * charge + charge * 2.0F) / 3.0F);
                if (charge < 0.1F) {
                    return;
                }

                velocity = charge * 3.0;
            } else if (item != Items.ENDER_PEARL && item != Items.FIRE_CHARGE) {
                return;
            }

            float previousYaw = player == mc.player ? player.lastYaw : player.lastHeadYaw;
            float currentYaw = player == mc.player ? player.yaw : player.headYaw;
            float yaw = (float)
                    Math.toRadians(previousYaw + MathHelper.wrapDegrees(currentYaw - previousYaw) * partialTicks);
            float pitch = (float) Math.toRadians(player.lastPitch + (player.pitch - player.lastPitch) * partialTicks);
            Vec3d direction = new Vec3d(
                            -MathHelper.sin(yaw) * MathHelper.cos(pitch),
                            -MathHelper.sin(pitch),
                            MathHelper.cos(yaw) * MathHelper.cos(pitch))
                    .normalize();
            double posX = player.prevX + (player.x - player.prevX) * partialTicks;
            double posY = player.prevY + (player.y - player.prevY) * partialTicks + player.getEyeHeight();
            double posZ = player.prevZ + (player.z - player.prevZ) * partialTicks;
            posX -= MathHelper.cos(yaw) * 0.16F;
            posY -= 0.1F;
            posZ -= MathHelper.sin(yaw) * 0.16F;
            if (item == Items.FIRE_CHARGE) {
                if (this.fireballPreview == null) {
                    this.fireballPreview = new FireballEntity(mc.world);
                }

                this.fireballPreview.shooter = player;
                this.fireballPreview.accelerationX = direction.x * 0.1;
                this.fireballPreview.accelerationY = direction.y * 0.1;
                this.fireballPreview.accelerationZ = direction.z * 0.1;
                this.positionPreview(
                        this.fireballPreview, posX, posY - this.fireballPreview.height * 0.5, posZ, direction, 0.0);
                this.renderFireballTrajectory(this.fireballPreview, partialTicks);
            } else {
                if (item == Items.BOW) {
                    if (this.arrowPreview == null) {
                        this.arrowPreview = new ArrowEntity(mc.world);
                    }

                    this.arrowPreview.shooter = player;
                    this.positionPreview(this.arrowPreview, posX, posY, posZ, direction, velocity);
                    this.renderArrowTrajectory(this.arrowPreview, partialTicks);
                } else {
                    if (this.pearlPreview == null) {
                        this.pearlPreview = new EnderPearlEntity(mc.world);
                    }

                    this.positionPreview(this.pearlPreview, posX, posY, posZ, direction, velocity);
                    this.renderPearlTrajectory(this.pearlPreview, player, partialTicks);
                }
            }
        }
    }

    private void positionPreview(
            Entity projectile, double posX, double posY, double posZ, Vec3d direction, double velocity) {
        projectile.setPosition(posX, posY, posZ);
        projectile.prevX = posX;
        projectile.prevY = posY;
        projectile.prevZ = posZ;
        projectile.velocityX = direction.x * velocity;
        projectile.velocityY = direction.y * velocity;
        projectile.velocityZ = direction.z * velocity;
        projectile.ticks = 0;
    }

    private void renderFireballTrajectory(FireballEntity fireball, float partialTicks) {
        Vec3d accelerationOverride = this.fireballAccelerationOverrides.get(fireball);
        FireballSImulator.Result result = this.crossTarget
                ? FireballSImulator.simulateCenterLine(fireball, accelerationOverride)
                : FireballSImulator.simulate(fireball, accelerationOverride);
        Vec3d impactPosition = result.getImpactPosition();
        if (impactPosition != null) {
            double viewerX = mc.getEntityRenderDispatcher().cameraX;
            double viewerY = mc.getEntityRenderDispatcher().cameraY;
            double viewerZ = mc.getEntityRenderDispatcher().cameraZ;
            double startX = fireball.prevX + (fireball.x - fireball.prevX) * partialTicks;
            double startY = fireball.prevY + (fireball.y - fireball.prevY) * partialTicks + fireball.height * 0.5;
            double startZ = fireball.prevZ + (fireball.z - fireball.prevZ) * partialTicks;
            Vec3d targetPosition = this.crossTarget && result.hitBlock()
                    ? result.getHitPosition()
                    : impactPosition.add(0.0, fireball.height * 0.5, 0.0);
            if (fireball == this.fireballPreview && fireball.shooter == mc.player && mc.options.perspective == 0) {
                Vec3d segment =
                        new Vec3d(targetPosition.x - startX, targetPosition.y - startY, targetPosition.z - startZ);
                double length = segment.length();
                if (length > 0.0) {
                    double clipScale = Math.min(0.5, length) / length;
                    startX += segment.x * clipScale;
                    startY += segment.y * clipScale;
                    startZ += segment.z * clipScale;
                }
            }

            Color fireballColor = FIREBALL_COLOR;
            float red = fireballColor.getRed() / 255.0F;
            float green = fireballColor.getGreen() / 255.0F;
            float blue = fireballColor.getBlue() / 255.0F;
            GL11.glPushMatrix();
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2848);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            GL11.glColor4f(red, green, blue, 1.0F);
            GL11.glBegin(1);
            GL11.glVertex3d(startX - viewerX, startY - viewerY, startZ - viewerZ);
            GL11.glVertex3d(targetPosition.x - viewerX, targetPosition.y - viewerY, targetPosition.z - viewerZ);
            GL11.glEnd();
            if (this.crossTarget) {
                HitResult collision = result.getCollision();
                renderCrossIndicator(
                        targetPosition,
                        collision != null ? collision.face : null,
                        viewerX,
                        viewerY,
                        viewerZ,
                        red,
                        green,
                        blue,
                        this.autoScale,
                        this.targetLineWidth);
            } else {
                Box impactBox = this.getImpactBox(fireball, impactPosition);
                RenderUtils.drawOutlinedBox(impactBox, viewerX, viewerY, viewerZ);
                RenderUtils.drawBoundingBox(impactBox.moved(-viewerX, -viewerY, -viewerZ), red, green, blue, 0.18F);
            }

            GL11.glLineWidth(1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDepthMask(true);
            GL11.glEnable(2929);
            GL11.glEnable(3553);
            GL11.glDisable(2848);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }
    }

    private void renderArrowTrajectory(ArrowEntity arrowEntity, float partialTicks) {
        ProjectileTrajectoryRenderer.ProjectileTrajectoryProps props =
                new ProjectileTrajectoryRenderer.ProjectileTrajectoryProps(
                        0.05,
                        0.99,
                        0.6,
                        Math.max(arrowEntity.width * 0.5, 0.25),
                        arrowEntity.width,
                        arrowEntity.height,
                        true,
                        5);
        this.renderPredictedTrajectory(
                arrowEntity,
                partialTicks,
                this.predictProjectileTrajectory(arrowEntity, props, arrowEntity.shooter),
                Color.WHITE);
    }

    private void renderPearlTrajectory(EnderPearlEntity pearlEntity, Entity owner, float partialTicks) {
        ProjectileTrajectoryRenderer.ProjectileTrajectoryProps props =
                new ProjectileTrajectoryRenderer.ProjectileTrajectoryProps(
                        0.03,
                        0.99,
                        0.8,
                        Math.max(pearlEntity.width * 0.5, 0.125),
                        pearlEntity.width,
                        pearlEntity.height,
                        false,
                        5);
        Color pearlColor = PEARL_COLOR;
        this.renderPredictedTrajectory(
                pearlEntity, partialTicks, this.predictProjectileTrajectory(pearlEntity, props, owner), pearlColor);
    }

    private ProjectileTrajectoryRenderer.TrajectoryPrediction predictProjectileTrajectory(
            Entity projectile, ProjectileTrajectoryRenderer.ProjectileTrajectoryProps props, Entity owner) {
        double posX = projectile.x;
        double posY = projectile.y;
        double posZ = projectile.z;
        double motionX = projectile.velocityX;
        double motionY = projectile.velocityY;
        double motionZ = projectile.velocityZ;
        int ticksInAir = Math.max(0, projectile.ticks);
        List<Vec3d> points = new ArrayList<>();
        points.add(new Vec3d(posX, posY, posZ));

        for (int tick = 0; tick < 120; tick++) {
            ProjectileTrajectoryRenderer.FluidState fluidState =
                    this.sampleFluidState(posX, posY, posZ, props.width, props.height, projectile);
            double[] motion = new double[] {motionX, motionY, motionZ};
            this.applyWaterFlowAcceleration(fluidState, motion);
            motionX = motion[0];
            motionY = motion[1];
            motionZ = motion[2];
            double nextX = posX + motionX;
            double nextY = posY + motionY;
            double nextZ = posZ + motionZ;
            Vec3d start = new Vec3d(posX, posY, posZ);
            Vec3d end = new Vec3d(nextX, nextY, nextZ);
            ProjectileTrajectoryRenderer.BlockCollisionResult blockCollision =
                    this.getNearestBlockCollision(start, end, props);
            HitResult blockHit = blockCollision.hit;
            double bestDistanceSq = blockCollision.distanceSq;
            Vec3d clampedEnd = blockHit != null ? blockHit.facePos : end;
            Box broadBox = new Box(
                            posX - props.hitboxRadius,
                            posY - props.hitboxRadius,
                            posZ - props.hitboxRadius,
                            posX + props.hitboxRadius,
                            posY + props.hitboxRadius,
                            posZ + props.hitboxRadius)
                    .expanded(motionX, motionY, motionZ)
                    .grown(1.0, 1.0, 1.0);
            Vec3d bestEntityHit = null;
            Direction bestEntityHitFace = null;

            for (Entity entity : mc.world.getEntities(projectile, broadBox)) {
                if (entity instanceof LivingEntity
                        && !(entity instanceof ArmorStandEntity)
                        && entity.hasCollision()
                        && ((LivingEntity) entity).deathTicks == 0
                        && (!(entity instanceof PlayerEntity) || !AntiBot.isBot(entity))
                        && (owner == null || !entity.is(owner) || ticksInAir >= props.ignoreOwnerTicks)) {
                    Box expandedBox = entity.getShape().grown(0.3, 0.3, 0.3);
                    HitResult entityHit = expandedBox.clip(start, clampedEnd);
                    if (entityHit != null) {
                        double distanceSq = start.squaredDistanceTo(entityHit.facePos);
                        if (distanceSq + 1.0E-7 < bestDistanceSq) {
                            bestDistanceSq = distanceSq;
                            bestEntityHit = entityHit.facePos;
                            bestEntityHitFace = entityHit.face;
                        }
                    }
                }
            }

            if (bestEntityHit != null) {
                this.addHitSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ, bestEntityHit);
                return new ProjectileTrajectoryRenderer.TrajectoryPrediction(
                        points, bestEntityHit, null, bestEntityHitFace, props.width, props.height);
            }

            if (blockHit != null) {
                this.addHitSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ, blockHit.facePos);
                return new ProjectileTrajectoryRenderer.TrajectoryPrediction(
                        points, blockHit.facePos, blockHit.getPos(), blockHit.face, props.width, props.height);
            }

            this.addFullSegmentPoints(points, posX, posY, posZ, motionX, motionY, motionZ);
            posX = nextX;
            posY = nextY;
            posZ = nextZ;
            motionX *= fluidState.inWater ? props.dragInWater : props.drag;
            motionY *= fluidState.inWater ? props.dragInWater : props.drag;
            motionZ *= fluidState.inWater ? props.dragInWater : props.drag;
            motionY -= props.gravity;
            ticksInAir++;
            if (posY < -64.0) {
                break;
            }
        }

        return new ProjectileTrajectoryRenderer.TrajectoryPrediction(
                points, null, null, null, props.width, props.height);
    }

    private void addFullSegmentPoints(
            List<Vec3d> points, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
        for (int step = 1; step <= 4; step++) {
            double t = step / 4.0;
            points.add(new Vec3d(posX + motionX * t, posY + motionY * t, posZ + motionZ * t));
        }
    }

    private void addHitSegmentPoints(
            List<Vec3d> points,
            double posX,
            double posY,
            double posZ,
            double motionX,
            double motionY,
            double motionZ,
            Vec3d hitVec) {
        double segmentLengthSq = motionX * motionX + motionY * motionY + motionZ * motionZ;
        double hitDx = hitVec.x - posX;
        double hitDy = hitVec.y - posY;
        double hitDz = hitVec.z - posZ;
        double hitDistanceSq = hitDx * hitDx + hitDy * hitDy + hitDz * hitDz;
        double hitT = segmentLengthSq > 0.0 ? Math.sqrt(hitDistanceSq / segmentLengthSq) : 0.0;
        hitT = Math.max(0.0, Math.min(1.0, hitT));
        int subCount = (int) Math.ceil(hitT * 4.0);

        for (int step = 1; step < subCount; step++) {
            double t = step / 4.0;
            points.add(new Vec3d(posX + motionX * t, posY + motionY * t, posZ + motionZ * t));
        }

        points.add(hitVec);
    }

    private void renderPredictedTrajectory(
            Entity projectile,
            float partialTicks,
            ProjectileTrajectoryRenderer.TrajectoryPrediction prediction,
            Color color) {
        if (prediction.points.size() >= 2) {
            double viewerX = mc.getEntityRenderDispatcher().cameraX;
            double viewerY = mc.getEntityRenderDispatcher().cameraY;
            double viewerZ = mc.getEntityRenderDispatcher().cameraZ;
            double startX = projectile.prevX + (projectile.x - projectile.prevX) * partialTicks;
            double startY = projectile.prevY + (projectile.y - projectile.prevY) * partialTicks;
            double startZ = projectile.prevZ + (projectile.z - projectile.prevZ) * partialTicks;
            float red = color.getRed() / 255.0F;
            float green = color.getGreen() / 255.0F;
            float blue = color.getBlue() / 255.0F;
            GL11.glPushMatrix();
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(2848);
            GL11.glDisable(3553);
            if (this.bowDepth && projectile == this.arrowPreview) {
                GL11.glEnable(2929);
            } else {
                GL11.glDisable(2929);
            }

            GL11.glDepthMask(false);
            GL11.glLineWidth(2.0F);
            GL11.glColor4f(red, green, blue, 1.0F);
            GL11.glBegin(3);
            GL11.glVertex3d(startX - viewerX, startY - viewerY, startZ - viewerZ);

            for (int i = 1; i < prediction.points.size(); i++) {
                Vec3d point = prediction.points.get(i);
                GL11.glVertex3d(point.x - viewerX, point.y - viewerY, point.z - viewerZ);
            }

            GL11.glEnd();
            if (prediction.impactPosition != null) {
                if (this.crossTarget) {
                    renderCrossIndicator(
                            prediction.impactPosition,
                            prediction.hitFace,
                            viewerX,
                            viewerY,
                            viewerZ,
                            red,
                            green,
                            blue,
                            this.autoScale,
                            this.targetLineWidth);
                } else {
                    Box impactBox = this.getPredictedImpactBox(projectile, prediction);
                    RenderUtils.drawOutlinedBox(impactBox, viewerX, viewerY, viewerZ);
                    RenderUtils.drawBoundingBox(impactBox.moved(-viewerX, -viewerY, -viewerZ), red, green, blue, 0.18F);
                }
            }

            if (projectile instanceof ArrowEntity && projectile != this.arrowPreview) {
                this.renderArrowTrajectoryStartMarker(
                        prediction,
                        projectile,
                        partialTicks,
                        startX,
                        startY,
                        startZ,
                        viewerX,
                        viewerY,
                        viewerZ,
                        red,
                        green,
                        blue);
            }

            GL11.glLineWidth(1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDepthMask(true);
            GL11.glEnable(2929);
            GL11.glEnable(3553);
            GL11.glDisable(2848);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }
    }

    public static void renderCrossIndicator(
            Vec3d hitPos,
            Direction hitFace,
            double camX,
            double camY,
            double camZ,
            float r,
            float g,
            float b,
            boolean autoScale,
            float lineWidth) {
        double distanceSq = mc.player.squaredDistanceTo(hitPos.x, hitPos.y, hitPos.z);
        double size = autoScale ? Math.min(0.1 * (1.0 + distanceSq / 500.0), 0.5) : 0.1;
        double x = hitPos.x - camX;
        double y = hitPos.y - camY;
        double z = hitPos.z - camZ;
        GL11.glLineWidth(lineWidth);
        GL11.glColor4f(r, g, b, 1.0F);
        GL11.glBegin(1);
        switch (hitFace != null ? hitFace : Direction.UP) {
            case WEST:
            case EAST:
                GL11.glVertex3d(x, y - size, z - size);
                GL11.glVertex3d(x, y + size, z + size);
                GL11.glVertex3d(x, y - size, z + size);
                GL11.glVertex3d(x, y + size, z - size);
                break;
            case NORTH:
            case SOUTH:
                GL11.glVertex3d(x - size, y - size, z);
                GL11.glVertex3d(x + size, y + size, z);
                GL11.glVertex3d(x + size, y - size, z);
                GL11.glVertex3d(x - size, y + size, z);
                break;
            case DOWN:
            case UP:
                GL11.glVertex3d(x - size, y, z - size);
                GL11.glVertex3d(x + size, y, z + size);
                GL11.glVertex3d(x + size, y, z - size);
                GL11.glVertex3d(x - size, y, z + size);
        }

        GL11.glEnd();
    }

    private void renderArrowTrajectoryStartMarker(
            ProjectileTrajectoryRenderer.TrajectoryPrediction prediction,
            Entity projectile,
            float partialTicks,
            double startX,
            double startY,
            double startZ,
            double viewerX,
            double viewerY,
            double viewerZ,
            float red,
            float green,
            float blue) {
        Vec3d direction = this.getArrowTrajectoryMarkerDirection(prediction, projectile, startX, startY, startZ);
        if (direction != null) {
            Vec3d armAxisA = this.getPerpendicularUnitVector(direction);
            if (armAxisA != null) {
                Vec3d armAxisB = this.normalizeVec3(this.cross(direction, armAxisA));
                if (armAxisB != null) {
                    double centerX = startX - direction.x * 0.12;
                    double centerY = startY - direction.y * 0.12;
                    double centerZ = startZ - direction.z * 0.12;
                    double spinAngle = (projectile.ticks + partialTicks) * 0.35;
                    double cosine = Math.cos(spinAngle);
                    double sine = Math.sin(spinAngle);
                    Vec3d rotatedArmAxisA = this.rotateMarkerAxis(armAxisA, armAxisB, cosine, sine);
                    Vec3d rotatedArmAxisB = this.rotateMarkerAxis(armAxisB, armAxisA, cosine, -sine);
                    GL11.glLineWidth(2.0F);
                    GL11.glColor4f(red, green, blue, 1.0F);
                    GL11.glBegin(1);
                    this.addMarkerSegment(
                            centerX, centerY, centerZ, rotatedArmAxisA, direction, viewerX, viewerY, viewerZ);
                    this.addMarkerSegment(
                            centerX,
                            centerY,
                            centerZ,
                            new Vec3d(-rotatedArmAxisA.x, -rotatedArmAxisA.y, -rotatedArmAxisA.z),
                            direction,
                            viewerX,
                            viewerY,
                            viewerZ);
                    this.addMarkerSegment(
                            centerX, centerY, centerZ, rotatedArmAxisB, direction, viewerX, viewerY, viewerZ);
                    this.addMarkerSegment(
                            centerX,
                            centerY,
                            centerZ,
                            new Vec3d(-rotatedArmAxisB.x, -rotatedArmAxisB.y, -rotatedArmAxisB.z),
                            direction,
                            viewerX,
                            viewerY,
                            viewerZ);
                    GL11.glEnd();
                }
            }
        }
    }

    private void addMarkerSegment(
            double centerX,
            double centerY,
            double centerZ,
            Vec3d axis,
            Vec3d direction,
            double viewerX,
            double viewerY,
            double viewerZ) {
        GL11.glVertex3d(
                centerX + axis.x * 0.02 - direction.x * 0.01 - viewerX,
                centerY + axis.y * 0.02 - direction.y * 0.01 - viewerY,
                centerZ + axis.z * 0.02 - direction.z * 0.01 - viewerZ);
        GL11.glVertex3d(
                centerX + axis.x * 0.15 - direction.x * 0.05 - viewerX,
                centerY + axis.y * 0.15 - direction.y * 0.05 - viewerY,
                centerZ + axis.z * 0.15 - direction.z * 0.05 - viewerZ);
    }

    private Vec3d getArrowTrajectoryMarkerDirection(
            ProjectileTrajectoryRenderer.TrajectoryPrediction prediction,
            Entity projectile,
            double startX,
            double startY,
            double startZ) {
        Vec3d direction =
                this.normalizeVec3(new Vec3d(projectile.velocityX, projectile.velocityY, projectile.velocityZ));
        if (direction != null) {
            return direction;
        }

        if (prediction.points.size() < 2) {
            return null;
        }

        Vec3d nextPoint = prediction.points.get(1);
        return this.normalizeVec3(new Vec3d(nextPoint.x - startX, nextPoint.y - startY, nextPoint.z - startZ));
    }

    private Vec3d getPerpendicularUnitVector(Vec3d direction) {
        Vec3d reference = Math.abs(direction.y) < 0.9 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(1.0, 0.0, 0.0);
        return this.normalizeVec3(this.cross(direction, reference));
    }

    private Vec3d rotateMarkerAxis(
            Vec3d primaryAxis, Vec3d secondaryAxis, double primaryWeight, double secondaryWeight) {
        return new Vec3d(
                primaryAxis.x * primaryWeight + secondaryAxis.x * secondaryWeight,
                primaryAxis.y * primaryWeight + secondaryAxis.y * secondaryWeight,
                primaryAxis.z * primaryWeight + secondaryAxis.z * secondaryWeight);
    }

    private Vec3d cross(Vec3d a, Vec3d b) {
        return new Vec3d(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x);
    }

    private Vec3d normalizeVec3(Vec3d vector) {
        double lengthSq = vector.x * vector.x + vector.y * vector.y + vector.z * vector.z;
        if (lengthSq <= 1.0E-6) {
            return null;
        }

        double invLength = 1.0 / Math.sqrt(lengthSq);
        return new Vec3d(vector.x * invLength, vector.y * invLength, vector.z * invLength);
    }

    private Box getPredictedImpactBox(Entity projectile, ProjectileTrajectoryRenderer.TrajectoryPrediction prediction) {
        if (projectile instanceof ArrowEntity) {
            return this.getProjectileImpactBox(prediction);
        } else if (prediction.hitBlockPos != null) {
            Box selectionBox = BlockUtils.getBlockSelectionBox(prediction.hitBlockPos);
            return selectionBox != null
                    ? selectionBox
                    : new Box(
                            prediction.hitBlockPos.getX(),
                            prediction.hitBlockPos.getY(),
                            prediction.hitBlockPos.getZ(),
                            prediction.hitBlockPos.getX() + 1.0,
                            prediction.hitBlockPos.getY() + 1.0,
                            prediction.hitBlockPos.getZ() + 1.0);
        } else {
            return this.getProjectileImpactBox(prediction);
        }
    }

    private Box getProjectileImpactBox(ProjectileTrajectoryRenderer.TrajectoryPrediction prediction) {
        double halfWidth = prediction.width * 0.5;
        return new Box(
                prediction.impactPosition.x - halfWidth,
                prediction.impactPosition.y,
                prediction.impactPosition.z - halfWidth,
                prediction.impactPosition.x + halfWidth,
                prediction.impactPosition.y + prediction.height,
                prediction.impactPosition.z + halfWidth);
    }

    private Box getImpactBox(FireballEntity fireball, Vec3d impactPosition) {
        double halfWidth = fireball.width * 0.5;
        return new Box(
                impactPosition.x - halfWidth,
                impactPosition.y,
                impactPosition.z - halfWidth,
                impactPosition.x + halfWidth,
                impactPosition.y + fireball.height,
                impactPosition.z + halfWidth);
    }

    private ProjectileTrajectoryRenderer.FluidState sampleFluidState(
            double posX, double posY, double posZ, double width, double height, Entity projectile) {
        Box entityBox = new Box(
                posX - width * 0.5, posY, posZ - width * 0.5, posX + width * 0.5, posY + height, posZ + width * 0.5);
        Box waterCheckBox = entityBox.grown(0.0, -0.4F, 0.0).contract(0.001, 0.001, 0.001);
        int minX = MathHelper.floor(waterCheckBox.minX);
        int maxX = MathHelper.floor(waterCheckBox.maxX + 1.0);
        int minY = MathHelper.floor(waterCheckBox.minY);
        int maxY = MathHelper.floor(waterCheckBox.maxY + 1.0);
        int minZ = MathHelper.floor(waterCheckBox.minZ);
        int maxZ = MathHelper.floor(waterCheckBox.maxZ + 1.0);
        if (!mc.world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new ProjectileTrajectoryRenderer.FluidState(false, new Vec3d(0.0, 0.0, 0.0));
        }

        boolean inWater = false;
        Vec3d flowDirection = new Vec3d(0.0, 0.0, 0.0);
        Mutable mutablePos = new Mutable();

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = mc.world.getBlockState(mutablePos);
                    if (blockState.getBlock().getMaterial() == Material.WATER) {
                        double liquidSurfaceY =
                                y + 1 - LiquidBlock.getHeightLoss((Integer) blockState.get(LiquidBlock.LEVEL));
                        if (maxY >= liquidSurfaceY) {
                            inWater = true;
                            flowDirection = blockState
                                    .getBlock()
                                    .applyMaterialDrag(mc.world, mutablePos, projectile, flowDirection);
                        }
                    }
                }
            }
        }

        if (flowDirection.length() > 0.0) {
            flowDirection = flowDirection.normalize();
        }

        return new ProjectileTrajectoryRenderer.FluidState(inWater, flowDirection);
    }

    private void applyWaterFlowAcceleration(ProjectileTrajectoryRenderer.FluidState fluidState, double[] motion) {
        if (fluidState.inWater && !(fluidState.flowDirection.length() <= 0.0)) {
            motion[0] += fluidState.flowDirection.x * 0.014;
            motion[1] += fluidState.flowDirection.y * 0.014;
            motion[2] += fluidState.flowDirection.z * 0.014;
        }
    }

    private Box getBlockSweepBounds(Vec3d start, Vec3d end) {
        return new Box(
                Math.min(start.x, end.x),
                Math.min(start.y, end.y),
                Math.min(start.z, end.z),
                Math.max(start.x, end.x),
                Math.max(start.y, end.y),
                Math.max(start.z, end.z));
    }

    private Box getVanillaProjectileBounds(Block block, BlockPos pos) {
        block.updateShape(mc.world, pos);
        return block.getOutlineShape(mc.world, pos);
    }

    private Box intersectBoxes(Box first, Box second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);
        return !(maxX - minX <= 1.0E-7) && !(maxY - minY <= 1.0E-7) && !(maxZ - minZ <= 1.0E-7)
                ? new Box(minX, minY, minZ, maxX, maxY, maxZ)
                : null;
    }

    private ProjectileTrajectoryRenderer.BlockCollisionResult rayTraceBlockCollisionBoxes(
            Vec3d start, Vec3d end, ProjectileTrajectoryRenderer.ProjectileTrajectoryProps props) {
        Box sweepBounds = this.getBlockSweepBounds(start, end);
        int minX = MathHelper.floor(sweepBounds.minX);
        int maxX = MathHelper.floor(sweepBounds.maxX + 1.0);
        int minY = MathHelper.floor(sweepBounds.minY);
        int maxY = MathHelper.floor(sweepBounds.maxY + 1.0);
        int minZ = MathHelper.floor(sweepBounds.minZ);
        int maxZ = MathHelper.floor(sweepBounds.maxZ + 1.0);
        if (!mc.world.isAreaLoaded(new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ), true)) {
            return new ProjectileTrajectoryRenderer.BlockCollisionResult(null, Double.MAX_VALUE);
        }

        List<Box> collisionBoxes = new ArrayList<>();
        Mutable mutablePos = new Mutable();
        HitResult bestHit = null;
        double bestDistanceSq = Double.MAX_VALUE;

        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    mutablePos.set(x, y, z);
                    BlockState blockState = mc.world.getBlockState(mutablePos);
                    Block block = blockState.getBlock();
                    if ((!props.ignoreBlockWithoutBoundingBox
                                    || block.getCollisionShape(mc.world, mutablePos, blockState) != null)
                            && block.canRayTrace(blockState, false)) {
                        collisionBoxes.clear();
                        Box vanillaProjectileBounds = this.getVanillaProjectileBounds(block, mutablePos);
                        block.addCollisions(mc.world, mutablePos, blockState, sweepBounds, collisionBoxes, null);

                        for (Box collisionBox : collisionBoxes) {
                            Box projectileCollisionBox = this.intersectBoxes(collisionBox, vanillaProjectileBounds);
                            if (projectileCollisionBox != null) {
                                HitResult hit = projectileCollisionBox.clip(start, end);
                                if (hit != null) {
                                    double distanceSq = start.squaredDistanceTo(hit.facePos);
                                    if (distanceSq + 1.0E-7 < bestDistanceSq) {
                                        bestDistanceSq = distanceSq;
                                        bestHit = new HitResult(hit.facePos, hit.face, new BlockPos(mutablePos));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new ProjectileTrajectoryRenderer.BlockCollisionResult(bestHit, bestDistanceSq);
    }

    private ProjectileTrajectoryRenderer.BlockCollisionResult getNearestBlockCollision(
            Vec3d start, Vec3d end, ProjectileTrajectoryRenderer.ProjectileTrajectoryProps props) {
        HitResult vanillaHit = mc.world.rayTrace(start, end, false, props.ignoreBlockWithoutBoundingBox, false);
        double vanillaDistanceSq = vanillaHit != null ? start.squaredDistanceTo(vanillaHit.facePos) : Double.MAX_VALUE;
        ProjectileTrajectoryRenderer.BlockCollisionResult collisionBoxHit =
                this.rayTraceBlockCollisionBoxes(start, end, props);
        return collisionBoxHit.hit != null && collisionBoxHit.distanceSq + 1.0E-7 < vanillaDistanceSq
                ? collisionBoxHit
                : new ProjectileTrajectoryRenderer.BlockCollisionResult(vanillaHit, vanillaDistanceSq);
    }

    private static final class BlockCollisionResult {
        final HitResult hit;
        final double distanceSq;

        private BlockCollisionResult(HitResult hit, double distanceSq) {
            this.hit = hit;
            this.distanceSq = distanceSq;
        }
    }

    private static final class FluidState {
        final boolean inWater;
        final Vec3d flowDirection;

        private FluidState(boolean inWater, Vec3d flowDirection) {
            this.inWater = inWater;
            this.flowDirection = flowDirection;
        }
    }

    private static final class ProjectileTrajectoryProps {
        final double gravity;
        final double drag;
        final double dragInWater;
        final double hitboxRadius;
        final double width;
        final double height;
        final boolean ignoreBlockWithoutBoundingBox;
        final int ignoreOwnerTicks;

        private ProjectileTrajectoryProps(
                double gravity,
                double drag,
                double dragInWater,
                double hitboxRadius,
                double width,
                double height,
                boolean ignoreBlockWithoutBoundingBox,
                int ignoreOwnerTicks) {
            this.gravity = gravity;
            this.drag = drag;
            this.dragInWater = dragInWater;
            this.hitboxRadius = hitboxRadius;
            this.width = width;
            this.height = height;
            this.ignoreBlockWithoutBoundingBox = ignoreBlockWithoutBoundingBox;
            this.ignoreOwnerTicks = ignoreOwnerTicks;
        }
    }

    private static final class TrajectoryPrediction {
        final List<Vec3d> points;
        final Vec3d impactPosition;
        final BlockPos hitBlockPos;
        final Direction hitFace;
        final double width;
        final double height;

        private TrajectoryPrediction(
                List<Vec3d> points,
                Vec3d impactPosition,
                BlockPos hitBlockPos,
                Direction hitFace,
                double width,
                double height) {
            this.points = points;
            this.impactPosition = impactPosition;
            this.hitBlockPos = hitBlockPos;
            this.hitFace = hitFace;
            this.width = width;
            this.height = height;
        }
    }
}
