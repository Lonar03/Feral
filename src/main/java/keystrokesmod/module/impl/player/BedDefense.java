// Ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: module/impl/combat/builder/feature/beddefense/* (BedDefenseFeature, BedAnchor, BedDefensePresets,
// BedDefensePlacement, BedDefenseBlockType) on top of BuilderPlacer. Not ported (yet): "Optimize movement",
// the in-game recorder and the preset editor GUI. Custom presets can be added to keystrokes/beddefense.json.
package keystrokesmod.module.impl.player;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.BuilderPlacer;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlocksUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class BedDefense extends Module {

    // ---------------------------------------------------------------- block types (BedDefenseBlockType)
    private enum BlockType {
        WOOL(Blocks.WOOL),
        GLASS(Blocks.GLASS, Blocks.STAINED_GLASS),
        WOOD(Blocks.PLANKS, Blocks.LOG, Blocks.LOG2),
        END_STONE(Blocks.END_STONE),
        CLAY(Blocks.HARDENED_CLAY, Blocks.STAINED_HARDENED_CLAY),
        OBSIDIAN(Blocks.OBSIDIAN),
        OTHER();

        final Set<Block> blocks;

        BlockType(Block... blocks) {
            this.blocks = new HashSet<>(Arrays.asList(blocks));
        }

        boolean matches(Block block, int blockId) {
            return this == OTHER ? Block.getId(block) == blockId : this.blocks.contains(block);
        }
    }

    /** x = lateral, y = up, z = forward (from the bed half nearest to you toward the other half) */
    private static final class Placement {
        final int x;
        final int y;
        final int z;
        final BlockType type;
        final int blockId;

        Placement(int x, int y, int z, BlockType type, int blockId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.blockId = blockId;
        }
    }

    private static final class Preset {
        final String name;
        final List<Placement> placements;
        ButtonSetting toggle;

        Preset(String name, List<Placement> placements) {
            this.name = name;
            this.placements = placements;
        }
    }

    private static final class Pending {
        final BlockPos pos;
        final BlockType type;
        final int blockId;

        Pending(BlockPos pos, BlockType type, int blockId) {
            this.pos = pos;
            this.type = type;
            this.blockId = blockId;
        }
    }

    // ---------------------------------------------------------------- settings / state
    private final SliderSetting waitToPlace;
    private final ButtonSetting optimizeMovement;
    private final ButtonSetting render;
    private final ButtonSetting swing;
    private final List<Preset> presets = new ArrayList<>();
    private final BuilderPlacer placer = new BuilderPlacer();
    private final Map<BlockPos, Long> brokenAt = new ConcurrentHashMap<>();
    private List<Pending> pending;
    // the block being built this tick and whether it's the spot you're standing in (then jump and place it last)
    private Pending target;
    private boolean standingIn;
    private List<BlockPos> allPositions;
    private String presetName;
    private Vec3d searchOrigin;
    private List<BlockPos> bedCells;
    private long nextSearch;
    // Optimize movement
    private BlockPos movementTarget;
    private int movementTicks;
    private boolean jumping;
    private double jumpDirectionX;
    private double jumpDirectionZ;

    public BedDefense() {
        super("Bed Defense", Module.category.player);
        this.registerSetting(new DescriptionSetting("Covers your bed (Adjust)"));
        this.registerSetting(this.waitToPlace = new SliderSetting("Wait to place", " ticks", 0.0, 0.0, 3.0, 1.0));
        this.registerSetting(this.optimizeMovement = new ButtonSetting("Optimize movement", false));
        this.registerSetting(this.swing = new ButtonSetting("Clientside swing", true));
        this.registerSetting(this.render = new ButtonSetting("Render", true));
        GroupSetting group = new GroupSetting("Defenses");
        this.registerSetting(group);
        for (Preset preset : builtInPresets()) {
            this.presets.add(preset);
        }
        this.presets.addAll(loadCustomPresets());
        for (Preset preset : this.presets) {
            preset.toggle = new ButtonSetting(group, preset.name, true);
            this.registerSetting(preset.toggle);
        }
        this.registerSetting(new ButtonSetting("Open presets file", BedDefense::openPresetsFile));
    }

    @Override
    public String getInfo() {
        return this.presetName == null ? "" : this.presetName;
    }

    @Override
    public void onEnable() {
        this.pending = null;
        this.target = null;
        this.standingIn = false;
        this.allPositions = null;
        this.presetName = null;
        this.nextSearch = 0L;
        this.searchOrigin = mc.player == null ? null : mc.player.getCommandSourcePos();
        this.placer.reset();
    }

    @Override
    public void onDisable() {
        this.pending = null;
        this.target = null;
        this.standingIn = false;
        this.allPositions = null;
        this.presetName = null;
        this.placer.reset();
    }

    // ---------------------------------------------------------------- tick (BedDefenseFeature.updateBuild +
    // BuilderPlacer)

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (mc.interactionManager.isMiningBlock()
                || ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            this.disable();
            return;
        }
        if (ModuleManager.scaffoldAdjust != null && ModuleManager.scaffoldAdjust.isEnabled()) {
            this.placer.idle();
            return;
        }
        if (this.pending == null) {
            if (System.currentTimeMillis() < this.nextSearch) {
                return;
            }
            BlockPos BED = this.findBed();
            if (BED == null) {
                this.nextSearch = System.currentTimeMillis() + 100L;
                return;
            }
            int[] anchor = this.anchor(BED);
            if (anchor == null) {
                Utils.sendMessage("&7[Bed Defense] &cCould not determine your bed orientation.");
                this.disable();
                return;
            }
            Object[] plan = this.selectPlan(anchor);
            if (plan == null) {
                Utils.sendMessage("&7[Bed Defense] &cYou do not have enough materials in your hotbar.");
                this.disable();
                return;
            }
            Preset preset = (Preset) plan[0];
            @SuppressWarnings("unchecked")
            List<Pending> positions = (List<Pending>) plan[1];
            boolean complete = (Boolean) plan[2];
            if (positions.isEmpty()) {
                Utils.sendMessage(
                        complete
                                ? "&7[Bed Defense] &b" + preset.name + " &7is already complete at your bed."
                                : "&7[Bed Defense] &cThe remaining defense positions are obstructed.");
                this.disable();
                return;
            }
            this.presetName = preset.name;
            BlockPos near = new BlockPos(anchor[0], anchor[1], anchor[2]);
            this.bedCells = Arrays.asList(near, near.add(anchor[3], 0, anchor[4]));
            this.pending = positions;
            this.allPositions = new ArrayList<>();
            for (Pending p : positions) {
                this.allPositions.add(p.pos);
            }
        }
        // drop finished blocks (anywhere in the list, spots can get skipped below)
        this.pending.removeIf(p -> p.type.matches(mc.world.getBlockState(p.pos).getBlock(), p.blockId));
        if (this.pending.isEmpty()) {
            Utils.sendMessage("&7[Bed Defense] &7Finished building &b" + this.presetName + " &7at your bed.");
            this.disable();
            return;
        }
        // keep the preset order (it's also the walking order), but skip spots you're standing in, so a block
        // in your own spot gets placed last (jump + place under you)
        Box playerBox = mc.player.getShape();
        Pending firstFree = null;
        for (Pending p : this.pending) {
            if (playerBox.intersects(BuilderPlacer.blockBox(p.pos))) {
                continue;
            }
            if (!mc.world.getBlockState(p.pos).getBlock().canBeReplaced(mc.world, p.pos)) {
                Utils.sendMessage("&7[Bed Defense] &cA defense position got obstructed, stopped.");
                this.disable();
                return;
            }
            firstFree = p;
            break;
        }
        this.standingIn = firstFree == null;
        final Pending next = firstFree != null ? firstFree : this.pending.get(0);
        this.target = next;
        this.placer.swing = this.swing.isToggled();
        boolean placed =
                this.placer.tick(next.pos, (int) this.waitToPlace.getInput(), () -> this.materialSlot(next), 4.0F);
    }

    private int materialSlot(Pending pending) {
        int current = mc.player.inventory.selectedSlot;
        if (this.matches(mc.player.inventory.getItem(current), pending.type, pending.blockId)) {
            return current;
        }
        return Utils.findHotbarSlot(stack -> this.matches(stack, pending.type, pending.blockId));
    }

    private boolean matches(ItemStack stack, BlockType type, int blockId) {
        return stack != null
                && stack.size > 0
                && stack.getItem() instanceof BlockItem
                && type.matches(((BlockItem) stack.getItem()).getBlock(), blockId);
    }

    // ---------------------------------------------------------------- bed + plan

    private BlockPos findBed() {
        BlockPos origin = new BlockPos(mc.player);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = -6; x < 6; x++) {
            for (int y = -6; y < 6; y++) {
                for (int z = -6; z < 6; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() != Blocks.BED) {
                        continue;
                    }
                    double d = pos.squaredDistanceToCenter(mc.player.x, mc.player.y, mc.player.z);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    /** Adjust: BedAnchor.fromBed. @return {originX, originY, originZ, forwardX, forwardZ} */
    private int[] anchor(BlockPos BED) {
        BlockState state = mc.world.getBlockState(BED);
        if (!(state.getBlock() instanceof BedBlock)) {
            return null;
        }
        Direction direction = state.get(BedBlock.FACING);
        if (state.get(BedBlock.PART) == BedBlock.Part.HEAD) {
            direction = direction.getOpposite();
        }
        BlockPos near = BED;
        BlockPos far = BED.offset(direction);
        if (mc.world.getBlockState(far).getBlock() != Blocks.BED) {
            return null;
        }
        Vec3d origin = this.searchOrigin != null ? this.searchOrigin : mc.player.getCommandSourcePos();
        if (near.squaredDistanceToCenter(origin.x, origin.y, origin.z)
                > far.squaredDistanceToCenter(origin.x, origin.y, origin.z)) {
            BlockPos swap = near;
            near = far;
            far = swap;
        }
        return new int[] {near.getX(), near.getY(), near.getZ(), far.getX() - near.getX(), far.getZ() - near.getZ()};
    }

    /** Adjust: BedDefenseFeature.selectPlan. @return {preset, pending list, complete} or null if no materials */
    private Object[] selectPlan(int[] anchor) {
        int fx = anchor[3];
        int fz = anchor[4];
        int lx = -fz;
        int lz = fx;
        Object[] obstructed = null;
        long now = System.currentTimeMillis();
        for (Preset preset : this.presets) {
            if (!preset.toggle.isToggled()) {
                continue;
            }
            int[] remaining = new int[9];
            for (int slot = 0; slot < 9; slot++) {
                ItemStack stack = mc.player.inventory.getItem(slot);
                remaining[slot] = stack == null ? 0 : mc.interactionManager.hasCreativeInventory() ? 256 : stack.size;
            }
            List<Pending> positions = new ArrayList<>();
            boolean complete = true;
            boolean enough = true;
            for (Placement placement : preset.placements) {
                BlockPos pos = new BlockPos(
                        anchor[0] + placement.z * fx + placement.x * lx,
                        anchor[1] + placement.y,
                        anchor[2] + placement.z * fz + placement.x * lz);
                BlockState state = mc.world.getBlockState(pos);
                if (placement.type.matches(state.getBlock(), placement.blockId)) {
                    continue;
                }
                Long broken = this.brokenAt.get(pos);
                if (broken != null && now - broken < 2500L) {
                    complete = false;
                    continue;
                }
                if (!state.getBlock().canBeReplaced(mc.world, pos)) {
                    complete = false;
                    continue;
                }
                int chosen = -1;
                for (int slot = 0; slot < 9; slot++) {
                    if (remaining[slot] >= 1
                            && this.matches(mc.player.inventory.getItem(slot), placement.type, placement.blockId)) {
                        chosen = slot;
                        break;
                    }
                }
                if (chosen == -1) {
                    enough = false;
                    break;
                }
                remaining[chosen]--;
                positions.add(new Pending(pos, placement.type, placement.blockId));
                complete = false;
            }
            if (!enough) {
                continue;
            }
            Object[] plan = new Object[] {preset, positions, complete};
            if (complete || !positions.isEmpty()) {
                return plan;
            }
            if (obstructed == null) {
                obstructed = plan;
            }
        }
        return obstructed;
    }

    // ---------------------------------------------------------------- Optimize movement (BedDefenseFeature.onInput)

    private void setInput(PrePlayerInputEvent e, float[] input) {
        e.setForward(input[0]);
        e.setStrafe(input[1]);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!this.optimizeMovement.isToggled() || isMovementKeyDown()) {
            this.jumping = false;
            this.jumpDirectionX = this.jumpDirectionZ = 0.0;
            return;
        }
        if (this.pending == null || this.pending.isEmpty()) {
            this.movementTarget = null;
            this.movementTicks = 0;
            this.jumping = false;
            return;
        }
        BlockPos pos = this.target != null ? this.target.pos : this.pending.get(0).pos;
        if (this.standingIn && pos.getY() <= (int) Math.floor(mc.player.y + 0.01)) {
            // only your own spot is left: jump in place and place it under you mid-air, or step out of it when there's
            // no room to jump (2 high tunnel)
            Box peak = mc.player.getShape().moved(0.0, 1.25, 0.0);
            if (mc.world.getCollisions(mc.player, peak).isEmpty()) {
                e.setForward(0.0F);
                e.setStrafe(0.0F);
                if (mc.player.onGround) {
                    e.setJump(true);
                }
                return;
            }
            this.stepOut(e, pos);
            return;
        }
        if (!pos.equals(this.movementTarget)) {
            this.movementTarget = pos;
            this.movementTicks = 0;
            this.jumping = false;
            this.jumpDirectionX = this.jumpDirectionZ = 0.0;
        }
        if (this.placer.current != null) {
            this.movementTicks = 0;
            this.jumping = false;
            return;
        }
        if (this.jumping) {
            if (!mc.player.onGround) {
                this.setInput(e, movementInput(this.jumpDirectionX, this.jumpDirectionZ));
                return;
            }
            this.jumping = false;
        }
        if (++this.movementTicks < 2) {
            return;
        }
        Box box = BuilderPlacer.blockBox(pos);
        Vec3d playerPos = mc.player.getCommandSourcePos();
        double x = pos.getX() + 0.5 - playerPos.x;
        double z = pos.getZ() + 0.5 - playerPos.z;
        double horizontal = Math.hypot(x, z);
        if (mc.player.getShape().intersects(box)) {
            // standing in the spot: step out of it
            if (horizontal >= 0.01) {
                this.steer(e, -x, -z);
            } else {
                double yaw = Math.toRadians(mc.player.yaw);
                this.steer(e, Math.sin(yaw), -Math.cos(yaw));
            }
            return;
        }
        Vec3d nearest = new Vec3d(
                MathHelper.clamp(playerPos.x, box.minX, box.maxX),
                MathHelper.clamp(playerPos.y, box.minY, box.maxY),
                MathHelper.clamp(playerPos.z, box.minZ, box.maxZ));
        double squaredDistanceTo = playerPos.squaredDistanceTo(nearest);
        double reach = mc.interactionManager.getReach();
        double nearReach = Math.max(0.0, reach - 0.3);
        double height = pos.getY() + 0.5 - mc.player.getEyePosition(1.0F).y;
        if (height > 0.75 && squaredDistanceTo < reach * reach && mc.player.onGround && canJumpInPlace()) {
            if (horizontal < 0.75) {
                e.setJump(true);
                return;
            }
            if (this.startJump(e, x, z, true)) {
                return;
            }
        }
        if (mc.player.onGround && this.startJump(e, x, z, false)) {
            return;
        }
        if (squaredDistanceTo >= nearReach * nearReach || horizontal > 2.25) {
            this.steer(e, x, z);
            return;
        }
        if (height > 0.75 && mc.player.onGround && canJumpInPlace()) {
            e.setJump(true);
        }
    }

    /** Walk into the nearest free neighbour cell of {@code spot} (not the bed, with a floor at most 3 down). */
    private void stepOut(PrePlayerInputEvent e, BlockPos spot) {
        LocalClientPlayerEntity player = mc.player;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Direction facing : Direction.BY_ID_HORIZONTAL) {
            BlockPos cell = spot.offset(facing);
            if (this.bedCells != null && this.bedCells.contains(cell)) {
                continue;
            }
            Box box = new Box(
                    cell.getX() + 0.2,
                    spot.getY(),
                    cell.getZ() + 0.2,
                    cell.getX() + 0.8,
                    spot.getY() + 1.8,
                    cell.getZ() + 0.8);
            if (!mc.world.getCollisions(player, box).isEmpty()
                    || mc.world
                            .getCollisions(player, box.expanded(0.0, -3.25, 0.0))
                            .isEmpty()) {
                continue;
            }
            double d = player.squaredDistanceTo(cell.getX() + 0.5, player.y, cell.getZ() + 0.5);
            if (d < bestDistance) {
                bestDistance = d;
                best = cell;
            }
        }
        if (best != null) {
            this.steer(e, best.getX() + 0.5 - player.x, best.getZ() + 0.5 - player.z);
        }
    }

    private void steer(PrePlayerInputEvent e, double x, double z) {
        if (!mc.player.onGround) {
            return;
        }
        double length = Math.hypot(x, z);
        if (length < 0.001) {
            return;
        }
        x /= length;
        z /= length;
        float[] input = movementInput(x, z);
        if (!canWalk(input[2], input[3])) {
            if (canJump(input[2], input[3])) {
                this.setInput(e, input);
                e.setJump(true);
                this.jumping = true;
                this.jumpDirectionX = input[2];
                this.jumpDirectionZ = input[3];
                return;
            }
            // try 45 degrees left/right, then 90 degrees
            double c = Math.sqrt(0.5);
            double[][] turns = {{x * c - z * c, x * c + z * c}, {x * c + z * c, -x * c + z * c}, {-z, x}, {z, -x}};
            boolean found = false;
            for (double[] t : turns) {
                input = movementInput(t[0], t[1]);
                if (canWalk(input[2], input[3])) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
        }
        this.setInput(e, input);
    }

    private boolean startJump(PrePlayerInputEvent e, double x, double z, boolean allowWalk) {
        double length = Math.hypot(x, z);
        if (length < 0.001) {
            return false;
        }
        float[] input = movementInput(x / length, z / length);
        boolean jumpable = canJump(input[2], input[3]);
        if (!jumpable && !(allowWalk && canWalk(input[2], input[3]))) {
            return false;
        }
        this.setInput(e, input);
        e.setJump(true);
        this.jumping = true;
        this.jumpDirectionX = input[2];
        this.jumpDirectionZ = input[3];
        return true;
    }

    /** Adjust: movementInput. @return {forward, strafe, directionX, directionZ} relative to the camera yaw */
    private static float[] movementInput(double x, double z) {
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
        float difference = MathHelper.wrapDegrees(yaw - mc.player.yaw);
        float forward;
        float strafe;
        if (Math.abs(difference) <= 70.0F) {
            forward = 1.0F;
            strafe = 0.0F;
        } else if (Math.abs(difference) < 110.0F) {
            forward = 0.0F;
            strafe = difference < 0.0F ? 1.0F : -1.0F;
        } else {
            forward = -1.0F;
            strafe = 0.0F;
        }
        float direction = mc.player.yaw;
        float strafeAngle = 90.0F;
        if (forward < 0.0F) {
            direction += 180.0F;
            strafeAngle = -45.0F;
        } else if (forward > 0.0F) {
            strafeAngle = 45.0F;
        }
        if (strafe > 0.0F) {
            direction -= strafeAngle;
        }
        if (strafe < 0.0F) {
            direction += strafeAngle;
        }
        double radians = Math.toRadians(direction);
        return new float[] {forward, strafe, (float) -Math.sin(radians), (float) Math.cos(radians)};
    }

    // addition: never walk/jump onto the bed, otherwise the defense walls you in
    private boolean blocked(Box box) {
        Box feet = box.grown(0.05, 0.0, 0.05);
        if (this.bedCells != null) {
            for (BlockPos cell : this.bedCells) {
                if (feet.intersects(new Box(
                        cell.getX(), cell.getY(), cell.getZ(), cell.getX() + 1, cell.getY() + 1.5, cell.getZ() + 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean canWalk(double x, double z) {
        LocalClientPlayerEntity player = mc.player;
        Box next = player.getShape().moved(x * 0.35, 0.0, z * 0.35);
        if (this.blocked(next) && !this.blocked(player.getShape())) {
            return false;
        }
        if (!mc.world.getCollisions(player, next).isEmpty()) {
            next = next.moved(0.0, player.stepHeight, 0.0);
            if (!mc.world.getCollisions(player, next).isEmpty()) {
                return false;
            }
        }
        // don't walk off edges higher than 3 blocks (no fall damage up to 3), Adjust only allowed 0.25
        return !mc.world.getCollisions(player, next.expanded(0.0, -3.25, 0.0)).isEmpty();
    }

    private boolean canJump(double x, double z) {
        LocalClientPlayerEntity player = mc.player;
        Box original = player.getShape();
        Box next = original.moved(x * 0.35, 0.0, z * 0.35);
        if (this.blocked(next.moved(0.0, 1.0, 0.0)) && !this.blocked(original)) {
            return false;
        }
        List<Box> collisions = mc.world.getCollisions(player, next);
        if (collisions.isEmpty()) {
            return false;
        }
        double highest = Double.NEGATIVE_INFINITY;
        for (Box collision : collisions) {
            highest = Math.max(highest, collision.maxY);
        }
        double height = highest - original.minY;
        if (player.stepHeight >= height || height > 1.05) {
            return false;
        }
        double rise = height + 0.01;
        if (!mc.world.getCollisions(player, original.moved(0.0, rise, 0.0)).isEmpty()) {
            return false;
        }
        Box raised = next.moved(0.0, rise, 0.0);
        if (!mc.world.getCollisions(player, raised).isEmpty()) {
            return false;
        }
        return !mc.world.getCollisions(player, raised.moved(0.0, -0.09, 0.0)).isEmpty();
    }

    private static boolean canJumpInPlace() {
        Box raised = mc.player.getShape().moved(0.0, 0.42, 0.0);
        return mc.world.getCollisions(mc.player, raised).isEmpty();
    }

    private static boolean isMovementKeyDown() {
        return mc.options.forwardKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    // blocks broken near you in the last 2.5 s are skipped (Adjust: ServerTracker.bedTimers)
    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof BlockUpdateS2CPacket) {
            BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) e.getPacket();
            if (packet.getBlockState().getBlock() == Blocks.AIR) {
                this.brokenAt.put(packet.getPos(), System.currentTimeMillis());
            }
        } else if (e.getPacket() instanceof BlocksUpdateS2CPacket) {
            for (BlocksUpdateS2CPacket.BlockUpdate data : ((BlocksUpdateS2CPacket) e.getPacket()).getUpdates()) {
                if (data.getBlockState().getBlock() == Blocks.AIR) {
                    this.brokenAt.put(data.getBlockPos(), System.currentTimeMillis());
                }
            }
        }
        if (this.brokenAt.size() > 512) {
            long now = System.currentTimeMillis();
            this.brokenAt.values().removeIf(t -> now - t > 2500L);
        }
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || !this.render.isToggled() || this.pending == null) {
            return;
        }
        for (Pending p : this.pending) {
            RenderUtils.renderBlock(p.pos, 0x3CB33636, true, true);
        }
    }

    // ---------------------------------------------------------------- presets

    private static Placement p(int x, int y, int z, BlockType type) {
        int id;
        switch (type) {
            case OBSIDIAN:
                id = 49;
                break;
            case WOOD:
                id = 5;
                break;
            case GLASS:
                id = 20;
                break;
            case END_STONE:
                id = 121;
                break;
            case CLAY:
                id = 172;
                break;
            default:
                id = 35;
        }
        return new Placement(x, y, z, type, id);
    }

    private static List<Preset> builtInPresets() {
        BlockType O = BlockType.OBSIDIAN;
        BlockType W = BlockType.WOOL;
        BlockType D = BlockType.WOOD;
        BlockType G = BlockType.GLASS;
        BlockType E = BlockType.END_STONE;
        List<Preset> list = new ArrayList<>();
        list.add(new Preset(
                "Obsidian",
                Arrays.asList(
                        p(0, 0, -1, O),
                        p(0, 0, 2, O),
                        p(-1, 0, 1, O),
                        p(-1, 0, 0, O),
                        p(1, 0, 1, O),
                        p(1, 0, 0, O),
                        p(0, 1, 1, O),
                        p(0, 1, 0, O))));
        list.add(new Preset(
                "End Glass Wood",
                Arrays.asList(
                        p(0, 0, -3, D),
                        p(0, 0, -2, G),
                        p(0, 0, -1, E),
                        p(2, 0, 2, D),
                        p(1, 0, 3, D),
                        p(0, 0, 4, D),
                        p(-1, 0, 3, D),
                        p(-2, 0, 2, D),
                        p(-1, 0, 2, G),
                        p(0, 0, 3, G),
                        p(1, 0, 2, G),
                        p(1, 1, 2, D),
                        p(0, 1, 3, D),
                        p(-1, 1, 2, D),
                        p(0, 0, 2, E),
                        p(0, 1, 2, G),
                        p(0, 2, 2, D),
                        p(-3, 0, 1, D),
                        p(-3, 0, 0, D),
                        p(-2, 0, 1, G),
                        p(-2, 0, 0, G),
                        p(-1, 0, 1, E),
                        p(-1, 0, 0, E),
                        p(-2, 1, 1, D),
                        p(-2, 1, 0, D),
                        p(-1, 1, 1, G),
                        p(-1, 1, 0, G),
                        p(-1, 2, 1, D),
                        p(-1, 2, 0, D),
                        p(3, 0, 1, D),
                        p(3, 0, 0, D),
                        p(2, 0, 1, G),
                        p(2, 0, 0, G),
                        p(1, 0, 1, E),
                        p(1, 0, 0, E),
                        p(2, 1, 1, D),
                        p(2, 1, 0, D),
                        p(1, 1, 1, G),
                        p(1, 1, 0, G),
                        p(1, 2, 1, D),
                        p(1, 2, 0, D),
                        p(0, 1, 1, E),
                        p(0, 1, 0, E),
                        p(0, 2, 1, G),
                        p(0, 2, 0, G),
                        p(0, 3, 1, D),
                        p(0, 3, 0, D),
                        p(-2, 0, -1, D),
                        p(-1, 0, -2, D),
                        p(-1, 0, -1, G),
                        p(-1, 1, -1, D),
                        p(2, 0, -1, D),
                        p(1, 0, -2, D),
                        p(1, 0, -1, G),
                        p(1, 1, -1, D),
                        p(0, 1, -1, G),
                        p(0, 2, -1, D),
                        p(0, 1, -2, D))));
        list.add(new Preset(
                "End Wool",
                Arrays.asList(
                        p(0, 0, -2, W),
                        p(0, 0, -1, E),
                        p(2, 0, 2, W),
                        p(1, 0, 3, W),
                        p(0, 0, 4, W),
                        p(-1, 0, 3, W),
                        p(-2, 0, 2, W),
                        p(-1, 0, 2, E),
                        p(0, 0, 3, E),
                        p(1, 0, 2, E),
                        p(0, 0, 2, E),
                        p(-1, 1, 2, W),
                        p(0, 1, 3, W),
                        p(1, 1, 2, W),
                        p(0, 1, 2, E),
                        p(-2, 0, 1, W),
                        p(-2, 0, 0, W),
                        p(-1, 0, 1, E),
                        p(-1, 0, 0, E),
                        p(-1, 1, 1, W),
                        p(-1, 1, 0, W),
                        p(2, 0, 1, W),
                        p(2, 0, 0, W),
                        p(1, 0, 1, E),
                        p(1, 0, 0, E),
                        p(1, 1, 1, W),
                        p(1, 1, 0, W),
                        p(0, 1, 1, E),
                        p(0, 1, 0, E),
                        p(0, 2, 2, W),
                        p(0, 2, 1, W),
                        p(0, 2, 0, W),
                        p(-1, 0, -1, W),
                        p(0, 1, -1, W),
                        p(1, 0, -1, W))));
        list.add(new Preset(
                "Wood Wool",
                Arrays.asList(
                        p(-1, 0, -1, D),
                        p(0, 0, -1, D),
                        p(1, 0, -1, D),
                        p(0, 0, -2, D),
                        p(1, 0, 2, D),
                        p(0, 0, 3, D),
                        p(-1, 0, 2, D),
                        p(0, 0, 2, D),
                        p(2, 0, 1, W),
                        p(2, 0, 0, W),
                        p(1, 0, 1, D),
                        p(1, 0, 0, D),
                        p(1, 1, 0, W),
                        p(1, 1, 1, W),
                        p(0, 1, 2, D),
                        p(-2, 0, 1, W),
                        p(-2, 0, 0, W),
                        p(-1, 0, 1, D),
                        p(-1, 0, 0, D),
                        p(-1, 1, 1, W),
                        p(-1, 1, 0, W),
                        p(0, 1, 1, D),
                        p(0, 1, 0, D),
                        p(0, 1, -1, D),
                        p(0, 2, 1, W),
                        p(0, 2, 0, W))));
        list.add(new Preset(
                "Wool",
                Arrays.asList(
                        p(0, 0, -1, W),
                        p(0, 0, 2, W),
                        p(-1, 0, 1, W),
                        p(-1, 0, 0, W),
                        p(1, 0, 1, W),
                        p(1, 0, 0, W),
                        p(0, 1, 1, W),
                        p(0, 1, 0, W))));
        return list;
    }

    private static File presetsFile() {
        return new File(mc.gameDir + File.separator + "keystrokes", "beddefense.json");
    }

    /**
     * keystrokes/beddefense.json: [{"name": "My defense", "placements": [[x, y, z, "WOOL"], [x, y, z, 49]]}]
     * x = right/left of the bed, y = up, z = along the bed (0 = the half nearest to you, 1 = the other half).
     * The 4th value is a type (WOOL, GLASS, WOOD, END_STONE, CLAY, OBSIDIAN) or a block id.
     */
    private static List<Preset> loadCustomPresets() {
        List<Preset> result = new ArrayList<>();
        File file = presetsFile();
        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(file)) {
                    writer.write(
                            "[\n  {\"name\": \"Example (disable me)\", \"placements\": "
                                    + "[[0, 0, -1, \"WOOL\"], [0, 0, 2, \"WOOL\"], [-1, 0, 0, \"WOOL\"], [-1, 0, 1, \"WOOL\"], "
                                    + "[1, 0, 0, \"WOOL\"], [1, 0, 1, \"WOOL\"], [0, 1, 0, \"WOOL\"], [0, 1, 1, \"WOOL\"]]}\n]\n");
                }
            }
            try (FileReader reader = new FileReader(file)) {
                JsonArray array = new JsonParser().parse(reader).getAsJsonArray();
                for (JsonElement element : array) {
                    JsonObject object = element.getAsJsonObject();
                    String name = object.get("name").getAsString();
                    List<Placement> placements = new ArrayList<>();
                    for (JsonElement p : object.getAsJsonArray("placements")) {
                        JsonArray a = p.getAsJsonArray();
                        JsonElement kind = a.get(3);
                        BlockType type;
                        int id;
                        if (kind.getAsJsonPrimitive().isNumber()) {
                            type = BlockType.OTHER;
                            id = kind.getAsInt();
                        } else {
                            type = BlockType.valueOf(kind.getAsString().toUpperCase());
                            id = p(0, 0, 0, type).blockId;
                        }
                        placements.add(new Placement(
                                a.get(0).getAsInt(),
                                a.get(1).getAsInt(),
                                a.get(2).getAsInt(),
                                type,
                                id));
                    }
                    if (!placements.isEmpty()) {
                        result.add(new Preset(name, placements));
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[Bed Defense] could not load " + file + ": " + ex);
        }
        return result;
    }

    private static void openPresetsFile() {
        try {
            java.awt.Desktop.getDesktop().open(presetsFile());
        } catch (Exception ex) {
            Utils.sendMessage("&7[Bed Defense] &cCould not open &7"
                    + presetsFile().getAbsolutePath() + " &c(edit it and restart the game to load new presets)");
        }
    }
}
