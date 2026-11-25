package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public final class DragonDivePrediction {
    private static final double LOOK_AHEAD_TICKS = 40.0;
    private static final int MAX_HIGHLIGHTED_BLOCKS = 256;
    private static final int MAX_CHECKED_BLOCKS = 8192;
    private static final int MAX_STEPS = 256;
    private static final DragonDivePrediction.Result EMPTY =
            new DragonDivePrediction.Result(null, null, null, Collections.emptyList(), Collections.emptyList());
    private final EnderDragonEntity dragon;
    private int lastTick = -1;
    private boolean lastBedWarsRules;
    private Vec3d lastPosition;
    private float lastYaw;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private double verticalAcceleration;
    private double turnRate;
    private DragonDivePrediction.Result result = EMPTY;

    public DragonDivePrediction(EnderDragonEntity dragon) {
        this.dragon = dragon;
    }

    public DragonDivePrediction.Result update(boolean bedWarsRules) {
        if (this.lastTick == this.dragon.ticks && this.lastBedWarsRules == bedWarsRules) {
            return this.result;
        }

        this.lastBedWarsRules = bedWarsRules;
        Vec3d position = new Vec3d(this.dragon.x, this.dragon.y, this.dragon.z);
        int elapsedTicks = this.dragon.ticks - this.lastTick;
        if (this.lastPosition != null
                && elapsedTicks > 0
                && elapsedTicks <= 5
                && !(position.squaredDistanceTo(this.lastPosition) > elapsedTicks * elapsedTicks * 64.0)) {
            double observedX = (position.x - this.lastPosition.x) / elapsedTicks;
            double observedY = (position.y - this.lastPosition.y) / elapsedTicks;
            double observedZ = (position.z - this.lastPosition.z) / elapsedTicks;
            this.verticalAcceleration = MathHelper.clamp((observedY - this.velocityY) / elapsedTicks, -0.04, 0.04);
            this.velocityX = this.velocityX + (observedX - this.velocityX) * 0.65;
            this.velocityY = this.velocityY + (observedY - this.velocityY) * 0.65;
            this.velocityZ = this.velocityZ + (observedZ - this.velocityZ) * 0.65;
            this.turnRate =
                    MathHelper.clamp(MathHelper.wrapDegrees(this.dragon.yaw - this.lastYaw) / elapsedTicks, -3.0, 3.0);
            this.lastPosition = position;
            this.lastYaw = this.dragon.yaw;
            this.lastTick = this.dragon.ticks;
            if (this.dragon.isAlive() && this.dragon.ticks >= 2) {
                this.result = this.predict(bedWarsRules || this.canBreakBlocks(), bedWarsRules);
            } else {
                this.result = EMPTY;
            }

            return this.result;
        } else {
            this.velocityX = this.velocityY = this.velocityZ = this.verticalAcceleration = this.turnRate = 0.0;
            this.lastPosition = position;
            this.lastYaw = this.dragon.yaw;
            this.lastTick = this.dragon.ticks;
            this.result = EMPTY;
            return this.result;
        }
    }

    private boolean canBreakBlocks() {
        World rulesWorld = this.dragon.world;
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServer server = minecraft.getServer();
        if (server != null && server.worlds != null && server.worlds.length > 0) {
            ServerWorld serverWorld = server.getWorld(rulesWorld.dimension.getId());
            if (serverWorld != null) {
                rulesWorld = serverWorld;
            }
        }

        return rulesWorld.getGameRules().getBoolean("mobGriefing");
    }

    private DragonDivePrediction.Result predict(boolean canBreakBlocks, boolean ignoreBlockProtection) {
        Box head = this.dragon.head.getShape();
        Box body = this.dragon.body.getShape();
        Set<BlockPos> checked = new HashSet<>();
        Set<BlockPos> affected = new LinkedHashSet<>();
        Set<BlockPos> contacts = new LinkedHashSet<>();
        List<Vec3d> path = new ArrayList<>();
        Vec3d impact = null;
        Box impactSurface = null;
        Direction impactFace = null;
        Box previousHead = head;
        Box previousBody = body;
        double x = this.dragon.x;
        double y = this.dragon.y;
        double z = this.dragon.z;
        double motionX = this.velocityX;
        double motionY = this.velocityY;
        double motionZ = this.velocityZ;
        double acceleration = this.verticalAcceleration;
        double yawVelocity = Math.toRadians(this.turnRate);
        double yawOffset = 0.0;
        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        double step = Math.max(0.15625, 0.5 / Math.max(1.0, speed));

        for (double tick = 0.0; tick <= 40.0; tick += step) {
            Box predictedHead = this.movePart(head, x, y, z, yawOffset);
            Box predictedBody = this.movePart(body, x, y, z, yawOffset);
            Vec3d headCenter = new Vec3d(
                    (predictedHead.minX + predictedHead.maxX) * 0.5,
                    (predictedHead.minY + predictedHead.maxY) * 0.5,
                    (predictedHead.minZ + predictedHead.maxZ) * 0.5);
            if (impact == null) {
                path.add(headCenter);
            }

            Set<BlockPos> futureContacts = tick > 0.0 ? contacts : null;
            this.collectBlocks(predictedHead, checked, affected, futureContacts, canBreakBlocks, ignoreBlockProtection);
            this.collectBlocks(predictedBody, checked, affected, futureContacts, canBreakBlocks, ignoreBlockProtection);
            if (impact == null && !contacts.isEmpty()) {
                DragonDivePrediction.Contact firstContact = null;

                for (BlockPos pos : contacts) {
                    DragonDivePrediction.Contact headContact = this.findContact(previousHead, predictedHead, pos);
                    DragonDivePrediction.Contact bodyContact = this.findContact(previousBody, predictedBody, pos);
                    if (headContact != null && (firstContact == null || headContact.fraction < firstContact.fraction)) {
                        firstContact = headContact;
                    }

                    if (bodyContact != null && (firstContact == null || bodyContact.fraction < firstContact.fraction)) {
                        firstContact = bodyContact;
                    }
                }

                if (firstContact != null) {
                    impact = firstContact.position;
                    impactSurface = firstContact.surface;
                    impactFace = firstContact.face;
                    path.add(impact);
                    contacts = null;
                } else {
                    contacts.clear();
                }
            }

            if (!canBreakBlocks && impact != null || affected.size() >= 256 || checked.size() >= 8192) {
                break;
            }

            previousHead = predictedHead;
            previousBody = predictedBody;
            double turn = yawVelocity * step;
            double cosine = Math.cos(turn);
            double sine = Math.sin(turn);
            double nextMotionX = motionX * cosine - motionZ * sine;
            motionZ = motionX * sine + motionZ * cosine;
            motionX = nextMotionX;
            yawOffset += turn;
            motionY = MathHelper.clamp(motionY + acceleration * step, -2.0, 2.0);
            x += motionX * step;
            y += motionY * step;
            z += motionZ * step;
            yawVelocity *= Math.pow(0.94, step);
            acceleration *= Math.pow(0.9, step);
        }

        return impact == null && affected.isEmpty()
                ? EMPTY
                : new DragonDivePrediction.Result(
                        impact,
                        impactSurface,
                        impactFace,
                        new ArrayList<>(affected),
                        impact == null ? Collections.emptyList() : path);
    }

    private DragonDivePrediction.Contact findContact(Box previous, Box predicted, BlockPos pos) {
        Vec3d start = new Vec3d(
                (previous.minX + previous.maxX) * 0.5,
                (previous.minY + previous.maxY) * 0.5,
                (previous.minZ + previous.maxZ) * 0.5);
        Vec3d end = new Vec3d(
                (predicted.minX + predicted.maxX) * 0.5,
                (predicted.minY + predicted.maxY) * 0.5,
                (predicted.minZ + predicted.maxZ) * 0.5);
        double movementLengthSq = start.squaredDistanceTo(end);
        if (movementLengthSq < 1.0E-10) {
            return null;
        }

        Box blockBounds =
                new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        Box expanded = blockBounds.grown(
                (predicted.maxX - predicted.minX) * 0.5,
                (predicted.maxY - predicted.minY) * 0.5,
                (predicted.maxZ - predicted.minZ) * 0.5);
        HitResult hit = expanded.clip(start, end);
        if (hit == null) {
            return null;
        }

        Box surface = BlockUtils.getBlockSelectionBox(pos);
        if (surface == null) {
            surface = blockBounds;
        }

        double fraction = Math.sqrt(start.squaredDistanceTo(hit.facePos) / movementLengthSq);
        Box touchingPart = previous.moved(
                (end.x - start.x) * fraction, (end.y - start.y) * fraction, (end.z - start.z) * fraction);
        double minX = MathHelper.clamp(touchingPart.minX, surface.minX, surface.maxX);
        double minY = MathHelper.clamp(touchingPart.minY, surface.minY, surface.maxY);
        double minZ = MathHelper.clamp(touchingPart.minZ, surface.minZ, surface.maxZ);
        double maxX = MathHelper.clamp(touchingPart.maxX, surface.minX, surface.maxX);
        double maxY = MathHelper.clamp(touchingPart.maxY, surface.minY, surface.maxY);
        double maxZ = MathHelper.clamp(touchingPart.maxZ, surface.minZ, surface.maxZ);
        double x = MathHelper.clamp(hit.facePos.x, minX, maxX);
        double y = MathHelper.clamp(hit.facePos.y, minY, maxY);
        double z = MathHelper.clamp(hit.facePos.z, minZ, maxZ);
        switch (hit.face) {
            case WEST:
                maxX = surface.minX;
                minX = surface.minX;
                x = surface.minX - 0.04;
                break;
            case EAST:
                maxX = surface.maxX;
                minX = surface.maxX;
                x = surface.maxX + 0.04;
                break;
            case DOWN:
                maxY = surface.minY;
                minY = surface.minY;
                y = surface.minY - 0.04;
                break;
            case UP:
                maxY = surface.maxY;
                minY = surface.maxY;
                y = surface.maxY + 0.04;
                break;
            case NORTH:
                maxZ = surface.minZ;
                minZ = surface.minZ;
                z = surface.minZ - 0.04;
                break;
            case SOUTH:
                maxZ = surface.maxZ;
                minZ = surface.maxZ;
                z = surface.maxZ + 0.04;
        }

        Box contactSurface = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        return new DragonDivePrediction.Contact(new Vec3d(x, y, z), contactSurface, hit.face, fraction);
    }

    private Box movePart(Box bounds, double x, double y, double z, double yawOffset) {
        double offsetX = (bounds.minX + bounds.maxX) * 0.5 - this.dragon.x;
        double offsetZ = (bounds.minZ + bounds.maxZ) * 0.5 - this.dragon.z;
        double cosine = Math.cos(yawOffset);
        double sine = Math.sin(yawOffset);
        return bounds.moved(
                x - this.dragon.x + offsetX * cosine - offsetZ * sine - offsetX,
                y - this.dragon.y,
                z - this.dragon.z + offsetX * sine + offsetZ * cosine - offsetZ);
    }

    private void collectBlocks(
            Box bounds,
            Set<BlockPos> checked,
            Set<BlockPos> affected,
            Set<BlockPos> contacts,
            boolean canBreakBlocks,
            boolean ignoreBlockProtection) {
        int minY = Math.max(0, MathHelper.floor(bounds.minY));
        int maxY = Math.min(255, MathHelper.floor(bounds.maxY));

        for (int x = MathHelper.floor(bounds.minX); x <= MathHelper.floor(bounds.maxX); x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = MathHelper.floor(bounds.minZ); z <= MathHelper.floor(bounds.maxZ); z++) {
                    if (affected.size() >= 256 || checked.size() >= 8192) {
                        return;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    if (checked.add(pos) && this.dragon.world.isChunkLoaded(pos)) {
                        Block block = this.dragon.world.getBlockState(pos).getBlock();
                        if (block.getMaterial() != Material.AIR) {
                            if (contacts != null) {
                                contacts.add(pos);
                            }

                            if (canBreakBlocks
                                    && (ignoreBlockProtection
                                            || block != Blocks.BARRIER
                                                    && block != Blocks.OBSIDIAN
                                                    && block != Blocks.END_STONE
                                                    && block != Blocks.BEDROCK
                                                    && block != Blocks.COMMAND_BLOCK)) {
                                affected.add(pos);
                            }
                        }
                    }
                }
            }
        }
    }

    private static final class Contact {
        private final Vec3d position;
        private final Box surface;
        private final Direction face;
        private final double fraction;

        private Contact(Vec3d position, Box surface, Direction face, double fraction) {
            this.position = position;
            this.surface = surface;
            this.face = face;
            this.fraction = fraction;
        }
    }

    public static final class Result {
        public final Vec3d impact;
        public final Box impactSurface;
        public final Direction impactFace;
        public final List<BlockPos> blocks;
        public final List<Vec3d> path;

        private Result(Vec3d impact, Box impactSurface, Direction impactFace, List<BlockPos> blocks, List<Vec3d> path) {
            this.impact = impact;
            this.impactSurface = impactSurface;
            this.impactFace = impactFace;
            this.blocks = blocks;
            this.path = path;
        }
    }
}
