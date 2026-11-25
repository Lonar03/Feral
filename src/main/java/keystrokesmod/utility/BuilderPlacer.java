// Ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: module/impl/combat/builder/BuilderPlacer.java + util/RotationUtil.findPlacement/getHitVecs.
// Shared by Head Hitter and Bed Defense: picks a block slot, turns toward the placement with a linear step and only
// right clicks when the rotation that gets sent this tick really hits the block face.
package keystrokesmod.utility;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import keystrokesmod.helper.RotationHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;

public final class BuilderPlacer {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final Random RANDOM = new Random();

    public static final class Placement {
        public final BlockPos pos;
        public final Direction facing;
        public final Vec3d facePos;
        public final float yaw;
        public final float pitch;

        Placement(BlockPos pos, Direction facing, Vec3d facePos, float yaw, float pitch) {
            this.pos = pos;
            this.facing = facing;
            this.facePos = facePos;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public interface SlotPicker {
        /** @return hotbar slot to place with, or -1 */
        int pick();
    }

    private int waitTicks;
    private int originalSlot = -1;
    private boolean rotating;
    private double speed = 60.0;
    public boolean swing = true;
    /** placement found this tick (null = target not reachable/visible) */
    public Placement current;
    /** last block this placer clicked, for rendering */
    public BlockPos lastPlaced;

    /**
     * Called once per tick (PreUpdate) with the block to place, or null when idle.
     *
     * @param waitToPlace Adjust's "Wait to place" (0-3): ticks between placements and a slower turn
     * @return true if a block was clicked this tick
     */
    public boolean tick(BlockPos target, int waitToPlace, SlotPicker slots, float accuracy) {
        return this.tick(target, waitToPlace, slots, accuracy, null);
    }

    /**
     * @param gridYaw when set, rotations whose yaw is a multiple of 45 degrees away from it are preferred
     *                (addition: then the movement fix keeps the exact movement direction instead of drifting sideways)
     */
    public boolean tick(BlockPos target, int waitToPlace, SlotPicker slots, float accuracy, Float gridYaw) {

        int previousWait = this.waitTicks;
        if (this.waitTicks > 0) {
            this.waitTicks--;
        }
        if (target == null) {
            this.idle();
            return false;
        }
        int slot = slots.pick();
        if (slot < 0) {
            this.idle();
            return false;
        }
        float[] server = new float[] {RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]};
        Placement hit = findPlacement(target, server, accuracy, gridYaw);
        this.current = hit;
        if (hit == null) {
            this.idle();
            return false;
        }
        double base;
        switch (waitToPlace) {
            case 1:
                base = 30.0;
                break;
            case 2:
                base = 25.0;
                break;
            case 3:
                base = 20.0;
                break;
            default:
                base = 60.0;
        }
        this.speed = base + 0.01 + RANDOM.nextFloat() * 0.04;
        float[] next = step(server, new float[] {hit.yaw, hit.pitch}, this.speed);
        this.send(next);
        if (previousWait >= 1 && waitToPlace >= 1) {
            return false;
        }
        HitResult traced = rayTrace(mc.player.getEyePosition(1.0F), next[0], next[1]);
        if (traced == null
                || traced.type != HitResult.Type.BLOCK
                || !hit.pos.equals(traced.getPos())
                || hit.facing != traced.face) {
            return false;
        }
        if (!mc.world.isUnobstructed(blockBox(hit.pos.offset(hit.facing)), null)) {
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        // Adjust: interactTicks. Clicking a bed/chest only places while sneaking, and the server has to know that
        // before the click, so sneak one tick first.
        if (BlockUtils.isInteractable(mc.world.getBlockState(hit.pos).getBlock())) {
            this.holdSneak();
            if (!player.isSneaking()) {
                return false;
            }
        } else {
            this.releaseSneak();
        }
        if (player.inventory.selectedSlot != slot) {
            if (this.originalSlot == -1) {
                this.originalSlot = player.inventory.selectedSlot;
            }
            player.inventory.selectedSlot = slot;
        }
        ItemStack held = player.getDisplayItemInHand();
        if (held == null || !(held.getItem() instanceof BlockItem)) {
            return false;
        }
        boolean placed =
                mc.interactionManager.useBlock(player, mc.world, held, traced.getPos(), traced.face, traced.facePos);
        if (placed) {
            if (this.swing) {
                player.swingArm();
            } else {
                mc.getNetworkHandler().sendPacket(new ArmSwingC2SPacket());
            }
            this.lastPlaced = hit.pos.offset(hit.facing);
        }
        this.waitTicks = waitToPlace;
        return placed;
    }

    private boolean pressedSneak;

    private void holdSneak() {
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), true);
        this.pressedSneak = true;
    }

    private void releaseSneak() {
        if (this.pressedSneak) {
            KeyBinding.set(mc.options.sneakKey.getKeyCode(), Utils.isBindDown(mc.options.sneakKey));
            this.pressedSneak = false;
        }
    }

    /** No target: give the slot back and turn back to the camera with the last speed. */
    public void idle() {
        this.current = null;
        this.releaseSneak();
        if (this.originalSlot != -1 && mc.player != null) {
            mc.player.inventory.selectedSlot = this.originalSlot;
            this.originalSlot = -1;
        }
        if (!this.rotating || mc.player == null) {
            return;
        }
        float[] server = new float[] {RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]};
        float[] camera = new float[] {mc.player.yaw, mc.player.pitch};
        if (Math.abs(MathHelper.wrapDegrees(server[0] - camera[0])) < 1.0F && Math.abs(server[1] - camera[1]) < 1.0F) {
            this.rotating = false;
            return;
        }
        this.send(step(server, camera, this.speed));
    }

    public void reset() {
        this.releaseSneak();
        if (this.originalSlot != -1 && mc.player != null) {
            mc.player.inventory.selectedSlot = this.originalSlot;
        }
        this.originalSlot = -1;
        this.rotating = false;
        this.waitTicks = 0;
        RotationHelper.get().forceMovementFix = false;
    }

    public boolean isRotating() {
        return this.rotating;
    }

    private void send(float[] rotation) {
        RotationHelper helper = RotationHelper.get();
        helper.setRotations(rotation[0], RotationUtils.clampPitch(rotation[1]));
        helper.forceMovementFix = true;
        this.rotating = true;
    }

    // Adjust: LinearRotationModel + sensitivity GCD
    private static float[] step(float[] from, float[] to, double speed) {
        float dx = MathHelper.wrapDegrees(to[0] - from[0]);
        float dy = to[1] - from[1];
        double length = Math.sqrt(dx * dx + dy * dy);
        float yaw = to[0];
        float pitch = to[1];
        if (length > speed) {
            yaw = (float) (from[0] + dx / length * speed);
            pitch = (float) (from[1] + dy / length * speed);
        } else {
            yaw = from[0] + dx;
        }
        float sensitivity = mc.options.mouseSensitivity * 0.6F + 0.2F;
        double gcd = (double) sensitivity * sensitivity * sensitivity * 8.0 * 0.15F;
        yaw = (float) (Math.round((yaw - from[0]) / gcd) * gcd + from[0]);
        pitch = (float) (Math.round((pitch - from[1]) / gcd) * gcd + from[1]);
        return new float[] {yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    // ---------------------------------------------------------------- placement search

    /** Adjust: BuilderPlacer.findPlacement + RotationUtil.findPlacement */
    public static Placement findPlacement(BlockPos target, float[] rotation, float accuracy) {
        return findPlacement(target, rotation, accuracy, null);
    }

    public static Placement findPlacement(BlockPos target, float[] rotation, float accuracy, Float gridYaw) {
        if (!mc.world.getBlockState(target).getBlock().canBeReplaced(mc.world, target)) {
            return null;
        }
        for (Entity entity : mc.world.getEntities(null, blockBox(target))) {
            if (!entity.removed && entity.blocksBuilding) {
                return null;
            }
        }
        Vec3d origin = mc.player.getCommandSourcePos();
        Box box = blockBox(target);
        Vec3d closest = new Vec3d(
                MathHelper.clamp(origin.x, box.minX, box.maxX),
                MathHelper.clamp(origin.y, box.minY, box.maxY),
                MathHelper.clamp(origin.z, box.minZ, box.maxZ));
        double reach = mc.interactionManager.getReach();
        if (origin.squaredDistanceTo(closest) >= reach * reach) {
            return null;
        }
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        HitResult initial = rayTrace(eyes, rotation[0], rotation[1]);
        if (isPlacementHit(initial, target, false) && (gridYaw == null || gridOffset(rotation[0], gridYaw) < 2.0F)) {
            return new Placement(initial.getPos(), initial.face, initial.facePos, rotation[0], rotation[1]);
        }
        List<float[]> candidates = new ArrayList<>(); // yaw, pitch, score
        for (Direction facing : Direction.values()) {
            BlockPos neighbor = target.offset(facing);
            BlockState state = mc.world.getBlockState(neighbor);
            Box nbox = state.getBlock().getCollisionShape(mc.world, neighbor, state);
            if (nbox == null) {
                continue;
            }
            sample(eyes, nbox, 0.95, 0.95, accuracy, rotation, gridYaw, candidates);
        }
        candidates.sort(Comparator.comparingDouble(c -> c[2]));
        for (float[] candidate : candidates) {
            HitResult hit = rayTrace(eyes, candidate[0], candidate[1]);
            if (isPlacementHit(hit, target, true)) {
                return new Placement(hit.getPos(), hit.face, hit.facePos, candidate[0], candidate[1]);
            }
        }
        return null;
    }

    private static boolean isPlacementHit(HitResult hit, BlockPos target, boolean allowInteractable) {
        return hit != null
                && hit.type == HitResult.Type.BLOCK
                && hit.getPos().offset(hit.face).equals(target)
                && (allowInteractable
                        || !BlockUtils.isInteractable(
                                mc.world.getBlockState(hit.getPos()).getBlock()));
    }

    private static void sample(
            Vec3d eyes,
            Box box,
            double horizontalFactor,
            double verticalFactor,
            float accuracy,
            float[] rotation,
            Float gridYaw,
            List<float[]> out) {
        double cx = (box.minX + box.maxX) / 2.0;
        double cy = (box.minY + box.maxY) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        double inset = 0.05 + (RANDOM.nextDouble() - 0.5) * 0.02;
        double xRadius = (box.maxX - box.minX) * (horizontalFactor - inset) / 2.0;
        double yRadius = (box.maxY - box.minY) * (verticalFactor - inset) / 2.0;
        double zRadius = (box.maxZ - box.minZ) * (horizontalFactor - inset) / 2.0;
        double xStep = xRadius * 2.0 / accuracy;
        double yStep = yRadius * 2.0 / accuracy;
        double zStep = zRadius * 2.0 / accuracy;
        for (double x = -xRadius; x <= xRadius + 1.0E-6; x += xStep) {
            for (double y = -yRadius; y <= yRadius + 1.0E-6; y += yStep) {
                for (double z = -zRadius; z <= zRadius + 1.0E-6; z += zStep) {
                    double px = cx + x;
                    double py = cy + y;
                    double pz = cz + z;
                    double dx = px - eyes.x;
                    double dy = py - eyes.y;
                    double dz = pz - eyes.z;
                    double horizontal = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI - 90.0);
                    float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
                    float dyaw = MathHelper.wrapDegrees(rotation[0] - yaw);
                    double dpitch = rotation[1] - pitch;
                    double score = dyaw * dyaw + dpitch * dpitch;
                    // prefer points away from the box edges a little (Adjust)
                    for (double rel : new double[] {
                        (px - box.minX) / (box.maxX - box.minX),
                        (py - box.minY) / (box.maxY - box.minY),
                        (pz - box.minZ) / (box.maxZ - box.minZ)
                    }) {
                        double value = Math.abs(rel);
                        if (value != 0.0 && value != 1.0) {
                            score *= 1.0 + (1.0 - Math.abs(0.5 - value) * 2.0) * 0.05;
                        }
                    }
                    if (gridYaw != null) {
                        float off = gridOffset(yaw, gridYaw);
                        score += off * off * 40.0;
                    }
                    out.add(new float[] {yaw, pitch, (float) score});
                }
            }
        }
    }

    /** angular distance of yaw to the nearest multiple of 45 degrees away from base */
    public static float gridOffset(float yaw, float base) {
        float rel = MathHelper.wrapDegrees(yaw - base);
        float snapped = Math.round(rel / 45.0F) * 45.0F;
        return Math.abs(rel - snapped);
    }

    public static HitResult rayTrace(Vec3d eyes, float yaw, float pitch) {
        double reach = mc.interactionManager.getReach();
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        Vec3d end = eyes.add(f1 * f2 * reach, f3 * reach, f * f2 * reach);
        return mc.world.rayTrace(eyes, end, false, false, true);
    }

    public static Box blockBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    // ---------------------------------------------------------------- block slots

    private static boolean isFullBlock(ItemStack stack) {
        if (stack == null || stack.size <= 0 || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FallingBlock
                || block == Blocks.TNT
                || BlockUtils.isInteractable(block)
                || !block.isCube()) {
            return false;
        }
        return block.getMaxY() - block.getMinY() == 1.0
                && block.getMaxX() - block.getMinX() == 1.0
                && block.getMaxZ() - block.getMinZ() == 1.0;
    }

    /** Adjust: hardBlockScore, the hardest full block in the hotbar */
    public static int hardestSlot() {
        int best = -1;
        float highest = -Float.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getItem(i);
            if (!isFullBlock(stack)) {
                continue;
            }
            float hardness = ((BlockItem) stack.getItem()).getBlock().getMiningTime(mc.world, BlockPos.ORIGIN);
            if (hardness > highest) {
                highest = hardness;
                best = i;
            }
        }
        return best;
    }

    /** Adjust: softBlockScore, wool counts as the softest */
    public static int softestSlot() {
        int best = -1;
        float lowest = Float.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getItem(i);
            if (!isFullBlock(stack)) {
                continue;
            }
            Block block = ((BlockItem) stack.getItem()).getBlock();
            float score = block == Blocks.WOOL ? -1.0F : block.getMiningTime(mc.world, BlockPos.ORIGIN);
            if (score < lowest) {
                lowest = score;
                best = i;
            }
        }
        return best;
    }
}
