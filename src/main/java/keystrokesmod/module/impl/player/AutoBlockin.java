package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class AutoBlockin extends Module {
    private static final Direction[] HORIZONTALS =
            new Direction[] {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    private static final AutoBlockin.SupportOffset[] SUPPORTS = new AutoBlockin.SupportOffset[] {
        new AutoBlockin.SupportOffset(0, 1, 0, Direction.DOWN),
        new AutoBlockin.SupportOffset(0, -1, 0, Direction.UP),
        new AutoBlockin.SupportOffset(0, 0, -1, Direction.SOUTH),
        new AutoBlockin.SupportOffset(0, 0, 1, Direction.NORTH),
        new AutoBlockin.SupportOffset(1, 0, 0, Direction.WEST),
        new AutoBlockin.SupportOffset(-1, 0, 0, Direction.EAST)
    };
    private static final double REACH = 4.5;
    private static final double GRID_INSET = 0.05;
    private static final double GRID_STEP = 0.2;
    private static final int GRID_N = (int) Math.round(5.0);
    private final SliderSetting speed;
    private final SliderSetting randomization;
    private final SliderSetting rotationTol;
    private final SliderSetting showProgress;
    private final ButtonSetting disableInCreative;
    private final ButtonSetting skipNearBed;
    private final KeySetting activationKey;
    private final ButtonSetting ignoreBlocksToggle;
    private final ItemListSetting ignoredBlocks;
    private boolean placing;
    private boolean slotWasSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private boolean placeQueued;
    private BlockPos targetHitPos;
    private Direction targetSide;
    private float aimYaw;
    private float aimPitch;
    private BlockPos hitAt;
    private Direction hitSide;
    private Vec3d placeAt;
    private float fillCount;
    private float lastFillCount = -1.0F;
    private float circleProgress;
    private float animStartProgress;
    private float animTargetProgress;
    private long animStartTime;
    private Timer progressFadeTimer;
    private Timer progressFadeInTimer;
    private float previousProgressAlpha;
    private boolean lastTargetAdjacent;
    private float fillTargetCount;
    private float lastFillTargetCount = -1.0F;

    public AutoBlockin() {
        super("AutoBlockin", Module.category.player);
        this.liteModule = true;
        this.registerSetting(this.speed = new SliderSetting("Speed", 10.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.randomization = new SliderSetting("Randomization", "%", 10.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.rotationTol = new SliderSetting("Rotation tolerance", "°", 25.0, 20.0, 100.0, 1.0));
        this.registerSetting(
                this.showProgress = new SliderSetting("Show progress", true, 0, new String[] {"Circle", "Percentage"}));
        this.registerSetting(this.disableInCreative = new ButtonSetting("Disable in creative", true));
        this.registerSetting(this.skipNearBed = new ButtonSetting("Skip near bed", true));
        this.registerSetting(this.activationKey = new KeySetting("Activation key", 0));
        this.registerSetting(this.ignoreBlocksToggle = new ButtonSetting("Ignore blocks", false));
        this.registerSetting(this.ignoredBlocks = new ItemListSetting("Items"));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        this.disablePlacing();
        this.placeQueued = false;
        this.fillCount = 0.0F;
        this.fillTargetCount = 0.0F;
        this.lastFillCount = -1.0F;
        this.lastFillTargetCount = -1.0F;
        this.circleProgress = 0.0F;
        this.resetProgressFade();
    }

    @Override
    public void guiUpdate() {
        this.ignoredBlocks.setVisible(this.ignoreBlocksToggle.isToggled(), this);
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (Utils.nullCheck()) {
            if (!this.disableInCreative.isToggled() || !mc.player.abilities.creativeMode) {
                if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
                    this.runTargetSelection();
                    if (mc.screen != null) {
                        this.disablePlacing();
                    }

                    if (this.placing && this.targetHitPos != null) {
                        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                        float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                        float[] sm = RotationUtils.smoothRotation(
                                baseYaw, basePitch, this.aimYaw, this.aimPitch, (int) this.speed.getInput(), (float)
                                        this.randomization.getInput());
                        double r = 4.5;
                        HitResult mop = RotationUtils.rayCastBlock(
                                r, RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
                        if (mop != null) {
                            BlockPos hitBlock = mop.getPos();
                            Direction side = mop.face;
                            if (hitBlock.equals(this.targetHitPos) && side == this.targetSide) {
                                double tol = this.rotationTol.getInput();
                                if (Math.abs(MathHelper.wrapDegrees(sm[0] - RotationUtils.serverRotations[0])) <= tol
                                        && Math.abs(sm[1] - RotationUtils.serverRotations[1]) <= tol) {
                                    this.hitAt = hitBlock;
                                    this.hitSide = side;
                                    this.placeAt = mop.facePos;
                                    this.placeQueued = true;
                                }
                            }
                        }

                        RotationHelper.get().forceMovementFix = true;
                        e.setYaw(sm[0]);
                        e.setPitch(sm[1]);
                    }
                }
            }
        }
    }

    private void runTargetSelection() {
        this.clearAim();
        if (this.activationKey.isPressed() && mc.screen == null) {
            int strongSlot = this.pickBlockSlot(true);
            int weakSlot = this.pickBlockSlot(false);
            if (strongSlot == -1 && weakSlot == -1) {
                this.disablePlacing();
            } else {
                this.plannedSlot = strongSlot != -1 ? strongSlot : weakSlot;
                if (!this.getTarget()) {
                    this.disablePlacing();
                } else {
                    if (this.lastTargetAdjacent) {
                        this.plannedSlot = strongSlot != -1 ? strongSlot : weakSlot;
                    } else {
                        this.plannedSlot = weakSlot != -1 ? weakSlot : strongSlot;
                    }

                    if (!this.placing) {
                        this.enablePlacing();
                    }

                    if (mc.options.attackKey.isPressed() || mc.options.useKey.isPressed()) {
                        this.clearAim();
                    }

                    KeyBinding.set(mc.options.attackKey.getKeyCode(), false);
                    KeyBinding.set(mc.options.useKey.getKeyCode(), false);
                    this.equipPlannedSlot();
                }
            }
        } else {
            this.disablePlacing();
            this.circleProgress = 0.0F;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (Utils.nullCheck()) {
            if (this.placeQueued) {
                this.placeQueued = false;
                if (this.hitAt != null && this.hitSide != null && this.placeAt != null) {
                    BlockPos placementPos = this.hitAt.offset(this.hitSide);
                    if (!this.isNearBed(placementPos)
                            && mc.interactionManager.useBlock(
                                    mc.player,
                                    mc.world,
                                    mc.player.getDisplayItemInHand(),
                                    this.hitAt,
                                    this.hitSide,
                                    this.placeAt)) {
                        mc.player.swingArm();
                    }
                }
            }

            this.fillCount = 0.0F;
            this.fillTargetCount = 0.0F;
            if (this.activationKey.isPressed() && mc.screen == null) {
                BlockPos feet = new BlockPos(
                        MathHelper.floor(mc.player.x), MathHelper.floor(mc.player.y), MathHelper.floor(mc.player.z));
                this.countProgressPosition(feet.up().up());

                for (Direction dir : HORIZONTALS) {
                    BlockPos lowerSide = feet.offset(dir);
                    this.countProgressPosition(lowerSide);
                    this.countProgressPosition(lowerSide.up());
                }

                if (this.fillCount != this.lastFillCount || this.fillTargetCount != this.lastFillTargetCount) {
                    this.animStartProgress = this.circleProgress;
                    if (this.fillTargetCount <= 0.0F) {
                        this.animTargetProgress = 0.0F;
                    } else {
                        this.animTargetProgress = Math.max(0.0F, Math.min(1.0F, this.fillCount / this.fillTargetCount));
                    }

                    this.animStartTime = System.currentTimeMillis();
                    this.lastFillCount = this.fillCount;
                    this.lastFillTargetCount = this.fillTargetCount;
                }
            } else {
                this.lastFillCount = -1.0F;
                this.lastFillTargetCount = -1.0F;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            int progressMode = (int) this.showProgress.getInput();
            if (mc.screen != null || progressMode == -1) {
                this.resetProgressFade();
            } else if (this.disableInCreative.isToggled() && mc.player.abilities.creativeMode) {
                this.resetProgressFade();
            } else {
                long elapsed = System.currentTimeMillis() - this.animStartTime;
                if (elapsed < 50L) {
                    float t = (float) elapsed / 50.0F;
                    this.circleProgress = lerp(this.animStartProgress, this.animTargetProgress, quadInOutEasing(t));
                } else {
                    this.circleProgress = this.animTargetProgress;
                }

                boolean hasValidProgress = this.fillTargetCount > 0.0F && this.fillCount > 0.0F;
                int alpha = this.updateProgressAlpha(hasValidProgress);
                if (alpha > 10) {
                    float ratio = Math.max(0.0F, Math.min(1.0F, this.circleProgress));
                    if (progressMode == 1) {
                        this.renderPercentage(ratio, alpha);
                    } else {
                        this.renderCircleProgress(ratio, alpha);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (this.placing && e.button > -1) {
            e.setCanceled(true);
        }
    }

    private void enablePlacing() {
        if (!this.placing) {
            this.placing = true;
            this.slotWasSwapped = false;
            this.prevSlot = mc.player.inventory.selectedSlot;
        }
    }

    private int updateProgressAlpha(boolean shouldShow) {
        if (shouldShow) {
            if (this.progressFadeTimer != null) {
                this.progressFadeTimer = null;
                this.progressFadeInTimer = new Timer(150.0F);
                this.progressFadeInTimer.start();
            } else if (this.progressFadeInTimer == null && this.previousProgressAlpha < 255.0F) {
                this.progressFadeInTimer = new Timer(150.0F);
                this.progressFadeInTimer.start();
            }

            float alpha;
            if (this.progressFadeInTimer != null) {
                alpha = this.progressFadeInTimer.getValueFloat(10.0F, 255.0F, 1);
                if (alpha >= 255.0F) {
                    alpha = 255.0F;
                    this.progressFadeInTimer = null;
                }
            } else {
                alpha = 255.0F;
            }

            this.previousProgressAlpha = alpha;
            return (int) alpha;
        } else {
            if (this.previousProgressAlpha <= 10.0F) {
                this.resetProgressFade();
                return 0;
            }

            if (this.progressFadeTimer == null) {
                this.progressFadeTimer = new Timer(150.0F);
                this.progressFadeTimer.start();
                this.progressFadeInTimer = null;
            }

            float alpha = 255.0F - this.progressFadeTimer.getValueInt(0, 255, 1);
            if (alpha <= 10.0F) {
                this.resetProgressFade();
                return 0;
            } else {
                this.previousProgressAlpha = alpha;
                return (int) alpha;
            }
        }
    }

    private void renderCircleProgress(float ratio, int alpha) {
        Window resolution = new Window(mc);
        float centerX = resolution.getWidth() / 2.0F + 0.5F;
        float centerY = resolution.getHeight() / 2.0F + 0.5F;
        float radius = 10.0F;
        float thickness = 3.0F;
        float alphaFloat = alpha / 255.0F;
        RenderUtils.draw2DCircle(centerX, centerY, radius, 100, thickness, 0.0F, 0.0F, 0.0F, alphaFloat * 0.5F);
        if (ratio >= 0.999F) {
            RenderUtils.draw2DCircle(centerX, centerY, radius, 100, thickness, 0.0F, 1.0F, 0.0F, alphaFloat);
        } else {
            int red = (int) ((1.0F - ratio) * 255.0F + 0.5F);
            int green = (int) (ratio * 255.0F + 0.5F);
            int color = (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8;
            float startAngle = 90.0F;
            float endAngle = startAngle + ratio * 360.0F + 0.5F;
            RenderUtils.draw2DCircleArc(centerX, centerY, radius, startAngle, endAngle, thickness, color);
        }
    }

    private void renderPercentage(float ratio, int alpha) {
        Window resolution = new Window(mc);
        String text = Math.round(ratio * 100.0F) + "%";
        float x = resolution.getWidth() / 2.0F - mc.textRenderer.getWidth(text) / 2.0F;
        float y = resolution.getHeight() / 2.0F + 12.0F;
        int color = Utils.mergeAlpha(ratio >= 0.999F ? 65280 : 16777215, alpha);
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        mc.textRenderer.drawWithShadow(text, x, y, color);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    private void resetProgressFade() {
        this.progressFadeTimer = null;
        this.progressFadeInTimer = null;
        this.previousProgressAlpha = 0.0F;
    }

    private void disablePlacing() {
        if (this.placing) {
            if (this.slotWasSwapped && this.prevSlot != -1 && this.prevSlot != mc.player.inventory.selectedSlot) {
                mc.player.inventory.selectedSlot = this.prevSlot;
            }

            this.placing = false;
            this.slotWasSwapped = false;
            this.prevSlot = -1;
            this.plannedSlot = -1;
            if (mc.screen == null) {
                KeyBinding.set(mc.options.attackKey.getKeyCode(), Mouse.isButtonDown(0));
                KeyBinding.set(mc.options.useKey.getKeyCode(), Mouse.isButtonDown(1));
            }
        }
    }

    private void clearAim() {
        this.targetHitPos = null;
        this.targetSide = null;
    }

    private void equipPlannedSlot() {
        int cur = mc.player.inventory.selectedSlot;
        if (this.plannedSlot != -1 && this.plannedSlot != cur) {
            mc.player.inventory.selectedSlot = this.plannedSlot;
            this.slotWasSwapped = true;
        }
    }

    private int pickBlockSlot(boolean preferStrong) {
        int best = -1;
        float bestScore = preferStrong ? -1.0F : Float.MAX_VALUE;

        for (int slot = 8; slot >= 0; slot--) {
            ItemStack s = mc.player.inventory.items[slot];
            if (s != null
                    && s.size != 0
                    && s.getItem() instanceof BlockItem
                    && (!this.ignoreBlocksToggle.isToggled() || !this.ignoredBlocks.matches(s))) {
                Block block = ((BlockItem) s.getItem()).getBlock();
                if (!(block instanceof LadderBlock)) {
                    float score = BlockUtils.getFistBreakTicks(block);
                    if (preferStrong ? score > bestScore : score < bestScore) {
                        bestScore = score;
                        best = slot;
                    }
                }
            }
        }

        return best;
    }

    private boolean getTarget() {
        AutoBlockin.AimResult result = this.roofAim();
        if (result == null) {
            result = this.sidesAim();
        }

        if (result == null) {
            return false;
        }

        BlockPos placed = result.supportBlock.offset(result.face);
        this.lastTargetAdjacent = this.isDirectAdjacentPlacement(placed);
        this.targetHitPos = result.supportBlock;
        this.targetSide = result.face;
        this.aimYaw = result.yaw;
        this.aimPitch = result.pitch;
        return true;
    }

    private AutoBlockin.AimResult roofAim() {
        Vec3d pos = new Vec3d(mc.player.x, mc.player.y, mc.player.z);
        BlockPos aboveHead =
                new BlockPos(MathHelper.floor(pos.x), MathHelper.floor(pos.y) + 2, MathHelper.floor(pos.z));
        if (!BlockUtils.replaceable(aboveHead)) {
            return null;
        }

        if (this.plannedSlot >= 0 && this.plannedSlot <= 8) {
            ItemStack held = mc.player.inventory.items[this.plannedSlot];
            double r = 4.5;
            Vec3d eye = new Vec3d(pos.x, pos.y + mc.player.getEyeHeight(), pos.z);
            double r2 = r * r;
            double rp12 = (r + 1.0) * (r + 1.0);
            int minY = MathHelper.floor(eye.y) + 1;
            int maxY = MathHelper.floor(eye.y + r);
            int minX = MathHelper.floor(eye.x - r);
            int maxX = MathHelper.floor(eye.x + r);
            int minZ = MathHelper.floor(eye.z - r);
            int maxZ = MathHelper.floor(eye.z + r);
            ArrayList<AutoBlockin.BlockCandidate> cands = new ArrayList<>();

            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double dx = x + 0.5 - eye.x;
                        double dy = y + 0.5 - eye.y;
                        double dz = z + 0.5 - eye.z;
                        if (!(dx * dx + dy * dy + dz * dz > rp12)) {
                            BlockPos bp = new BlockPos(x, y, z);
                            if (!BlockUtils.replaceable(bp)) {
                                Block block = BlockUtils.getBlock(bp);
                                if (!BlockUtils.isInteractable(block)
                                        && !(block instanceof FenceBlock)
                                        && !(block instanceof WallBlock)) {
                                    double d2 = BlockUtils.dist2PointAABB(eye, bp);
                                    if (!(d2 > r2)) {
                                        cands.add(new AutoBlockin.BlockCandidate(d2, bp));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            cands.sort((a, b) -> Double.compare(a.dist, b.dist));

            for (AutoBlockin.BlockCandidate cand : cands) {
                AutoBlockin.AimResult res = this.getBestRotationsToBlock(held, cand.pos, eye, r, minY);
                if (res != null) {
                    return res;
                }
            }

            return null;
        } else {
            return null;
        }
    }

    private AutoBlockin.AimResult getBestRotationsToBlock(
            ItemStack held, BlockPos targetCell, Vec3d eye, double reachVal, int minY) {
        float baseYaw = RotationUtils.serverRotations[0];
        float basePitch = RotationUtils.serverRotations[1];
        boolean faceUp = Math.abs(eye.y - (targetCell.getY() + 1)) < Math.abs(eye.y - targetCell.getY());
        boolean faceSouth = Math.abs(eye.z - (targetCell.getZ() + 1)) < Math.abs(eye.z - targetCell.getZ());
        boolean faceEast = Math.abs(eye.x - (targetCell.getX() + 1)) < Math.abs(eye.x - targetCell.getX());
        double bx = targetCell.getX();
        double by = targetCell.getY();
        double bz = targetCell.getZ();
        double jit = 0.020000000000000004;
        ArrayList<AutoBlockin.RotationCandidate> cands = new ArrayList<>((GRID_N + 1) * (GRID_N + 1) * 3 + 1);
        cands.add(new AutoBlockin.RotationCandidate(0.0, baseYaw, basePitch));

        for (int row = 0; row <= GRID_N; row++) {
            double v = clamp01(row * 0.2 + jitter(jit));

            for (int col = 0; col <= GRID_N; col++) {
                double u = clamp01(col * 0.2 + jitter(jit));
                float[] rY =
                        RotationUtils.getRotationsFromEye(eye, bx + u, faceUp ? by + 1.0 - 0.05 : by + 0.05, bz + v);
                cands.add(new AutoBlockin.RotationCandidate(
                        Math.abs(MathHelper.wrapDegrees(rY[0] - baseYaw)) + Math.abs(rY[1] - basePitch), rY[0], rY[1]));
                float[] rZ =
                        RotationUtils.getRotationsFromEye(eye, bx + u, by + v, faceSouth ? bz + 1.0 - 0.05 : bz + 0.05);
                cands.add(new AutoBlockin.RotationCandidate(
                        Math.abs(MathHelper.wrapDegrees(rZ[0] - baseYaw)) + Math.abs(rZ[1] - basePitch), rZ[0], rZ[1]));
                float[] rX =
                        RotationUtils.getRotationsFromEye(eye, faceEast ? bx + 1.0 - 0.05 : bx + 0.05, by + v, bz + u);
                cands.add(new AutoBlockin.RotationCandidate(
                        Math.abs(MathHelper.wrapDegrees(rX[0] - baseYaw)) + Math.abs(rX[1] - basePitch), rX[0], rX[1]));
            }
        }

        cands.sort((a, b) -> Double.compare(a.cost, b.cost));
        int byY = targetCell.getY();

        for (AutoBlockin.RotationCandidate c : cands) {
            HitResult mop = RotationUtils.rayCastBlock(reachVal, c.yaw, c.pitch);
            if (mop != null) {
                BlockPos hitBlock = mop.getPos();
                Direction face = mop.face;
                if (hitBlock.equals(targetCell)
                        && hitBlock.getY() >= minY
                        && (face != Direction.DOWN || byY != minY)
                        && BlockUtils.canPlaceBlockOnSide(held, hitBlock, face)) {
                    BlockPos placementPos = hitBlock.offset(face);
                    if (!this.isNearBed(placementPos)) {
                        return new AutoBlockin.AimResult(hitBlock, face, c.yaw, c.pitch);
                    }
                }
            }
        }

        return null;
    }

    private void countProgressPosition(BlockPos pos) {
        if (!this.isNearBed(pos)) {
            this.fillTargetCount++;
            if (!BlockUtils.replaceable(pos)) {
                this.fillCount++;
            }
        }
    }

    private AutoBlockin.AimResult sidesAim() {
        BlockPos feet = new BlockPos(
                MathHelper.floor(mc.player.x), MathHelper.floor(mc.player.y), MathHelper.floor(mc.player.z));
        BlockPos head = feet.up();
        double r = 4.5;
        Vec3d eye = mc.player.getEyePosition(1.0F);
        ArrayList<BlockPos> baseline = new ArrayList<>(8);

        for (Direction dir : HORIZONTALS) {
            baseline.add(feet.offset(dir));
            baseline.add(head.offset(dir));
        }

        ArrayList<BlockPos> primaryGoals = new ArrayList<>(baseline.size());

        for (BlockPos pos : baseline) {
            if (BlockUtils.replaceable(pos) && this.hasReplaceableNeighbor(pos, feet, head)) {
                primaryGoals.add(pos);
            }
        }

        if (primaryGoals.isEmpty()) {
            return null;
        }

        LivingEntity enemy = ModuleManager.killAura != null ? ModuleManager.killAura.getClosestTarget(10.0) : null;
        if (enemy != null) {
            ArrayList<Direction> directions = new ArrayList<>(Arrays.asList(HORIZONTALS));
            directions.sort((a, b) -> {
                double da = sq(feet.getX() + a.getOffsetX() + 0.5 - enemy.x)
                        + sq(feet.getZ() + a.getOffsetZ() + 0.5 - enemy.z);
                double db = sq(feet.getX() + b.getOffsetX() + 0.5 - enemy.x)
                        + sq(feet.getZ() + b.getOffsetZ() + 0.5 - enemy.z);
                return Double.compare(da, db);
            });

            for (Direction direction : directions) {
                BlockPos side = feet.offset(direction);
                ArrayList<BlockPos> sideGoals = new ArrayList<>(2);

                for (BlockPos goal : primaryGoals) {
                    if (goal.getX() == side.getX() && goal.getZ() == side.getZ()) {
                        sideGoals.add(goal);
                    }
                }

                AutoBlockin.AimResult rEnemy = this.findBestForGoals(sideGoals, r, eye);
                if (rEnemy != null) {
                    return rEnemy;
                }
            }
        }

        AutoBlockin.AimResult result = this.findBestForGoals(primaryGoals, r, eye);
        if (result != null) {
            return result;
        }

        ArrayList<BlockPos> frontier = new ArrayList<>(primaryGoals);
        HashSet<Long> seen = new HashSet<>(frontier.size() * 8);

        for (BlockPos g : frontier) {
            seen.add(g.toLong());
        }

        for (int iter = 0; iter < 5 && !frontier.isEmpty(); iter++) {
            ArrayList<BlockPos> layer = new ArrayList<>(frontier.size() * 3);

            for (BlockPos g : frontier) {
                for (Direction f : Direction.values()) {
                    BlockPos s = g.offset(f);
                    if (BlockUtils.replaceable(s) && seen.add(s.toLong())) {
                        layer.add(s);
                    }
                }
            }

            if (!layer.isEmpty()) {
                AutoBlockin.AimResult rLayer = this.findBestForGoals(layer, r, eye);
                if (rLayer != null) {
                    return rLayer;
                }
            }

            frontier = layer;
        }

        return null;
    }

    private boolean hasReplaceableNeighbor(BlockPos pos, BlockPos... exclude) {
        for (Direction facing : Direction.values()) {
            BlockPos neighbor = pos.offset(facing);
            if (BlockUtils.replaceable(neighbor)) {
                boolean excluded = false;

                for (BlockPos excludedPos : exclude) {
                    if (neighbor.equals(excludedPos)) {
                        excluded = true;
                        break;
                    }
                }

                if (!excluded) {
                    return true;
                }
            }
        }

        return false;
    }

    private AutoBlockin.AimResult findBestForGoals(List<BlockPos> goals, double reachVal, Vec3d eye) {
        if (goals != null && !goals.isEmpty()) {
            if (this.plannedSlot >= 0 && this.plannedSlot <= 8) {
                ItemStack held = mc.player.inventory.items[this.plannedSlot];
                float curYaw = RotationUtils.serverRotations[0];
                float curPitch = RotationUtils.serverRotations[1];
                HitResult now = RotationUtils.rayCastBlock(reachVal, curYaw, curPitch);
                if (now != null) {
                    BlockPos support = now.getPos();
                    Direction faceHit = now.face;
                    if (!BlockUtils.replaceable(support) && BlockUtils.canPlaceBlockOnSide(held, support, faceHit)) {
                        for (BlockPos goal : goals) {
                            AutoBlockin.AimResult ok = this.tryPlacement(
                                    reachVal,
                                    RotationUtils.serverRotations[0],
                                    RotationUtils.serverRotations[1],
                                    support,
                                    faceHit,
                                    goal);
                            if (ok != null) {
                                return ok;
                            }
                        }
                    }
                }

                double jit = 0.020000000000000004;
                double insetTop = 0.949;
                double insetBot = 0.051000000000000004;
                ArrayList<AutoBlockin.PlacementCandidate> cands =
                        new ArrayList<>(Math.max(16, goals.size() * 6 * (GRID_N + 1) * (GRID_N + 1)));

                for (BlockPos g : goals) {
                    for (AutoBlockin.SupportOffset s : SUPPORTS) {
                        BlockPos support = new BlockPos(g.getX() + s.dx, g.getY() + s.dy, g.getZ() + s.dz);
                        if (!BlockUtils.replaceable(support) && BlockUtils.canPlaceBlockOnSide(held, support, s.face)) {
                            double sx = support.getX();
                            double sy = support.getY();
                            double sz = support.getZ();

                            for (int row = 0; row <= GRID_N; row++) {
                                boolean ltr = (row & 1) == 0;
                                double v = clamp01(row * 0.2 + jitter(jit));

                                for (int col = 0; col <= GRID_N; col++) {
                                    double cu = clamp01(col * 0.2 + jitter(jit));
                                    double u = ltr ? cu : 1.0 - cu;
                                    double px;
                                    double py;
                                    double pz;
                                    if (s.dy != 0) {
                                        px = sx + u;
                                        pz = sz + v;
                                        py = sy + (s.dy < 0 ? insetTop : insetBot);
                                    } else if (s.dz != 0) {
                                        px = sx + u;
                                        py = sy + v;
                                        pz = sz + (s.dz < 0 ? insetTop : insetBot);
                                    } else {
                                        pz = sz + u;
                                        py = sy + v;
                                        px = sx + (s.dx < 0 ? insetTop : insetBot);
                                    }

                                    float[] rot = RotationUtils.getRotationsFromEye(eye, px, py, pz);
                                    float dYaw = Math.abs(MathHelper.wrapDegrees(rot[0] - curYaw));
                                    float dPit = Math.abs(rot[1] - curPitch);
                                    if (!(dYaw < 0.1F) || !(dPit < 0.1F)) {
                                        cands.add(new AutoBlockin.PlacementCandidate(
                                                dYaw + dPit, rot[0], rot[1], support, s.face, g));
                                    }
                                }
                            }
                        }
                    }
                }

                if (cands.isEmpty()) {
                    return null;
                }

                cands.sort((a, b) -> Double.compare(a.cost, b.cost));

                for (AutoBlockin.PlacementCandidate c : cands) {
                    AutoBlockin.AimResult ok = this.tryPlacement(reachVal, c.yaw, c.pitch, c.support, c.face, c.goal);
                    if (ok != null) {
                        return ok;
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private AutoBlockin.AimResult tryPlacement(
            double reachVal, float yaw, float pit, BlockPos expectedSupport, Direction expectedFace, BlockPos goal) {
        HitResult mop = RotationUtils.rayCastBlock(reachVal, yaw, pit);
        if (mop == null) {
            return null;
        } else {
            BlockPos hitBlock = mop.getPos();
            Direction faceHit = mop.face;
            if (!hitBlock.equals(expectedSupport)) {
                return null;
            } else if (faceHit != expectedFace) {
                return null;
            } else {
                BlockPos placed = hitBlock.offset(faceHit);
                if (!placed.equals(goal)) {
                    return null;
                } else {
                    return this.isNearBed(placed) ? null : new AutoBlockin.AimResult(hitBlock, faceHit, yaw, pit);
                }
            }
        }
    }

    private boolean isDirectAdjacentPlacement(BlockPos p) {
        BlockPos feet = new BlockPos(
                MathHelper.floor(mc.player.x), MathHelper.floor(mc.player.y), MathHelper.floor(mc.player.z));
        int dx = p.getX() - feet.getX();
        int dy = p.getY() - feet.getY();
        int dz = p.getZ() - feet.getZ();
        return dx == 0 && dz == 0 && dy == 2
                ? true
                : (dy == 0 || dy == 1) && (Math.abs(dx) == 1 && dz == 0 || Math.abs(dz) == 1 && dx == 0);
    }

    private boolean isNearBed(BlockPos placementPos) {
        if (!this.skipNearBed.isToggled()) {
            return false;
        }

        if (BlockUtils.getBlock(placementPos) instanceof BedBlock) {
            return true;
        }

        for (Direction facing : Direction.values()) {
            if (BlockUtils.getBlock(placementPos.offset(facing)) instanceof BedBlock) {
                return true;
            }
        }

        return false;
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private static float quadInOutEasing(float t) {
        return t < 0.5F ? 2.0F * t * t : -1.0F + (4.0F - 2.0F * t) * t;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double jitter(double range) {
        return range > 0.0 ? (Math.random() * 2.0 - 1.0) * range : 0.0;
    }

    private static class AimResult {
        final BlockPos supportBlock;
        final Direction face;
        final float yaw;
        final float pitch;

        AimResult(BlockPos supportBlock, Direction face, float yaw, float pitch) {
            this.supportBlock = supportBlock;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class BlockCandidate {
        final double dist;
        final BlockPos pos;

        BlockCandidate(double dist, BlockPos pos) {
            this.dist = dist;
            this.pos = pos;
        }
    }

    private static class PlacementCandidate {
        final double cost;
        final float yaw;
        final float pitch;
        final BlockPos support;
        final BlockPos goal;
        final Direction face;

        PlacementCandidate(double cost, float yaw, float pitch, BlockPos support, Direction face, BlockPos goal) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
            this.goal = goal;
        }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw;
        final float pitch;

        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class SupportOffset {
        final int dx;
        final int dy;
        final int dz;
        final Direction face;

        SupportOffset(int dx, int dy, int dz, Direction face) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.face = face;
        }
    }
}
