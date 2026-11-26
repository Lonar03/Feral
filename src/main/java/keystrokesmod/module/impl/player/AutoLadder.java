package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.List;
import keystrokesmod.Feral;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PreInputEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.helper.AutoLadderHelper;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.script.model.Simulation;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Plane;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import net.minecraft.world.World;

public class AutoLadder extends Module {
    private static final int LOOKAHEAD_TICKS = 20;
    private static final int GROUND_LOOKAHEAD_TICKS = 40;
    private static final int GUIDE_TIMEOUT_TICKS = 26;
    private static final int GUIDE_INVALID_LIMIT = 4;
    private static final int STAGE_INVALID_TICK_LIMIT = 2;
    private final AutoLadderHelper session = new AutoLadderHelper();
    private int retryAfterTick;
    private boolean fallQualified;
    private final List<Vec3d> fallPath = new ArrayList<>();
    private boolean predictedLanding;
    private World trackedWorld;
    private AutoLadder.RotationHit stableHit;
    private AutoLadder.ExecutionStage stableStage;
    private static final double MAX_PLACE_BELOW_PLAYER_Y = 3.0;
    private static final double MAX_PLACE_XZ_DELTA = 4.5;
    private static final double ENTRY_TICK_WEIGHT = 120.0;
    private static final double ENTRY_ALIGNMENT_WEIGHT = 90.0;
    private static final double TARGET_DISTANCE_WEIGHT = 10.0;
    private static final double[] HIT_OFFSETS = new double[] {0.5, 0.35, 0.65, 0.2, 0.8, 0.1, 0.9};
    private final SliderSetting minimumDropDistance;
    private final SliderSetting rotationSpeed;
    private final ButtonSetting onlyWhileHoldingLadder;
    private final ButtonSetting renderPlacements;
    private final ButtonSetting blockBlacklistToggle;
    private final BlockListSetting blockBlacklist;
    private AutoLadder.ExecutionStage stage = AutoLadder.ExecutionStage.IDLE;
    private AutoLadder.PlacementPlan activePlan;
    private int currentPlacementTick;
    private boolean placedThisTick;
    private int lastPlacementAttemptTick = Integer.MIN_VALUE;
    private int guideStartTick = Integer.MIN_VALUE;
    private int guideInvalidTicks;
    private int stageInvalidTicks;
    private float stickyYaw;
    private float stickyPitch;
    private boolean hasStickyRotation;
    private int stageStartTick;
    private int rotationTick = Integer.MIN_VALUE;
    private AutoLadder.ExecutionStage rotationStage;
    private AutoLadder.RotationHit placementHit;
    private LocalClientPlayerEntity trackedPlayer;
    private boolean movementClaimed;

    public AutoLadder() {
        super("AutoLadder", Module.category.player);
        this.registerSetting(
                this.minimumDropDistance =
                        new SliderSetting("Minimum height", " blocks", 4.0, 0.0, 20.0, 0.5, "Minimum drop distance"));
        this.registerSetting(this.rotationSpeed = new SliderSetting("Rotation speed", "°", 90.0, 10.0, 180.0, 5.0));
        this.registerSetting(this.onlyWhileHoldingLadder = new ButtonSetting("Only while holding ladder", false));
        this.registerSetting(this.renderPlacements = new ButtonSetting("Render placements", false));
        this.registerSetting(this.blockBlacklistToggle = new ButtonSetting("Block blacklist", false));
        this.registerSetting(this.blockBlacklist = new BlockListSetting("Blacklisted blocks"));
        this.blockBlacklist.visible = false;
        this.closetModule = true;
        this.session.observe();
    }

    @Override
    public void guiUpdate() {
        this.blockBlacklist.setVisible(this.blockBlacklistToggle.isToggled(), this);
    }

    @Override
    public void onEnable() {
        this.fallQualified = false;
        this.session.start();
        this.trackedPlayer = mc.player;
        this.trackedWorld = mc.world;
        this.retryAfterTick = 0;
        this.placedThisTick = false;
        this.resetExecutionState(true, "enabled");
        this.hasStickyRotation = false;
    }

    @Override
    public void onDisable() {
        this.releaseMovement();
        this.fallQualified = false;
        this.resetExecutionState(true, "disabled");
        this.session.finish();
        this.hasStickyRotation = false;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onGameTick(GameTickEvent e) {
        this.movementClaimed = false;
        this.placedThisTick = false;
        this.currentPlacementTick++;
        this.rotationTick = Integer.MIN_VALUE;
        this.placementHit = null;
        this.session.updatePlayer();
        if (this.trackedPlayer != mc.player || this.trackedWorld != mc.world) {
            this.fallQualified = false;
            this.resetExecutionState(false, "player-changed");
            this.trackedWorld = mc.world;
            this.hasStickyRotation = false;
            this.trackedPlayer = mc.player;
        }

        if (!this.isModuleReady()) {
            this.fallQualified = false;
            this.resetExecutionState(true, "module-not-ready");
            this.hasStickyRotation = false;
        } else {
            if (!this.isCurrentlyFalling()) {
                this.fallQualified = false;
            }

            if (this.shouldYieldToClutch()) {
                this.yieldToClutch();
            } else if (!this.session.changedManually() && !this.hasIncompatibleModule()) {
                if (!this.isCurrentlyFalling()) {
                    this.fallQualified = false;
                    this.resetExecutionState(true, "fall-ended");
                } else {
                    if (!this.fallQualified
                            && (this.minimumDropDistance.getInput() == 0.0
                                    || this.heightAboveGround() >= this.minimumDropDistance.getInput())) {
                        this.fallQualified = true;
                    }

                    if (this.activePlan == null) {
                        if (this.currentPlacementTick >= this.retryAfterTick
                                && !this.session.restoring()
                                && this.session.sent().rotationKnown
                                && this.session.sent().positionKnown
                                && this.shouldStartPlanningNow()) {
                            this.startExecutionPlan();
                        }
                    } else if (this.isInsideTargetLadder(this.activePlan.ladderPos)) {
                        this.resetExecutionState(true, "ladder-occupied");
                    } else if (!this.isPlanWithinPlacementBounds(this.activePlan)) {
                        this.resetExecutionState(true, "plan-out-of-bounds");
                    } else {
                        if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                                && this.isSolidLadderSupportBlock(this.activePlan.supportPlacePos)) {
                            this.stage = AutoLadder.ExecutionStage.PLACE_LADDER;
                            this.stageStartTick = this.currentPlacementTick;
                        }

                        if (this.stage == AutoLadder.ExecutionStage.PLACE_LADDER
                                && this.isLadderBlock(this.activePlan.ladderPos)) {
                            this.enterGuideState(this.currentPlacementTick);
                        }

                        if (this.stage == AutoLadder.ExecutionStage.GUIDE_ENTRY) {
                            this.tickGuideState();
                        } else if (!this.shouldAbortForMissingItems()
                                && this.currentPlacementTick - this.stageStartTick
                                        <= (this.activePlan.groundCatch ? 40 : 10)) {
                            this.prepareHeldItemForStage();
                        } else {
                            this.resetExecutionState(true, "placement-expired");
                        }
                    }
                }
            } else {
                if (this.session.changedManually()) {
                    this.fallQualified = false;
                }

                this.resetExecutionState(true, "control-conflict");
                this.session.clearManualChange();
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (!this.isModuleReady()) {
            this.resetExecutionState(true, "module-not-ready");
            this.hasStickyRotation = false;
        } else if (this.shouldYieldToClutch()) {
            this.yieldToClutch();
        } else if (e.yaw != null || e.pitch != null || this.hasIncompatibleModule()) {
            this.resetExecutionState(true, "rotation-conflict");
            this.hasStickyRotation = false;
        } else if (this.session.holdingAim()) {
            this.claimRotations(e, this.session.heldYaw(), this.session.heldPitch());
        } else if (this.activePlan == null || this.stage == AutoLadder.ExecutionStage.GUIDE_ENTRY) {
            this.restoreRotations(e);
            if (this.activePlan != null && e.yaw == null) {
                this.claimRotations(e, mc.player.yaw, mc.player.pitch);
            }
        } else if (!this.isPlanWithinPlacementBounds(this.activePlan)) {
            this.resetExecutionState(true, "plan-out-of-bounds");
            this.restoreRotations(e);
        } else {
            AutoLadder.RotationHit hit = this.resolveStableHit();
            if (hit == null) {
                if (++this.stageInvalidTicks > 2) {
                    this.resetExecutionState(true, "no-placement-ray");
                }

                this.restoreRotations(e);
            } else {
                this.stageInvalidTicks = 0;
                this.applyRotations(e, hit.yaw, hit.pitch, (float) this.rotationSpeed.getInput());
                this.rotationTick = this.currentPlacementTick;
                this.rotationStage = this.stage;
                this.placementHit = hit;
            }
        }
    }

    private void applyRotations(ClientRotationEvent e, float yaw, float pitch, float limit) {
        float baseYaw = this.session.sent().yaw;
        float basePitch = this.session.sent().pitch;
        float yawStep = MathHelper.clamp(MathHelper.wrapDegrees(yaw - baseYaw), -limit, limit);
        float pitchStep = MathHelper.clamp(pitch - basePitch, -limit, limit);
        float[] fixed = RotationUtils.fixRotation(baseYaw + yawStep, basePitch + pitchStep, baseYaw, basePitch);
        this.stickyYaw = fixed[0];
        this.stickyPitch = fixed[1];
        this.hasStickyRotation = true;
        this.session.rememberAim();
        this.claimRotations(e, this.stickyYaw, this.stickyPitch);
    }

    private void claimRotations(ClientRotationEvent event, float yaw, float pitch) {
        this.movementClaimed = this.activePlan != null;
        event.claimExactRotations(yaw, pitch, this.movementClaimed);
    }

    private void releaseMovement() {
        if (this.movementClaimed) {
            RotationHelper.get().releaseServerRelativeMovement();
            this.movementClaimed = false;
        }
    }

    private void restoreRotations(ClientRotationEvent e) {
        if (this.hasStickyRotation) {
            if (e.yaw == null && e.pitch == null) {
                float yaw = mc.player.yaw;
                float pitch = mc.player.pitch;
                if (Math.abs(MathHelper.wrapDegrees(yaw - this.session.sent().yaw)) < 1.0F
                        && Math.abs(pitch - this.session.sent().pitch) < 1.0F) {
                    this.hasStickyRotation = false;
                } else {
                    this.applyRotations(e, yaw, pitch, Math.min(45.0F, (float) this.rotationSpeed.getInput()));
                }
            } else {
                this.hasStickyRotation = false;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreInput(PreInputEvent e) {
        if (this.shouldYieldToClutch()) {
            this.yieldToClutch();
        } else if (this.isModuleReady() && this.isCurrentlyFalling()) {
            if (!this.placedThisTick
                    && this.activePlan != null
                    && !this.session.holdingAim()
                    && !this.session.changedManually()
                    && this.session.canSwitch()
                    && this.lastPlacementAttemptTick != this.currentPlacementTick
                    && this.rotationTick == this.currentPlacementTick
                    && this.rotationStage == this.stage
                    && this.placementHit != null
                    && (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                            || this.stage == AutoLadder.ExecutionStage.PLACE_LADDER)) {
                Float yaw = RotationHelper.get().getServerYaw();
                Float pitch = RotationHelper.get().getServerPitch();
                if (yaw != null && pitch != null && RotationHelper.get().ownsServerRelativeMovement()) {
                    HitResult hit = RotationUtils.rayCastBlock(this.getPlacementReach(), yaw, pitch);
                    BlockPos placePos = this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                            ? this.activePlan.supportPlacePos
                            : this.activePlan.ladderPos;
                    BlockPos anchor = this.placementHit.mop.getPos();
                    Direction face = this.placementHit.mop.face;
                    AutoLadderHelper.SentState sent = this.session.sent();
                    Vec3d eye = new Vec3d(sent.x, sent.y + mc.player.getEyeHeight(), sent.z);
                    Vec3d look = RotationUtils.getVectorForRotation(sent.pitch, sent.yaw);
                    HitResult publishedHit = mc.world.rayTrace(
                            eye,
                            eye.add(
                                    look.x * this.getPlacementReach(),
                                    look.y * this.getPlacementReach(),
                                    look.z * this.getPlacementReach()),
                            false,
                            false,
                            true);
                    if (sent.rotationKnown
                            && sent.positionKnown
                            && this.isValidPlacementRay(publishedHit, placePos, anchor, face)
                            && this.isValidPlacementRay(hit, placePos, anchor, face)) {
                        if (!this.isPlanStillValidForCurrentStage()) {
                            this.resetExecutionState(true, "stage-invalid");
                        } else {
                            ItemStack heldStack = this.resolveHeldStackForCurrentStage();
                            if (heldStack != null && this.session.sent().slot == mc.player.inventory.selectedSlot) {
                                this.lastPlacementAttemptTick = this.currentPlacementTick;
                                BlockState previousState = mc.world.getBlockState(placePos);
                                int previousCount = heldStack.size;
                                PlayerUseC2SPacket previousPlacement = this.session.lastPlacement();
                                boolean accepted = mc.interactionManager.useBlock(
                                        mc.player, mc.world, heldStack, hit.getPos(), hit.face, hit.facePos);
                                PlayerUseC2SPacket dispatched = this.session.lastPlacement();
                                if (dispatched != previousPlacement
                                        && dispatched != null
                                        && anchor.equals(dispatched.getPos())
                                        && dispatched.getFace() == face.getId()) {
                                    this.session.pinAim(yaw, pitch);
                                    if (accepted) {
                                        this.placedThisTick = true;
                                        mc.player.swingArm();
                                        mc.getItemInHandRenderer().onBlockUsed();
                                        if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                                                && this.isSolidLadderSupportBlock(this.activePlan.supportPlacePos)) {
                                            this.stage = AutoLadder.ExecutionStage.PLACE_LADDER;
                                            this.stageStartTick = this.currentPlacementTick;
                                        } else if (this.stage == AutoLadder.ExecutionStage.PLACE_LADDER
                                                && this.isLadderBlock(this.activePlan.ladderPos)) {
                                            this.enterGuideState(this.currentPlacementTick);
                                        }
                                    }
                                } else {
                                    mc.world.setBlockState(placePos, previousState, 3);
                                    heldStack.size = previousCount;
                                    this.resetExecutionState(true, "placement-not-dispatched");
                                }
                            }
                        }
                    }
                }
            }
        } else {
            this.resetExecutionState(true, "placement-interrupted");
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (!this.isModuleReady()
                || !this.isCurrentlyFalling()
                || this.activePlan == null
                || this.stage == AutoLadder.ExecutionStage.IDLE
                || !RotationHelper.get().ownsServerRelativeMovement()
                || this.session.changedManually()
                || this.hasIncompatibleModule()) {
            this.releaseMovement();
        } else if (this.shouldYieldToClutch()) {
            this.yieldToClutch();
        } else {
            this.prepareHeldItemForStage();
            Vec3d movementTarget = this.getLadderSteerTarget(this.activePlan.ladderPos);
            this.applyMovementOverride(e, movementTarget);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRightClick(RightClickMouseEvent e) {
        if (this.placedThisTick) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onAttack(PreAttackEvent e) {
        if (this.placedThisTick) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (this.renderPlacements.isToggled()
                && this.isModuleReady()
                && this.activePlan != null
                && this.trackedWorld == mc.world
                && this.trackedPlayer == mc.player
                && (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                        || this.stage == AutoLadder.ExecutionStage.PLACE_LADDER)) {
            AutoLadder.PlacementPlan plan = this.activePlan;
            if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                    && plan.supportPlacePos != null
                    && !this.isSolidLadderSupportBlock(plan.supportPlacePos)) {
                RenderUtils.renderBlock(plan.supportPlacePos, -43691, false, true);
            }

            if (!this.isLadderBlock(plan.ladderPos)) {
                double x = plan.ladderPos.getX();
                double z = plan.ladderPos.getZ();
                double width = 1.0;
                double depth = 1.0;
                switch (plan.ladderFace) {
                    case NORTH:
                        z += 0.875;
                        depth = 0.125;
                        break;
                    case SOUTH:
                        depth = 0.125;
                        break;
                    case WEST:
                        x += 0.875;
                        width = 0.125;
                        break;
                    case EAST:
                        width = 0.125;
                        break;
                    default:
                        return;
                }

                RenderUtils.renderBox(x, plan.ladderPos.getY(), z, width, 1.0, depth, -21931, false, true);
            }
        }
    }

    private void startExecutionPlan() {
        AutoLadder.PlacementPlan plan = this.buildDeterministicPlan();
        if (plan == null) {
            this.resetExecutionState(true, "no-valid-plan");
        } else {
            this.activatePlan(plan);
        }
    }

    private void activatePlan(AutoLadder.PlacementPlan plan) {
        this.activePlan = plan;
        this.stableHit = null;
        this.stage = plan.requiresSupportPlacement
                ? AutoLadder.ExecutionStage.PLACE_SUPPORT
                : AutoLadder.ExecutionStage.PLACE_LADDER;
        this.stageStartTick = this.currentPlacementTick;
        this.guideStartTick = this.currentPlacementTick;
        this.guideInvalidTicks = 0;
        this.stageInvalidTicks = 0;
        if (this.isLadderBlock(plan.ladderPos)) {
            this.enterGuideState(this.currentPlacementTick);
        } else {
            this.prepareHeldItemForStage();
        }
    }

    private void tickGuideState() {
        if (this.activePlan != null && this.stage == AutoLadder.ExecutionStage.GUIDE_ENTRY) {
            if (mc.player.isClimbing() || this.isInsideTargetLadder(this.activePlan.ladderPos)) {
                this.resetExecutionState(true, "guide-success");
            } else if (mc.player.onGround) {
                this.resetExecutionState(true, "guide-grounded");
            } else if (!this.isPlanWithinPlacementBounds(this.activePlan)) {
                this.resetExecutionState(true, "guide-plan-out-of-bounds");
            } else if (!this.isLadderBlock(this.activePlan.ladderPos)) {
                this.resetExecutionState(true, "ladder-missing");
            } else {
                AutoLadder.EntryEstimate estimate =
                        this.estimateLadderEntry(this.activePlan.ladderPos, this.activePlan.ladderFace, null, 0, 0);
                if (!estimate.valid()) {
                    this.guideInvalidTicks++;
                    if (this.guideInvalidTicks > 4) {
                        this.resetExecutionState(true, "guide-unreachable");
                        return;
                    }
                } else {
                    this.guideInvalidTicks = 0;
                }

                int playerTick = this.getPlacementPlayerTick();
                if (playerTick != Integer.MIN_VALUE
                        && this.guideStartTick != Integer.MIN_VALUE
                        && playerTick - this.guideStartTick > 26) {
                    this.resetExecutionState(true, "guide-timeout");
                }
            }
        }
    }

    private void enterGuideState(int playerTick) {
        this.stage = AutoLadder.ExecutionStage.GUIDE_ENTRY;
        this.guideStartTick = playerTick;
        this.guideInvalidTicks = 0;
        this.stageInvalidTicks = 0;
        this.restoreOriginalSlotIfNeeded();
    }

    private AutoLadder.RotationHit resolveStableHit() {
        float yaw = this.session.sent().yaw;
        float pitch = this.session.sent().pitch;
        if (this.activePlan.groundCatch) {
            BlockPos anchor = this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                    ? this.activePlan.supportPlacePos.down()
                    : this.activePlan.ladderSupportPos;
            Direction face =
                    this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT ? Direction.UP : this.activePlan.ladderFace;
            Vec3d point = this.buildFaceHitVec(anchor, face, 0.5, 0.5);
            float[] aim = this.getFixedRotationsForVec(point, yaw, pitch);
            return new AutoLadder.RotationHit(aim[0], aim[1], new HitResult(point, face, anchor), 0.0);
        }

        if (this.stableHit != null && this.stableStage == this.stage) {
            BlockPos anchor = this.stableHit.mop.getPos();
            Direction face = this.stableHit.mop.face;
            BlockPos destination = this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                    ? this.activePlan.supportPlacePos
                    : this.activePlan.ladderPos;
            float[] aim = this.getFixedRotationsForVec(this.stableHit.mop.facePos, yaw, pitch);
            HitResult hit = RotationUtils.rayCastBlock(this.getPlacementReach(), aim[0], aim[1]);
            if (this.isValidPlacementRay(hit, destination, anchor, face)) {
                return new AutoLadder.RotationHit(aim[0], aim[1], this.stableHit.mop, 0.0);
            }
        }

        this.stableStage = this.stage;
        return this.stableHit = this.resolveRotationForCurrentStage(yaw, pitch);
    }

    private AutoLadder.RotationHit resolveRotationForCurrentStage(float baseYaw, float basePitch) {
        return this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                ? this.resolveSupportPlacementHit(this.activePlan, baseYaw, basePitch)
                : this.resolveLadderPlacementHit(this.activePlan, baseYaw, basePitch);
    }

    private AutoLadder.RotationHit resolveSupportPlacementHit(
            AutoLadder.PlacementPlan plan, float baseYaw, float basePitch) {
        return plan != null
                        && plan.requiresSupportPlacement
                        && plan.supportPlacePos != null
                        && BlockUtils.replaceable(plan.supportPlacePos)
                ? this.findSupportBlockPlacementHit(plan.supportPlacePos, baseYaw, basePitch)
                : null;
    }

    private AutoLadder.RotationHit resolveLadderPlacementHit(
            AutoLadder.PlacementPlan plan, float baseYaw, float basePitch) {
        if (plan == null || plan.ladderPos == null || plan.ladderSupportPos == null || plan.ladderFace == null) {
            return null;
        } else if (!this.isReplaceableOrLadder(plan.ladderPos)) {
            return null;
        } else {
            return !this.canUseAsLadderSupport(plan.ladderPos, plan.ladderFace, plan.ladderSupportPos)
                    ? null
                    : this.findRotationHitForPlacement(
                            plan.ladderPos, plan.ladderSupportPos, plan.ladderFace, baseYaw, basePitch);
        }
    }

    private AutoLadder.PlacementPlan buildDeterministicPlan() {
        if (this.findLadderSlot() == -1) {
            return null;
        }

        this.predictFallPath();
        AutoLadder.PlacementPlan direct = this.findPlacementPlan(false);
        return direct != null ? direct : (this.findSupportSlot() == -1 ? null : this.findGroundPlan());
    }

    private void predictFallPath() {
        this.fallPath.clear();
        this.predictedLanding = false;
        Simulation simulation = Simulation.create();
        simulation.setJump(false);

        for (int tick = 0; tick <= 40; tick++) {
            Vec3 pos = simulation.getPosition();
            this.fallPath.add(new Vec3d(pos.x, pos.y, pos.z));
            if (simulation.isInLiquid()) {
                return;
            }

            if (simulation.onGround()) {
                this.predictedLanding = true;
                return;
            }

            simulation.tick();
        }
    }

    private boolean fallPassesCell(BlockPos cell) {
        for (Vec3d pos : this.fallPath) {
            if (pos.y < cell.getY()) {
                break;
            }

            if (pos.y < cell.getY() + 1
                    && pos.x >= cell.getX()
                    && pos.x < cell.getX() + 1
                    && pos.z >= cell.getZ()
                    && pos.z < cell.getZ() + 1) {
                return true;
            }
        }

        return false;
    }

    private AutoLadder.PlacementPlan findGroundPlan() {
        if (this.predictedLanding && !this.fallPath.isEmpty()) {
            Vec3d landing = this.fallPath.get(this.fallPath.size() - 1);
            BlockPos center = new BlockPos(landing.x, Math.ceil(landing.y - 1.0E-7), landing.z);
            AutoLadder.PlacementPlan best = null;

            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos ladder = center.add(x, 0, z);
                    if (BlockUtils.replaceable(ladder)) {
                        for (Direction face : Plane.HORIZONTAL) {
                            BlockPos support = ladder.offset(face.getOpposite());
                            if (BlockUtils.replaceable(support) && this.isSolidLadderSupportBlock(support.down())) {
                                AutoLadder.PlacementPlan candidate =
                                        new AutoLadder.PlacementPlan(ladder, support, face, true, 0.0, true);
                                AutoLadder.EntryEstimate estimate = this.estimateGroundEntry(candidate);
                                if (estimate.valid()) {
                                    double dx = ladder.getX() + 0.5 - landing.x;
                                    double dz = ladder.getZ() + 0.5 - landing.z;
                                    candidate = new AutoLadder.PlacementPlan(
                                            ladder,
                                            support,
                                            face,
                                            true,
                                            this.computePlanCost(
                                                    estimate.tickAhead,
                                                    estimate.entryAlignmentErrorSq,
                                                    dx * dx + dz * dz),
                                            true);
                                    best = this.pickBetterPlan(best, candidate);
                                }
                            }
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    private AutoLadder.EntryEstimate estimateGroundEntry(AutoLadder.PlacementPlan plan) {
        Simulation simulation = Simulation.create();
        boolean supportPlaced = this.isSolidLadderSupportBlock(plan.supportPlacePos);
        boolean ladderPlaced = this.isLadderBlock(plan.ladderPos);
        if (supportPlaced
                || BlockUtils.replaceable(plan.supportPlacePos)
                        && this.isSolidLadderSupportBlock(plan.supportPlacePos.down())) {
            if (!this.isReplaceableOrLadder(plan.ladderPos)) {
                return AutoLadder.EntryEstimate.invalid();
            }

            AutoLadderHelper.SentState sent = this.session.sent();
            float yaw = sent.yaw;
            float pitch = sent.pitch;
            Vec3d publishedEye = new Vec3d(sent.x, sent.y + mc.player.getEyeHeight(), sent.z);
            Vec3d target = this.getLadderSteerTarget(plan.ladderPos);

            for (int tick = 0; tick <= 40; tick++) {
                Vec3 pos = simulation.getPosition();
                if (simulation.onGround() || simulation.isInLiquid() || pos.y < plan.ladderPos.getY()) {
                    return AutoLadder.EntryEstimate.invalid();
                }

                if (ladderPlaced
                        && simulation.isOnLadder()
                        && this.feetOccupyLadderCell(pos.x, pos.y, pos.z, plan.ladderPos)) {
                    double dx = pos.x - target.x;
                    double dz = pos.z - target.z;
                    return new AutoLadder.EntryEstimate(tick, dx * dx + dz * dz);
                }

                Vec3d eye = new Vec3d(pos.x, pos.y + mc.player.getEyeHeight(), pos.z);
                float nextYaw;
                float nextPitch;
                if (!ladderPlaced) {
                    BlockPos anchor = supportPlaced ? plan.supportPlacePos : plan.supportPlacePos.down();
                    Direction face = supportPlaced ? plan.ladderFace : Direction.UP;
                    Vec3d point = this.buildFaceHitVec(anchor, face, 0.5, 0.5);
                    double dx = point.x - eye.x;
                    double dy = point.y - eye.y;
                    double dz = point.z - eye.z;
                    float[] aim = RotationUtils.fixRotation(
                            yaw + MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F - yaw),
                            (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))),
                            yaw,
                            pitch);
                    float speed = (float) this.rotationSpeed.getInput();
                    float[] step = RotationUtils.fixRotation(
                            yaw + MathHelper.clamp(MathHelper.wrapDegrees(aim[0] - yaw), -speed, speed),
                            pitch + MathHelper.clamp(aim[1] - pitch, -speed, speed),
                            yaw,
                            pitch);
                    nextYaw = step[0];
                    nextPitch = step[1];
                    BlockPos hypothetical = supportPlaced ? plan.supportPlacePos : null;
                    if ((tick > 0 || this.session.canSwitch())
                            && this.groundRayReaches(publishedEye, yaw, pitch, anchor, face, hypothetical)
                            && this.groundRayReaches(eye, nextYaw, nextPitch, anchor, face, hypothetical)) {
                        if (!supportPlaced) {
                            double half = mc.player.width * 0.5;
                            Box box = new Box(
                                    pos.x - half,
                                    pos.y,
                                    pos.z - half,
                                    pos.x + half,
                                    pos.y + mc.player.height,
                                    pos.z + half);
                            if (box.intersects(new Box(plan.supportPlacePos, plan.supportPlacePos.add(1, 1, 1)))) {
                                return AutoLadder.EntryEstimate.invalid();
                            }

                            simulation.setBlockState(plan.supportPlacePos, Blocks.STONE.defaultState());
                            supportPlaced = true;
                        } else {
                            simulation.setBlockState(
                                    plan.ladderPos,
                                    Blocks.LADDER.defaultState().set(LadderBlock.FACING, plan.ladderFace));
                            ladderPlaced = true;
                        }
                    }
                } else {
                    float speed = Math.min(45.0F, (float) this.rotationSpeed.getInput());
                    float[] step = RotationUtils.fixRotation(
                            yaw + MathHelper.clamp(MathHelper.wrapDegrees(mc.player.yaw - yaw), -speed, speed),
                            pitch + MathHelper.clamp(mc.player.pitch - pitch, -speed, speed),
                            yaw,
                            pitch);
                    nextYaw = step[0];
                    nextPitch = step[1];
                }

                if (ladderPlaced
                        && simulation.isOnLadder()
                        && this.feetOccupyLadderCell(pos.x, pos.y, pos.z, plan.ladderPos)) {
                    double entryX = pos.x - target.x;
                    double entryZ = pos.z - target.z;
                    return new AutoLadder.EntryEstimate(tick, entryX * entryX + entryZ * entryZ);
                }

                float[] input = this.chooseSteering(
                        target,
                        pos.x,
                        pos.z,
                        simulation.getMotion().x,
                        simulation.getMotion().z,
                        nextYaw,
                        simulation.getAirAcceleration());
                simulation.setForward(input[0]);
                simulation.setStrafe(input[1]);
                simulation.setYaw(nextYaw);
                simulation.setJump(false);
                simulation.setSneak(false);
                simulation.setSprinting(mc.options.sprintKey.isPressed());
                simulation.tick();
                pos = simulation.getPosition();
                publishedEye = new Vec3d(pos.x, pos.y + mc.player.getEyeHeight(), pos.z);
                yaw = nextYaw;
                pitch = nextPitch;
            }

            return AutoLadder.EntryEstimate.invalid();
        } else {
            return AutoLadder.EntryEstimate.invalid();
        }
    }

    private boolean groundRayReaches(
            Vec3d eye, float yaw, float pitch, BlockPos anchor, Direction face, BlockPos hypotheticalSupport) {
        Vec3d look = RotationUtils.getVectorForRotation(pitch, yaw);
        double reach = this.getPlacementReach();
        Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        HitResult hit = mc.world.rayTrace(eye, end, false, false, true);
        if (hypotheticalSupport != null) {
            HitResult supportHit = new Box(hypotheticalSupport, hypotheticalSupport.add(1, 1, 1)).clip(eye, end);
            if (supportHit != null
                    && (hit == null
                            || hit.type != Type.BLOCK
                            || eye.squaredDistanceTo(supportHit.facePos) < eye.squaredDistanceTo(hit.facePos))) {
                hit = new HitResult(supportHit.facePos, supportHit.face, hypotheticalSupport);
            }
        }

        return hit != null && hit.type == Type.BLOCK && anchor.equals(hit.getPos()) && face == hit.face;
    }

    private AutoLadder.PlacementPlan findPlacementPlan(boolean requiresSupportPlacement) {
        int feetY = MathHelper.floor(this.getPlayerFeetY());
        int minX = MathHelper.floor(mc.player.x - 4.5);
        int maxX = MathHelper.floor(mc.player.x + 4.5);
        int minZ = MathHelper.floor(mc.player.z - 4.5);
        int maxZ = MathHelper.floor(mc.player.z + 4.5);
        AutoLadder.PlacementPlan best = null;

        for (int y = feetY; y >= feetY - 3; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos ladderPos = new BlockPos(x, y, z);
                    if (this.isWithinPlacementBounds(ladderPos)
                            && this.canReachCellBeforePassing(ladderPos)
                            && this.isReplaceableOrLadder(ladderPos)
                            && this.fallPassesCell(ladderPos)) {
                        AutoLadder.PlacementPlan plan = requiresSupportPlacement
                                ? this.buildSupportThenLadderPlanForCell(ladderPos)
                                : this.buildDirectLadderPlanForCell(ladderPos);
                        best = this.pickBetterPlan(best, plan);
                    }
                }
            }
        }

        return best;
    }

    private boolean canReachCellBeforePassing(BlockPos pos) {
        double y = this.getPlayerFeetY();
        double motionY = mc.player.velocityY;
        double speed = Math.sqrt(mc.player.velocityX * mc.player.velocityX + mc.player.velocityZ * mc.player.velocityZ);
        double distance = mc.player.width;

        for (int tick = 0; tick < 20 && y >= pos.getY(); tick++) {
            distance += speed + 0.026;
            speed = (speed + 0.026) * 0.91;
            y += motionY;
            motionY = (motionY - 0.08) * 0.98;
        }

        double dx = Math.max(0.0, Math.abs(pos.getX() + 0.5 - mc.player.x) - 0.5);
        double dz = Math.max(0.0, Math.abs(pos.getZ() + 0.5 - mc.player.z) - 0.5);
        return dx * dx + dz * dz <= distance * distance;
    }

    private AutoLadder.PlacementPlan buildDirectLadderPlanForCell(BlockPos ladderPos) {
        AutoLadder.PlacementPlan best = null;

        for (Direction ladderFace : Plane.HORIZONTAL) {
            BlockPos ladderSupportPos = ladderPos.offset(ladderFace.getOpposite());
            if (this.isWithinPlacementBounds(ladderSupportPos)
                    && this.canUseAsLadderSupport(ladderPos, ladderFace, ladderSupportPos)) {
                AutoLadder.RotationHit hit = this.isLadderBlock(ladderPos)
                        ? null
                        : this.findRotationHitForPlacement(
                                ladderPos,
                                ladderSupportPos,
                                ladderFace,
                                this.getPlacementBaseYaw(),
                                this.getPlacementBasePitch());
                if (hit != null || this.isLadderBlock(ladderPos)) {
                    int readyTick = hit == null
                            ? 0
                            : this.rotationTicks(
                                    hit.yaw, hit.pitch, this.getPlacementBaseYaw(), this.getPlacementBasePitch());
                    AutoLadder.EntryEstimate estimate =
                            this.estimateLadderEntry(ladderPos, ladderFace, null, readyTick, 0);
                    if (estimate.valid()) {
                        double planCost = this.computePlanCost(
                                estimate.tickAhead,
                                estimate.entryAlignmentErrorSq,
                                this.getHorizontalDistanceSq(ladderPos));
                        AutoLadder.PlacementPlan candidate =
                                new AutoLadder.PlacementPlan(ladderPos, ladderSupportPos, ladderFace, false, planCost);
                        best = this.pickBetterPlan(best, candidate);
                    }
                }
            }
        }

        return best;
    }

    private AutoLadder.PlacementPlan buildSupportThenLadderPlanForCell(BlockPos ladderPos) {
        AutoLadder.PlacementPlan best = null;

        for (Direction ladderFace : Plane.HORIZONTAL) {
            BlockPos supportPlacePos = ladderPos.offset(ladderFace.getOpposite());
            if (this.isWithinPlacementBounds(supportPlacePos)
                    && BlockUtils.replaceable(supportPlacePos)
                    && !this.doesPlayerIntersectPlacementNowOrNextTick(supportPlacePos)
                    && this.hasSupportPlacementAnchor(supportPlacePos)) {
                AutoLadder.RotationHit supportHit = this.findSupportBlockPlacementHit(
                        supportPlacePos, this.getPlacementBaseYaw(), this.getPlacementBasePitch());
                if (supportHit != null) {
                    float[] ladderAim = this.getFixedRotationsForVec(
                            this.buildFaceHitVec(supportPlacePos, ladderFace, 0.5, 0.5),
                            supportHit.yaw,
                            supportHit.pitch);
                    int supportReadyTick = this.rotationTicks(
                            supportHit.yaw, supportHit.pitch, this.getPlacementBaseYaw(), this.getPlacementBasePitch());
                    int readyTick = supportReadyTick
                            + 1
                            + this.rotationTicks(ladderAim[0], ladderAim[1], supportHit.yaw, supportHit.pitch);
                    AutoLadder.EntryEstimate estimate = this.estimateLadderEntry(
                            ladderPos, ladderFace, supportPlacePos, readyTick, supportReadyTick);
                    if (estimate.valid()) {
                        double planCost = this.computePlanCost(
                                estimate.tickAhead,
                                estimate.entryAlignmentErrorSq,
                                this.getHorizontalDistanceSq(ladderPos));
                        AutoLadder.PlacementPlan candidate =
                                new AutoLadder.PlacementPlan(ladderPos, supportPlacePos, ladderFace, true, planCost);
                        best = this.pickBetterPlan(best, candidate);
                    }
                }
            }
        }

        return best;
    }

    private AutoLadder.PlacementPlan pickBetterPlan(
            AutoLadder.PlacementPlan currentBest, AutoLadder.PlacementPlan candidate) {
        if (candidate == null) {
            return currentBest;
        } else if (currentBest == null) {
            return candidate;
        } else if (Math.abs(candidate.planCost - currentBest.planCost) > 1.0E-6) {
            return candidate.planCost < currentBest.planCost ? candidate : currentBest;
        } else {
            return candidate.ladderPos.toLong() < currentBest.ladderPos.toLong() ? candidate : currentBest;
        }
    }

    private double computePlanCost(int entryTick, double entryAlignmentErrorSq, double horizontalDistanceSq) {
        return entryTick * 120.0 + entryAlignmentErrorSq * 90.0 + horizontalDistanceSq * 10.0;
    }

    private AutoLadder.RotationHit findSupportBlockPlacementHit(BlockPos placePos, float baseYaw, float basePitch) {
        AutoLadder.RotationHit best = null;

        for (Direction placeFace : Direction.values()) {
            if (placeFace != Direction.DOWN) {
                BlockPos anchorPos = placePos.offset(placeFace.getOpposite());
                if (this.isAnchorBlockValid(anchorPos)) {
                    AutoLadder.RotationHit hit =
                            this.findRotationHitForPlacement(placePos, anchorPos, placeFace, baseYaw, basePitch);
                    if (hit != null && (best == null || hit.score < best.score)) {
                        best = hit;
                    }
                }
            }
        }

        return best;
    }

    private AutoLadder.RotationHit findRotationHitForPlacement(
            BlockPos placePos, BlockPos supportPos, Direction supportFace, float baseYaw, float basePitch) {
        if (placePos != null && supportPos != null && supportFace != null && mc.player != null) {
            if (!this.isWithinReachBounds(placePos, supportPos)) {
                return null;
            }

            Vec3i faceDirection = supportFace.getNormal();
            Vec3d supportFaceCenter = new Vec3d(
                    supportPos.getX() + 0.5 + faceDirection.getX() * 0.5,
                    supportPos.getY() + 0.5 + faceDirection.getY() * 0.5,
                    supportPos.getZ() + 0.5 + faceDirection.getZ() * 0.5);
            AutoLadder.RotationHit best = null;
            double reach = this.getPlacementReach();
            Vec3d eye = mc.player.getEyePosition(1.0F);

            for (double u : HIT_OFFSETS) {
                for (double v : HIT_OFFSETS) {
                    Vec3d hitVec = this.buildFaceHitVec(supportPos, supportFace, u, v);
                    if (hitVec != null) {
                        float[] fixedRotations = this.getFixedRotationsForHitVec(hitVec, baseYaw, basePitch);
                        HitResult traced = RotationUtils.rayCastBlock(reach, fixedRotations[0], fixedRotations[1]);
                        if (this.isValidPlacementRay(traced, placePos, supportPos, supportFace)) {
                            double yawDelta = Math.abs(MathHelper.wrapDegrees(fixedRotations[0] - baseYaw));
                            double pitchDelta = Math.abs(fixedRotations[1] - basePitch);
                            double centerPenalty = supportFaceCenter.squaredDistanceTo(traced.facePos);
                            double score = yawDelta + pitchDelta + centerPenalty * 4.0;
                            if (best == null || score < best.score) {
                                best = new AutoLadder.RotationHit(fixedRotations[0], fixedRotations[1], traced, score);
                            }
                        }
                    }
                }
            }

            return best;
        } else {
            return null;
        }
    }

    private boolean isValidPlacementRay(
            HitResult mop, BlockPos expectedPlacePos, BlockPos expectedSupportPos, Direction expectedFace) {
        if (mop != null
                && mop.type == Type.BLOCK
                && mop.getPos() != null
                && mop.face != null
                && mop.facePos != null
                && expectedPlacePos != null
                && expectedSupportPos != null
                && expectedFace != null
                && !this.isBlacklisted(expectedSupportPos)) {
            if (BlockUtils.isBlockPosEqual(mop.getPos(), expectedSupportPos) && mop.face == expectedFace) {
                BlockPos placePos = BlockUtils.offsetPos(mop);
                return placePos != null && BlockUtils.isBlockPosEqual(placePos, expectedPlacePos);
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean canUseAsLadderSupport(BlockPos ladderPos, Direction ladderFace, BlockPos supportPos) {
        if (ladderPos == null || ladderFace == null || supportPos == null || mc.world == null) {
            return false;
        } else if (!this.isSolidLadderSupportBlock(supportPos)) {
            return false;
        } else {
            return !this.isReplaceableOrLadder(ladderPos)
                    ? false
                    : Blocks.LADDER.canBePlaced(mc.world, ladderPos, ladderFace);
        }
    }

    private boolean isAnchorBlockValid(BlockPos pos) {
        if (pos != null
                && !BlockUtils.replaceable(pos)
                && !BlockUtils.isInteractable(BlockUtils.getBlock(pos))
                && !this.isBlacklisted(pos)) {
            Block block = BlockUtils.getBlock(pos);
            return block != null && block != Blocks.AIR;
        } else {
            return false;
        }
    }

    private boolean hasSupportPlacementAnchor(BlockPos placePos) {
        if (placePos == null) {
            return false;
        }

        for (Direction placeFace : Direction.values()) {
            if (placeFace != Direction.DOWN) {
                BlockPos anchorPos = placePos.offset(placeFace.getOpposite());
                if (this.isAnchorBlockValid(anchorPos)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSolidLadderSupportBlock(BlockPos pos) {
        if (pos != null
                && !BlockUtils.replaceable(pos)
                && !BlockUtils.isInteractable(BlockUtils.getBlock(pos))
                && !this.isBlacklisted(pos)) {
            Block block = BlockUtils.getBlock(pos);
            return block != null && block != Blocks.AIR && block.isCube();
        } else {
            return false;
        }
    }

    private boolean isBlacklisted(BlockPos pos) {
        if (this.blockBlacklistToggle.isToggled()
                && !this.blockBlacklist.getBlocks().isEmpty()
                && pos != null
                && mc.world != null) {
            BlockState state = mc.world.getBlockState(pos);
            Block block = state.getBlock();
            return this.isBlacklisted(block, block.getMetadataFromState(state));
        } else {
            return false;
        }
    }

    private boolean isBlacklisted(Block block, int metadata) {
        if (this.blockBlacklistToggle.isToggled() && block != null) {
            Identifier registryName = (Identifier) Block.REGISTRY.getKey(block);
            if (registryName == null) {
                return false;
            }

            String registryId = registryName.toString();
            String storageId = metadata != 0 ? registryId + ":" + metadata : registryId;
            return this.blockBlacklist.contains(storageId) || this.blockBlacklist.contains(registryId);
        } else {
            return false;
        }
    }

    private boolean isWithinReachBounds(BlockPos placePos, BlockPos supportPos) {
        if (mc.player == null) {
            return false;
        }

        Vec3d eye = mc.player.getEyePosition(1.0F);
        Vec3d placeCenter = new Vec3d(placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5);
        Vec3d supportCenter = new Vec3d(supportPos.getX() + 0.5, supportPos.getY() + 0.5, supportPos.getZ() + 0.5);
        double reach = this.getPlacementReach() + 1.0;
        return eye.distanceTo(placeCenter) <= reach || eye.distanceTo(supportCenter) <= reach;
    }

    private float[] getFixedRotationsForHitVec(Vec3d hitVec, float baseYaw, float basePitch) {
        return this.getFixedRotationsForVec(hitVec, baseYaw, basePitch);
    }

    private float[] getFixedRotationsForVec(Vec3d targetVec, float baseYaw, float basePitch) {
        if (targetVec != null && mc.player != null) {
            double dx = targetVec.x - mc.player.x;
            double dy = targetVec.y - (mc.player.y + mc.player.getEyeHeight());
            double dz = targetVec.z - mc.player.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float yaw = horizontalDistance < 1.0E-6 ? baseYaw : (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontalDistance)));
            yaw = baseYaw + MathHelper.wrapDegrees(yaw - baseYaw);
            pitch = MathHelper.clamp(basePitch + MathHelper.wrapDegrees(pitch - basePitch), -90.0F, 90.0F);
            return RotationUtils.fixRotation(yaw, pitch, baseYaw, basePitch);
        } else {
            return null;
        }
    }

    private Vec3d buildFaceHitVec(BlockPos blockPos, Direction face, double u, double v) {
        if (blockPos != null && face != null) {
            double cu = MathHelper.clamp(u, 0.01, 0.99);
            double cv = MathHelper.clamp(v, 0.01, 0.99);
            double x;
            double y;
            double z;
            switch (face) {
                case NORTH:
                    x = blockPos.getX() + cu;
                    y = blockPos.getY() + cv;
                    z = blockPos.getZ() + 0.01;
                    break;
                case SOUTH:
                    x = blockPos.getX() + cu;
                    y = blockPos.getY() + cv;
                    z = blockPos.getZ() + 0.99;
                    break;
                case WEST:
                    x = blockPos.getX() + 0.01;
                    y = blockPos.getY() + cv;
                    z = blockPos.getZ() + cu;
                    break;
                case EAST:
                    x = blockPos.getX() + 0.99;
                    y = blockPos.getY() + cv;
                    z = blockPos.getZ() + cu;
                    break;
                case DOWN:
                    x = blockPos.getX() + cu;
                    y = blockPos.getY() + 0.01;
                    z = blockPos.getZ() + cv;
                    break;
                case UP:
                    x = blockPos.getX() + cu;
                    y = blockPos.getY() + 0.99;
                    z = blockPos.getZ() + cv;
                    break;
                default:
                    return null;
            }

            return new Vec3d(x, y, z);
        } else {
            return null;
        }
    }

    private int rotationTicks(float yaw, float pitch, float baseYaw, float basePitch) {
        double angle = Math.max(Math.abs(MathHelper.wrapDegrees(yaw - baseYaw)), Math.abs(pitch - basePitch));
        return Math.max(1, (int) Math.ceil(angle / this.rotationSpeed.getInput()));
    }

    private AutoLadder.EntryEstimate estimateLadderEntry(
            BlockPos ladderPos,
            Direction ladderFace,
            BlockPos supportPlacementPos,
            int ladderReadyTick,
            int supportReadyTick) {
        Simulation simulation = Simulation.create();
        Vec3d target = this.getLadderSteerTarget(ladderPos);

        for (int tickAhead = 0; tickAhead <= 20; tickAhead++) {
            Vec3 pos = simulation.getPosition();
            if (simulation.onGround() || simulation.isInLiquid() || pos.y < ladderPos.getY()) {
                return AutoLadder.EntryEstimate.invalid();
            }

            if (tickAhead >= ladderReadyTick
                    && simulation.isOnLadder()
                    && this.feetOccupyLadderCell(pos.x, pos.y, pos.z, ladderPos)) {
                double dx = target.x - pos.x;
                double dz = target.z - pos.z;
                return new AutoLadder.EntryEstimate(tickAhead, dx * dx + dz * dz);
            }

            if (supportPlacementPos != null && tickAhead == supportReadyTick) {
                double halfWidth = mc.player.width * 0.5;
                Box playerBox = new Box(
                        pos.x - halfWidth,
                        pos.y,
                        pos.z - halfWidth,
                        pos.x + halfWidth,
                        pos.y + mc.player.height,
                        pos.z + halfWidth);
                if (playerBox.intersects(new Box(supportPlacementPos, supportPlacementPos.add(1, 1, 1)))) {
                    return AutoLadder.EntryEstimate.invalid();
                }

                simulation.setBlockState(supportPlacementPos, Blocks.STONE.defaultState());
            }

            if (tickAhead == ladderReadyTick && !this.isLadderBlock(ladderPos)) {
                simulation.setBlockState(ladderPos, Blocks.LADDER.defaultState().set(LadderBlock.FACING, ladderFace));
            }

            if (tickAhead >= ladderReadyTick
                    && simulation.isOnLadder()
                    && this.feetOccupyLadderCell(pos.x, pos.y, pos.z, ladderPos)) {
                double dx = target.x - pos.x;
                double dz = target.z - pos.z;
                return new AutoLadder.EntryEstimate(tickAhead, dx * dx + dz * dz);
            }

            float[] input = this.chooseSteering(
                    target,
                    pos.x,
                    pos.z,
                    simulation.getMotion().x,
                    simulation.getMotion().z,
                    this.getSteeringYaw(),
                    simulation.getAirAcceleration());
            simulation.setForward(input[0]);
            simulation.setStrafe(input[1]);
            simulation.setYaw(this.getSteeringYaw());
            simulation.setJump(false);
            simulation.setSneak(false);
            simulation.setSprinting(mc.options.sprintKey.isPressed());
            simulation.tick();
        }

        return AutoLadder.EntryEstimate.invalid();
    }

    private boolean feetOccupyLadderCell(double x, double y, double z, BlockPos pos) {
        return MathHelper.floor(x) == pos.getX()
                && MathHelper.floor(y) == pos.getY()
                && MathHelper.floor(z) == pos.getZ();
    }

    private boolean isPlanStillValidForCurrentStage() {
        if (this.activePlan == null || !this.isPlanWithinPlacementBounds(this.activePlan)) {
            return false;
        }

        if (this.activePlan.groundCatch) {
            return !this.shouldAbortForMissingItems()
                    && this.estimateGroundEntry(this.activePlan).valid();
        }

        if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT) {
            if (this.activePlan.requiresSupportPlacement
                    && this.activePlan.supportPlacePos != null
                    && BlockUtils.replaceable(this.activePlan.supportPlacePos)
                    && this.findSupportSlot() != -1
                    && !this.doesPlayerIntersectPlacementNowOrNextTick(this.activePlan.supportPlacePos)
                    && this.hasSupportPlacementAnchor(this.activePlan.supportPlacePos)) {
                float[] aim = this.getFixedRotationsForVec(
                        this.buildFaceHitVec(this.activePlan.supportPlacePos, this.activePlan.ladderFace, 0.5, 0.5),
                        this.placementHit.yaw,
                        this.placementHit.pitch);
                int readyTick = this.rotationTicks(aim[0], aim[1], this.placementHit.yaw, this.placementHit.pitch);
                AutoLadder.EntryEstimate estimate = this.estimateLadderEntry(
                        this.activePlan.ladderPos,
                        this.activePlan.ladderFace,
                        this.activePlan.supportPlacePos,
                        readyTick,
                        0);
                return estimate.valid();
            } else {
                return false;
            }
        } else if (this.stage == AutoLadder.ExecutionStage.PLACE_LADDER) {
            if (this.findLadderSlot() != -1
                    && this.isReplaceableOrLadder(this.activePlan.ladderPos)
                    && this.canUseAsLadderSupport(
                            this.activePlan.ladderPos, this.activePlan.ladderFace, this.activePlan.ladderSupportPos)) {
                AutoLadder.EntryEstimate estimate =
                        this.estimateLadderEntry(this.activePlan.ladderPos, this.activePlan.ladderFace, null, 0, 0);
                return estimate.valid();
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean shouldAbortForMissingItems() {
        if (this.activePlan == null) {
            return false;
        } else {
            return this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT
                    ? this.findSupportSlot() == -1 || this.findLadderSlot() == -1
                    : this.stage == AutoLadder.ExecutionStage.PLACE_LADDER && this.findLadderSlot() == -1;
        }
    }

    private void prepareHeldItemForStage() {
        if (mc.player != null && this.activePlan != null) {
            if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT) {
                int supportSlot = this.findSupportSlot();
                if (supportSlot != -1) {
                    this.switchToSlot(supportSlot);
                }
            } else if (this.stage == AutoLadder.ExecutionStage.PLACE_LADDER) {
                int ladderSlot = this.findLadderSlot();
                if (ladderSlot != -1) {
                    this.switchToSlot(ladderSlot);
                }
            }
        }
    }

    private ItemStack resolveHeldStackForCurrentStage() {
        if (mc.player == null) {
            return null;
        }

        ItemStack held = mc.player.getDisplayItemInHand();
        if (this.stage == AutoLadder.ExecutionStage.PLACE_SUPPORT) {
            if (this.isSupportBlockStack(held)) {
                return held;
            }

            int slot = this.findSupportSlot();
            if (slot == -1) {
                return null;
            }

            this.switchToSlot(slot);
            held = mc.player.getDisplayItemInHand();
            return this.isSupportBlockStack(held) ? held : null;
        } else {
            if (this.isLadderStack(held)) {
                return held;
            }

            int ladderSlot = this.findLadderSlot();
            if (ladderSlot == -1) {
                return null;
            }

            this.switchToSlot(ladderSlot);
            held = mc.player.getDisplayItemInHand();
            return this.isLadderStack(held) ? held : null;
        }
    }

    private void switchToSlot(int slot) {
        this.session.select(slot);
    }

    private void restoreOriginalSlotIfNeeded() {
        this.session.restore();
    }

    private int findLadderSlot() {
        if (mc.player == null) {
            return -1;
        }

        return Utils.findHotbarSlot(this::isLadderStack);
    }

    private int findSupportSlot() {
        if (mc.player == null) {
            return -1;
        }

        return Utils.findHotbarSlot(this::isSupportBlockStack);
    }

    private boolean isLadderStack(ItemStack stack) {
        if (stack != null && stack.size > 0 && stack.getItem() instanceof BlockItem) {
            Block block = ((BlockItem) stack.getItem()).getBlock();
            return block == Blocks.LADDER;
        } else {
            return false;
        }
    }

    private boolean isSupportBlockStack(ItemStack stack) {
        if (stack != null && stack.size > 0 && stack.getItem() instanceof BlockItem) {
            BlockItem itemBlock = (BlockItem) stack.getItem();
            Block block = itemBlock.getBlock();
            return block != Blocks.LADDER
                    && block != null
                    && block.isCube()
                    && Utils.canBePlaced(itemBlock)
                    && !this.isBlacklisted(block, itemBlock.getBlockMetadata(stack.getMetadata()));
        } else {
            return false;
        }
    }

    private float[] steeringInput(Vec3d target, double x, double z, double motionX, double motionZ, float yaw) {
        return this.chooseSteering(target, x, z, motionX, motionZ, yaw, mc.player.speedInAir);
    }

    private float[] chooseSteering(
            Vec3d target, double x, double z, double motionX, double motionZ, float yaw, float acceleration) {
        double dx = target.x - x;
        double dz = target.z - z;
        double best = Double.POSITIVE_INFINITY;
        int bestForward = 0;
        int bestStrafe = 0;
        float sin = MathHelper.sin(yaw * (float) Math.PI / 180.0F);
        float cos = MathHelper.cos(yaw * (float) Math.PI / 180.0F);

        for (int forward = -1; forward <= 1; forward++) {
            for (int strafe = -1; strafe <= 1; strafe++) {
                float forwardInput = forward * 0.98F;
                float strafeInput = strafe * 0.98F;
                float scale = acceleration
                        / Math.max(1.0F, MathHelper.sqrt(forwardInput * forwardInput + strafeInput * strafeInput));
                double velocityX = motionX + (strafeInput * cos - forwardInput * sin) * scale;
                double velocityZ = motionZ + (forwardInput * cos + strafeInput * sin) * scale;
                double errorX = dx - velocityX * 5.0;
                double errorZ = dz - velocityZ * 5.0;
                double cost = errorX * errorX + errorZ * errorZ + (forward * forward + strafe * strafe) * 1.0E-6;
                if (cost < best) {
                    best = cost;
                    bestForward = forward;
                    bestStrafe = strafe;
                }
            }
        }

        return new float[] {bestForward, bestStrafe};
    }

    private void applyMovementOverride(PrePlayerInputEvent e, Vec3d target) {
        float[] input = this.steeringInput(
                target, mc.player.x, mc.player.z, mc.player.velocityX, mc.player.velocityZ, this.getSteeringYaw());
        e.setForward(input[0]);
        e.setStrafe(input[1]);
        e.setJump(false);
        e.setSneak(false);
    }

    private Vec3d getLadderSteerTarget(BlockPos ladderPos) {
        return new Vec3d(ladderPos.getX() + 0.5, ladderPos.getY() + 0.5, ladderPos.getZ() + 0.5);
    }

    private double getHorizontalDistanceSq(BlockPos pos) {
        if (pos != null && mc.player != null) {
            double dx = pos.getX() + 0.5 - mc.player.x;
            double dz = pos.getZ() + 0.5 - mc.player.z;
            return dx * dx + dz * dz;
        } else {
            return Double.MAX_VALUE;
        }
    }

    private float getSteeringYaw() {
        Float yaw = RotationHelper.get().getServerYaw();
        return yaw == null ? this.session.sent().yaw : yaw;
    }

    private float getPlacementBaseYaw() {
        return this.session.sent().yaw;
    }

    private float getPlacementBasePitch() {
        return this.session.sent().pitch;
    }

    private boolean shouldYieldToClutch() {
        Module clutchModule = ModuleManager.getModule(Clutch.class);
        return clutchModule instanceof Clutch && ((Clutch) clutchModule).isActiveWindow();
    }

    int yieldToClutch() {
        int original = this.session.transferSlot();
        Module clutch = ModuleManager.getModule(Clutch.class);
        if (original >= 0 && clutch instanceof Clutch) {
            ((Clutch) clutch).adoptLadderSlot(original);
        }

        this.resetExecutionState(false, "yield-clutch");
        this.hasStickyRotation = false;
        return original;
    }

    private boolean hasIncompatibleModule() {
        return this.getBlockingModule() != null;
    }

    private String getBlockingModule() {
        return Feral.lagHandler != null && Feral.lagHandler.isBuffering() ? "Buffered packets" : null;
    }

    private boolean shouldStartPlanningNow() {
        return this.isCurrentlyFalling()
                && (!this.onlyWhileHoldingLadder.isToggled() || this.isLadderStack(mc.player.getDisplayItemInHand()))
                && this.findLadderSlot() != -1
                && (this.fallQualified
                        || this.minimumDropDistance.getInput() == 0.0
                        || this.heightAboveGround() >= this.minimumDropDistance.getInput());
    }

    private double heightAboveGround() {
        Box feet = mc.player.getShape();
        Box column = new Box(
                feet.minX + 1.0E-7,
                -1.0,
                feet.minZ + 1.0E-7,
                feet.maxX - 1.0E-7,
                feet.minY + 1.0E-7,
                feet.maxZ - 1.0E-7);
        double surface = Double.NEGATIVE_INFINITY;
        List<Box> boxes = new ArrayList<>();

        for (int x = MathHelper.floor(column.minX); x <= MathHelper.floor(column.maxX); x++) {
            for (int z = MathHelper.floor(column.minZ); z <= MathHelper.floor(column.maxZ); z++) {
                for (int y = MathHelper.floor(feet.minY); y >= 0 && y + 1.5 > surface; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    boxes.clear();
                    state.getBlock().addCollisions(mc.world, pos, state, column, boxes, mc.player);

                    for (Box box : boxes) {
                        if (box.maxY <= feet.minY + 1.0E-7) {
                            surface = Math.max(surface, box.maxY);
                        }
                    }
                }
            }
        }

        return feet.minY - surface;
    }

    private boolean isCurrentlyFalling() {
        return mc.player != null
                && !mc.player.onGround
                && !mc.player.isClimbing()
                && mc.player.velocityY < 0.0
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.abilities.flying
                && !mc.player.isSpectator()
                && !mc.player.noClip
                && mc.player.isAlive()
                && !mc.player.isRiding()
                && !mc.player.hasItemInUse();
    }

    private boolean isPlanWithinPlacementBounds(AutoLadder.PlacementPlan plan) {
        if (plan == null || plan.ladderPos == null) {
            return false;
        } else if (plan.groundCatch && this.stage != AutoLadder.ExecutionStage.GUIDE_ENTRY) {
            return this.getPlayerFeetY() >= plan.ladderPos.getY()
                    && this.getHorizontalDistanceSq(plan.ladderPos) <= 20.25;
        } else if (this.stage == AutoLadder.ExecutionStage.GUIDE_ENTRY) {
            double belowDelta = this.getPlayerFeetY() - plan.ladderPos.getY();
            return mc.player != null
                    && Math.abs(plan.ladderPos.getX() + 0.5 - mc.player.x) <= 4.5
                    && Math.abs(plan.ladderPos.getZ() + 0.5 - mc.player.z) <= 4.5
                    && belowDelta >= -1.0
                    && belowDelta <= 3.0;
        } else if (!this.isWithinPlacementBounds(plan.ladderPos)) {
            return false;
        } else {
            return plan.ladderSupportPos != null && !this.isWithinPlacementBounds(plan.ladderSupportPos)
                    ? false
                    : plan.supportPlacePos == null || this.isWithinPlacementBounds(plan.supportPlacePos);
        }
    }

    private boolean isWithinPlacementBounds(BlockPos pos) {
        if (pos != null && mc.player != null) {
            double dx = Math.abs(pos.getX() + 0.5 - mc.player.x);
            double dz = Math.abs(pos.getZ() + 0.5 - mc.player.z);
            if (dx <= 4.5 && dz <= 4.5) {
                double belowDelta = this.getPlayerFeetY() - pos.getY();
                return belowDelta > -1.0 && belowDelta <= 3.0;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private double getPlayerFeetY() {
        if (mc.player == null) {
            return 0.0;
        } else {
            return mc.player.getShape() != null ? mc.player.getShape().minY : mc.player.y;
        }
    }

    private boolean isReplaceableOrLadder(BlockPos pos) {
        if (pos == null) {
            return false;
        }

        Block block = BlockUtils.getBlock(pos);
        return block == Blocks.LADDER || BlockUtils.replaceable(pos);
    }

    private boolean isInsideTargetLadder(BlockPos ladderPos) {
        return ladderPos != null
                && mc.player != null
                && mc.player.isClimbing()
                && this.isLadderBlock(ladderPos)
                && this.feetOccupyLadderCell(mc.player.x, this.getPlayerFeetY(), mc.player.z, ladderPos);
    }

    private boolean doesPlayerIntersectPlacementNowOrNextTick(BlockPos pos) {
        if (mc.player != null && pos != null) {
            Box placementBox = new Box(pos, pos.add(1, 1, 1));
            Box playerBox = mc.player.getShape();
            return playerBox.intersects(placementBox)
                    || playerBox
                            .moved(mc.player.velocityX, mc.player.velocityY, mc.player.velocityZ)
                            .intersects(placementBox);
        } else {
            return false;
        }
    }

    private boolean isLadderBlock(BlockPos pos) {
        return pos != null && BlockUtils.getBlock(pos) == Blocks.LADDER;
    }

    private boolean isModuleReady() {
        if (this.isEnabled() && Utils.nullCheck() && mc.interactionManager != null && !mc.isPaused()) {
            LocalClientPlayerEntity player = mc.player;
            return player != null && player.input != null && mc.screen == null;
        } else {
            return false;
        }
    }

    private int getPlacementPlayerTick() {
        return Utils.nullCheck() && mc.player != null ? this.currentPlacementTick : Integer.MIN_VALUE;
    }

    private double getPlacementReach() {
        return mc.interactionManager == null ? 4.5 : mc.interactionManager.getReach();
    }

    private void resetExecutionState(boolean restoreOriginalSlot, String reason) {
        this.releaseMovement();
        if (this.activePlan != null || "no-valid-plan".equals(reason) || "control-conflict".equals(reason)) {
            this.retryAfterTick = this.currentPlacementTick + 3;
        }

        this.stableHit = null;
        this.stage = AutoLadder.ExecutionStage.IDLE;
        this.activePlan = null;
        this.placementHit = null;
        this.rotationTick = Integer.MIN_VALUE;
        this.lastPlacementAttemptTick = Integer.MIN_VALUE;
        this.guideStartTick = Integer.MIN_VALUE;
        this.guideInvalidTicks = 0;
        this.stageInvalidTicks = 0;
        if (restoreOriginalSlot) {
            this.restoreOriginalSlotIfNeeded();
        }
    }

    private static final class EntryEstimate {
        private final int tickAhead;
        private final double entryAlignmentErrorSq;

        private EntryEstimate(int tickAhead, double entryAlignmentErrorSq) {
            this.tickAhead = tickAhead;
            this.entryAlignmentErrorSq = entryAlignmentErrorSq;
        }

        private static AutoLadder.EntryEstimate invalid() {
            return new AutoLadder.EntryEstimate(-1, Double.MAX_VALUE);
        }

        private boolean valid() {
            return this.tickAhead >= 0;
        }
    }

    private enum ExecutionStage {
        IDLE,
        PLACE_SUPPORT,
        PLACE_LADDER,
        GUIDE_ENTRY;
    }

    private static final class PlacementPlan {
        private final BlockPos ladderPos;
        private final BlockPos ladderSupportPos;
        private final Direction ladderFace;
        private final boolean requiresSupportPlacement;
        private final BlockPos supportPlacePos;
        private final double planCost;
        private final boolean groundCatch;

        private PlacementPlan(
                BlockPos ladderPos,
                BlockPos supportPos,
                Direction ladderFace,
                boolean requiresSupportPlacement,
                double planCost) {
            this(ladderPos, supportPos, ladderFace, requiresSupportPlacement, planCost, false);
        }

        private PlacementPlan(
                BlockPos ladderPos,
                BlockPos supportPos,
                Direction ladderFace,
                boolean requiresSupportPlacement,
                double planCost,
                boolean groundCatch) {
            this.ladderPos = ladderPos;
            this.ladderSupportPos = supportPos;
            this.ladderFace = ladderFace;
            this.requiresSupportPlacement = requiresSupportPlacement;
            this.supportPlacePos = requiresSupportPlacement ? supportPos : null;
            this.planCost = planCost;
            this.groundCatch = groundCatch;
        }
    }

    private static final class RotationHit {
        private final float yaw;
        private final float pitch;
        private final HitResult mop;
        private final double score;

        private RotationHit(float yaw, float pitch, HitResult mop, double score) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.mop = mop;
            this.score = score;
        }
    }
}
