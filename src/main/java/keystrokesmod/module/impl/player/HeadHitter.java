// Ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: module/impl/combat/builder/feature/headhitter/* (HeadHitterFeature + records) on top of BuilderPlacer.
// While sprint jumping forward it predicts where you land and places a block 2 above the landing spot (built from the
// closest wall/floor in up to 4 blocks), so the next jump hits your head and you keep sprint-jump speed.
package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityLivingBase;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.BuilderPlacer;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.SimulatedPlayer;
import keystrokesmod.utility.Utils;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class HeadHitter extends Module {
    private final KeySetting holdKey;
    private boolean held;
    private final ButtonSetting render;
    private final ButtonSetting swing;
    private final ButtonSetting noJumpDelay;
    private final ButtonSetting gridRotations;

    private final BuilderPlacer placer = new BuilderPlacer();
    private boolean checkedEarlyJump;
    private boolean sprinting;
    private Plan plan;
    private BlockPos target;
    private BlockPos lockedPosition;
    private Direction lockedFacing;
    private int offGroundTicks;

    public HeadHitter() {
        super("Head Hitter", Module.category.player);
        this.registerSetting(new DescriptionSetting("Adjust's head hitter, sprint jump forward"));
        this.registerSetting(new DescriptionSetting("Hold key: only builds while held (none = always)"));
        this.registerSetting(this.holdKey = new KeySetting("Hold key", 0));
        this.registerSetting(this.swing = new ButtonSetting("Clientside swing", true));
        this.registerSetting(this.render = new ButtonSetting("Render", true));
        this.registerSetting(this.noJumpDelay = new ButtonSetting("No jump delay", true));
        this.registerSetting(this.gridRotations = new ButtonSetting("Keep movement direction", true));
    }

    @Override
    public void onEnable() {
        this.resetPlan();
        this.placer.reset();
    }

    @Override
    public void onDisable() {
        this.resetPlan();
        this.placer.reset();
    }

    private void resetPlan() {
        this.sprinting = false;
        this.plan = null;
        this.lockedPosition = null;
        this.lockedFacing = null;
        this.checkedEarlyJump = false;
        this.target = null;
    }

    private boolean isActive() {
        return this.holdKey.getKey() == 0 || (mc.screen == null && this.holdKey.isPressed());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        boolean active = this.isActive();
        if (!active) {
            if (this.held) {
                this.resetPlan();
                this.placer.reset();
                this.held = false;
            }
            this.placer.idle();
            return;
        }
        this.held = true;
        this.offGroundTicks = mc.player.onGround ? 0 : this.offGroundTicks + 1;
        if (ModuleManager.scaffoldAdjust != null && ModuleManager.scaffoldAdjust.isEnabled()) {
            this.placer.idle();
            return;
        }
        this.placer.swing = this.swing.isToggled();
        this.updateBuild();
        Float grid = this.gridRotations.isToggled() ? this.movementYaw() : null;
        boolean placed = this.placer.tick(this.target, 0, BuilderPlacer::hardestSlot, grid != null ? 8.0F : 4.0F, grid);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.screen != null
                || !this.isActive()
                || e.getForward() <= 0.0F && !mc.options.forwardKey.isPressed()
                || !mc.player.isSprinting()) {
            this.resetPlan();
            return;
        }
        this.sprinting = true;
        e.setJump(true);
        if (this.noJumpDelay.isToggled() && mc.player.onGround) {
            // vanilla waits 10 ticks between jumps while jump is held; head hit jumps land after ~3 ticks
            ((IAccessorEntityLivingBase) mc.player).setJumpTicks(0);
        }
        if (mc.player.onGround) {
            this.plan = null;
            this.lockedPosition = null;
            this.lockedFacing = null;
            this.checkedEarlyJump = false;
            this.target = null;
            return;
        }
        if (this.plan != null) {
            return;
        }
        float[] movement = new float[] {Math.signum(e.getForward()), Math.signum(e.getStrafe())};
        if (!this.checkedEarlyJump && (this.offGroundTicks == 1 || this.offGroundTicks == 2)) {
            this.checkedEarlyJump = true;
            Plan candidate = this.predictPlan(movement);
            if (candidate != null && hasRoofSupport(candidate.roof)) {
                this.plan = candidate;
            }
        } else if (this.offGroundTicks >= 3) {
            this.plan = this.predictPlan(movement);
        }
    }

    // ---------------------------------------------------------------- planning

    private void updateBuild() {
        if (!this.sprinting || this.plan == null) {
            this.target = null;
            return;
        }
        if (this.lockedPosition != null && isNotReplaceable(this.lockedPosition)) {
            this.lockedPosition = null;
            this.lockedFacing = null;
        }
        List<Support> supports = this.findSupports(this.plan);
        if (this.lockedPosition != null && this.lockedFacing != null) {
            BlockPos locked = this.lockedPosition.offset(this.lockedFacing);
            supports.removeIf(
                    s -> s.positions.size() >= 2 && !s.positions.get(1).equals(locked));
        }
        if (supports.isEmpty()) {
            this.target = null;
            return;
        }
        float[] server = new float[] {RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]};
        if (this.target != null && !isNotReplaceable(this.target)) {
            for (Support s : supports) {
                if (s.last().equals(this.target) && BuilderPlacer.findPlacement(this.target, server, 4.0F) != null) {
                    return;
                }
            }
        }
        List<Object[]> placements = new ArrayList<>(); // support, rotation difference
        for (Support s : supports) {
            BuilderPlacer.Placement hit = BuilderPlacer.findPlacement(s.last(), server, 4.0F);
            if (hit == null) {
                continue;
            }
            float yaw = MathHelper.wrapDegrees(server[0] - hit.yaw);
            float pitch = server[1] - hit.pitch;
            placements.add(new Object[] {s, (double) (yaw * yaw + pitch * pitch)});
        }
        if (placements.isEmpty()) {
            this.target = null;
            return;
        }
        placements.sort(Comparator.<Object[]>comparingInt(o -> ((Support) o[0]).positions.size())
                .thenComparing(Comparator.<Object[]>comparingDouble(o -> ((Support) o[0]).clearance)
                        .reversed())
                .thenComparingDouble(o -> (Double) o[1])
                .thenComparingInt(o -> ((Support) o[0]).last().getX())
                .thenComparingInt(o -> ((Support) o[0]).last().getY())
                .thenComparingInt(o -> ((Support) o[0]).last().getZ()));
        Support selected = (Support) placements.get(0)[0];
        this.target = selected.last();
        if (this.lockedPosition == null && selected.positions.size() >= 2) {
            BlockPos second = selected.positions.get(1);
            for (Direction facing : Direction.BY_ID_HORIZONTAL) {
                if (this.plan.roof.offset(facing).equals(second)) {
                    this.lockedPosition = this.plan.roof;
                    this.lockedFacing = facing;
                    break;
                }
            }
        }
    }

    private Plan predictPlan(float[] movement) {
        Input input = new Input();
        input.movementForward = movement[0];
        input.movementSideways = movement[1];
        input.jumping = true;
        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(input);
        List<Box> boxes = new ArrayList<>(29);
        boxes.add(sim.getEntityBoundingBox());
        boolean airborne = !sim.onGround;
        boolean landed = false;
        for (int tick = 0; tick < 20; tick++) {
            sim.tick();
            boxes.add(sim.getEntityBoundingBox());
            if (sim.onGround && airborne) {
                landed = true;
                break;
            }
            if (!sim.onGround) {
                airborne = true;
            }
        }
        if (!landed) {
            return null;
        }
        BlockPos landing = new BlockPos(sim.getPos());
        Box landingBox = sim.getEntityBoundingBox();
        sim.tick();
        boxes.add(sim.getEntityBoundingBox());
        Vec3d after = sim.getPos();
        BlockPos roof = new BlockPos(after.x, landing.getY() + 2, after.z);
        for (int tick = 0; tick < 7; tick++) {
            sim.tick();
            boxes.add(sim.getEntityBoundingBox());
        }
        List<Box> movementBoxes = Collections.unmodifiableList(boxes);
        Plan original = new Plan(landing, roof, movementBoxes);
        if (isNotReplaceable(roof)) {
            return original;
        }
        boolean originalFits = !blocksStepUp(roof, landing.getY());
        if (originalFits && !this.findSupports(original).isEmpty()) {
            return original;
        }
        Plan best = null;
        Support bestSupport = null;
        double bestOverlap = 0.0;
        for (int x = MathHelper.floor(landingBox.minX); x <= MathHelper.floor(landingBox.maxX - 1.0E-6); x++) {
            double overlapX = Math.min(landingBox.maxX, x + 1.0) - Math.max(landingBox.minX, x);
            if (overlapX < 0.15) {
                continue;
            }
            for (int z = MathHelper.floor(landingBox.minZ); z <= MathHelper.floor(landingBox.maxZ - 1.0E-6); z++) {
                if (x == roof.getX() && z == roof.getZ()) {
                    continue;
                }
                double overlapZ = Math.min(landingBox.maxZ, z + 1.0) - Math.max(landingBox.minZ, z);
                if (overlapZ < 0.15) {
                    continue;
                }
                if (blocksStepUp(new BlockPos(x, roof.getY(), z), landing.getY())) {
                    continue;
                }
                Plan alternative = new Plan(landing, new BlockPos(x, roof.getY(), z), movementBoxes);
                for (Support s : this.findSupports(alternative)) {
                    double overlap = overlapX * overlapZ;
                    if (bestSupport == null
                            || compareCandidates(s, overlap, alternative, bestSupport, bestOverlap, best) < 0) {
                        best = alternative;
                        bestSupport = s;
                        bestOverlap = overlap;
                    }
                }
            }
        }
        return best != null ? best : originalFits ? original : null;
    }

    // Not in Adjust: a roof 2 above the landing height over (or right next to) a step up (stair, slab, 1-high block)
    // leaves too little headroom to climb it, so on stairs the original jammed itself under its own roof.
    private static boolean blocksStepUp(BlockPos roof, int landingY) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos ground = new BlockPos(roof.getX() + dx, landingY, roof.getZ() + dz);
                if (hasCollision(ground) && !hasCollision(ground.up())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int compareCandidates(
            Support a, double overlapA, Plan planA, Support b, double overlapB, Plan planB) {
        int c = Integer.compare(a.positions.size(), b.positions.size());
        if (c != 0) {
            return c;
        }
        c = Double.compare(b.clearance, a.clearance);
        if (c != 0) {
            return c;
        }
        c = Double.compare(overlapB, overlapA);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(planA.roof.getX(), planB.roof.getX());
        return c != 0 ? c : Integer.compare(planA.roof.getZ(), planB.roof.getZ());
    }

    private List<Support> findSupports(Plan plan) {
        List<Support> result = new ArrayList<>();
        if (isNotReplaceable(plan.roof)) {
            return result;
        }
        List<BlockPos> path = new ArrayList<>(4);
        path.add(plan.roof);
        Set<BlockPos> visited = new HashSet<>();
        visited.add(plan.roof);
        this.searchSupports(plan, plan.roof, path, visited, result);
        return result;
    }

    private void searchSupports(
            Plan plan, BlockPos position, List<BlockPos> path, Set<BlockPos> visited, List<Support> result) {
        for (Direction facing : Direction.values()) {
            if (hasCollision(position.offset(facing))) {
                result.add(new Support(new ArrayList<>(path), clearance(path, plan.movement)));
                break;
            }
        }
        if (path.size() >= 4) {
            return;
        }
        Direction vertical = null;
        for (int i = 1; i < path.size(); i++) {
            int delta = path.get(i).getY() - path.get(i - 1).getY();
            if (delta != 0) {
                vertical = delta > 0 ? Direction.UP : Direction.DOWN;
                break;
            }
        }
        for (Direction facing : Direction.values()) {
            if (facing.getAxis() == Direction.Axis.Y && vertical != null && facing != vertical) {
                continue;
            }
            BlockPos next = position.offset(facing);
            if (next.getY() < plan.landing.getY()
                    || next.getY() > plan.roof.getY()
                    || visited.contains(next)
                    || isNotReplaceable(next)) {
                continue;
            }
            Box block = BuilderPlacer.blockBox(next);
            boolean intersects = false;
            for (Box box : plan.movement) {
                if (box.grown(0.05, 0.05, 0.05).intersects(block)) {
                    intersects = true;
                    break;
                }
            }
            if (intersects) {
                continue;
            }
            visited.add(next);
            path.add(next);
            this.searchSupports(plan, next, path, visited, result);
            path.remove(path.size() - 1);
            visited.remove(next);
        }
    }

    private static double clearance(List<BlockPos> positions, List<Box> movement) {
        if (positions.size() == 1) {
            return 0.0;
        }
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 1; i < positions.size(); i++) {
            Box block = BuilderPlacer.blockBox(positions.get(i));
            for (Box box : movement) {
                double x = Math.max(0.0, Math.max(box.minX - block.maxX, block.minX - box.maxX));
                double y = Math.max(0.0, Math.max(box.minY - block.maxY, block.minY - box.maxY));
                double z = Math.max(0.0, Math.max(box.minZ - block.maxZ, block.minZ - box.maxZ));
                minimum = Math.min(minimum, x * x + y * y + z * z);
            }
        }
        return minimum;
    }

    private static boolean hasRoofSupport(BlockPos roof) {
        for (Direction facing : Direction.BY_ID_HORIZONTAL) {
            BlockPos neighbor = roof.offset(facing);
            if (!hasCollision(neighbor) || !hasCollision(neighbor.up())) {
                continue;
            }
            for (Direction second : Direction.values()) {
                if (second.getAxis() == facing.getAxis()) {
                    continue;
                }
                if (hasCollision(neighbor.offset(second))
                        || hasCollision(neighbor.up().offset(second))) {
                    return true;
                }
            }
        }
        return false;
    }

    private float movementYaw() {
        float yaw = mc.player.yaw;
        float forward =
                (mc.options.forwardKey.isPressed() ? 1.0F : 0.0F) - (mc.options.backKey.isPressed() ? 1.0F : 0.0F);
        float strafe = (mc.options.leftKey.isPressed() ? 1.0F : 0.0F) - (mc.options.rightKey.isPressed() ? 1.0F : 0.0F);
        if (forward < 0.0F) {
            yaw += 180.0F;
        }
        float offset = forward < 0.0F ? -45.0F : forward > 0.0F ? 45.0F : 90.0F;
        if (strafe > 0.0F) {
            yaw -= offset;
        }
        if (strafe < 0.0F) {
            yaw += offset;
        }
        return yaw;
    }

    private static boolean hasCollision(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        Box box = state.getBlock().getCollisionShape(mc.world, pos, state);
        return box != null && box.maxY - box.minY != 0.0;
    }

    private static boolean isNotReplaceable(BlockPos pos) {
        return !mc.world.getBlockState(pos).getBlock().canBeReplaced(mc.world, pos);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || !this.render.isToggled() || this.plan == null) {
            return;
        }
        if (!isNotReplaceable(this.plan.roof)) {
            RenderUtils.renderBlock(this.plan.roof, 0x3CB33636, true, true);
        }
        if (this.target != null && !this.target.equals(this.plan.roof)) {
            RenderUtils.renderBlock(this.target, 0x3CB3B336, true, true);
        }
    }

    private static final class Plan {
        final BlockPos landing;
        final BlockPos roof;
        final List<Box> movement;

        Plan(BlockPos landing, BlockPos roof, List<Box> movement) {
            this.landing = landing;
            this.roof = roof;
            this.movement = movement;
        }
    }

    private static final class Support {
        final List<BlockPos> positions;
        final double clearance;

        Support(List<BlockPos> positions, double clearance) {
            this.positions = positions;
            this.clearance = clearance;
        }

        BlockPos last() {
            return this.positions.get(this.positions.size() - 1);
        }
    }
}
