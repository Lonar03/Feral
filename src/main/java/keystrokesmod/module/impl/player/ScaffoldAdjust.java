// Ported from Adjust (openadjust, github.com/yxzeroo/openadjust), modified for Feral
// Source: module/impl/movement/scaffold/* (ScaffoldModule, HypixelScaffoldMode, NormalScaffoldMode, PlacementPredictor,
// PlacementFinder, RotationSolver, PlacementTracker, SafeWalkHelper and the rotation models).
// Differences: rotations go through our RotationHelper (always with movement fix), the simulation uses our
// SimulatedPlayer, Rewound/NCP modes, binds, fake clicks and the inbound packet hold while towering are left out.
package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityLivingBase;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.movement.Stasis;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.SimulatedPlayer;
import keystrokesmod.utility.Utils;
import net.minecraft.block.AbstractPressurePlateBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.WorldSettings;

public class ScaffoldAdjust extends Module {
    private static final String[] MODES = new String[] {"Normal", "Hypixel"};
    private static final String[] NO_ASCEND_MODES = new String[] {"Flat", "Buffer"};
    private static final String[] ROTATION_MODES = new String[] {"Linear", "Distributed", "Step", "Organic", "Instant"};
    private static final String[] TOWER_MOVEMENTS = new String[] {"Normal", "Stop", "Center"};
    private static final float[] BACK_WEIGHTS = new float[] {0.17F, 0.66F, 0.17F};
    private static final float[] FORWARD_WEIGHTS = new float[] {0.4F, 0.6F};
    private static final Random RANDOM = new Random();
    // Adjust: ServiceUtil.movementAdjustment, a random double rolled once per session
    private static final double MOVEMENT_ADJUSTMENT = RANDOM.nextDouble();
    private static final int ROTATION_WINDOW = Math.abs(RANDOM.nextInt()) % 3 + 20;

    private enum Stage {
        NONE,
        GROUND,
        FORWARD,
        BACK,
        RETURN
    }

    // ---------------------------------------------------------------- settings
    private final SliderSetting mode;
    private final ButtonSetting rotationsEnabled;
    private final SliderSetting rotationMode;
    private final SliderSetting maxAngle;
    private final SliderSetting stepAngle;
    private final SliderSetting driftIntensity;
    private final SliderSetting jitterIntensity;
    private final ButtonSetting snap;
    private final ButtonSetting stableYaw;
    private final ButtonSetting raycast;
    private final ButtonSetting clientsideSwing;
    private final ButtonSetting spoofItem;
    private final ButtonSetting changeSlot;
    private final SliderSetting extend;
    private final ButtonSetting centerMovement;
    private final ButtonSetting autoStasis;
    private final ButtonSetting safeWalkEnabled;
    private final ButtonSetting safeWalkSneak;
    private final SliderSetting unSneakDelay;
    private final SliderSetting earlySneak;
    private final ButtonSetting allowSprint;
    private final ButtonSetting noAscendEnabled;
    private final SliderSetting noAscendMode;
    private final ButtonSetting autoJump;
    private final ButtonSetting ascendOnJump;
    private final ButtonSetting towerEnabled;
    private final SliderSetting towerMovement;
    private final ButtonSetting blockCount;
    private final ButtonSetting blockPlacements;

    // ---------------------------------------------------------------- state (ScaffoldModule)
    private Target placement;
    private Target lastPlacement;
    private BlockPos lastPlacePos;
    private BlockPos lastPlaceBox;
    private Target predicted;
    private float targetYaw;
    private float targetPitch;
    private float rotationJitter;
    private int ticksSincePlace = 100;
    private int validSlotTicks;
    private int baseY;
    private boolean placeReady;
    private boolean hasAngles;
    private boolean firstTurn;
    private boolean noAscendActive;
    private boolean keepBaseY;
    private boolean fastForward;
    private boolean towerKeyOnGround;
    private Float yawOffset;
    private float geometryYaw;
    private int originalSlot = -1;
    // rotation that gets sent this tick (null = none requested)
    private float[] nextRotation;
    private boolean requested;
    private boolean returning;
    private RotModel lastModel;
    // movement counters (Adjust keeps them on the player)
    private int offGroundTicks;
    private int onGroundTicks;
    private boolean lastMoveHadPosition;
    // Hypixel mode
    private Stage stage = Stage.NONE;
    private BlockPos lastGroundSupport;
    private int stageTicks;
    private int forwardTicks;
    private boolean towering;
    // Normal mode tower
    private boolean placedUp;
    // PlacementTracker
    private final List<Record> records = new ArrayList<>();
    private Vec3d pendingPos;
    private long pendingUntil;
    private int rejectTicks; // Adjust: ServerTracker.ticks
    // SafeWalkHelper
    private int unsneakTicks;
    private boolean sneaking;
    public static boolean safeWalkRequest;

    public ScaffoldAdjust() {
        super("Scaffold (Adjust)", Module.category.player);
        this.registerSetting(new DescriptionSetting("Adjust's scaffold"));
        this.registerSetting(this.mode = new SliderSetting("Mode", 1, MODES));
        GroupSetting rotations = new GroupSetting("Rotations");
        this.registerSetting(rotations);
        this.registerSetting(this.rotationsEnabled = new ButtonSetting(rotations, "Enabled", true));
        // Adjust: RotationSetting (Normal mode only, Hypixel uses its own rotations)
        this.registerSetting(this.rotationMode = new SliderSetting(rotations, "Rotation mode", 2, ROTATION_MODES));
        this.registerSetting(this.maxAngle = new SliderSetting(rotations, "Max angle", "°", 60.0, 10.0, 180.0, 1.0));
        this.registerSetting(this.stepAngle = new SliderSetting(rotations, "Step angle", "°", 25.0, 5.0, 180.0, 1.0));
        this.registerSetting(this.driftIntensity = new SliderSetting(rotations, "Drift intensity", 1.2, 0.5, 2.0, 0.1));
        this.registerSetting(
                this.jitterIntensity = new SliderSetting(rotations, "Jitter intensity", 0.12, 0.0, 0.3, 0.01));
        this.registerSetting(this.snap = new ButtonSetting(rotations, "Snap", false));
        this.registerSetting(this.stableYaw = new ButtonSetting(rotations, "Stable yaw", true));
        this.registerSetting(this.raycast = new ButtonSetting(rotations, "Raycast", true));
        GroupSetting addons = new GroupSetting("Addons");
        this.registerSetting(addons);
        this.registerSetting(this.clientsideSwing = new ButtonSetting(addons, "Clientside swing", true));
        this.registerSetting(this.spoofItem = new ButtonSetting(addons, "Spoof item", false));
        this.registerSetting(this.changeSlot = new ButtonSetting(addons, "Change slot", true));
        this.registerSetting(this.extend = new SliderSetting(addons, "Extend", 0.0, 0.0, 3.0, 1.0));
        this.registerSetting(this.centerMovement = new ButtonSetting(addons, "Center movement", true));
        this.registerSetting(this.autoStasis = new ButtonSetting(addons, "Auto stasis", false));
        this.registerSetting(this.allowSprint = new ButtonSetting(addons, "Allow sprint", true));
        GroupSetting safeWalk = new GroupSetting("Safe walk");
        this.registerSetting(safeWalk);
        this.registerSetting(this.safeWalkEnabled = new ButtonSetting(safeWalk, "Enabled", false));
        this.registerSetting(this.safeWalkSneak = new ButtonSetting(safeWalk, "Sneak", false));
        this.registerSetting(
                this.unSneakDelay = new SliderSetting(safeWalk, "Un-sneak delay", " ticks", 0.0, 0.0, 5.0, 1.0));
        this.registerSetting(
                this.earlySneak = new SliderSetting(safeWalk, "Early sneak", " ticks", 0.0, 0.0, 5.0, 1.0));
        GroupSetting noAscend = new GroupSetting("No ascend");
        this.registerSetting(noAscend);
        this.registerSetting(this.noAscendEnabled = new ButtonSetting(noAscend, "Enabled", false));
        this.registerSetting(this.noAscendMode = new SliderSetting(noAscend, "Mode", 0, NO_ASCEND_MODES));
        this.registerSetting(this.autoJump = new ButtonSetting(noAscend, "Auto jump", true));
        this.registerSetting(this.ascendOnJump = new ButtonSetting(noAscend, "Ascend on jump", true));
        GroupSetting tower = new GroupSetting("Tower");
        this.registerSetting(tower);
        this.registerSetting(this.towerEnabled = new ButtonSetting(tower, "Enabled", false));
        this.registerSetting(this.towerMovement = new SliderSetting(tower, "Movement", 0, TOWER_MOVEMENTS));
        GroupSetting render = new GroupSetting("Render");
        this.registerSetting(render);
        this.registerSetting(this.blockCount = new ButtonSetting(render, "Block count", true));
        this.registerSetting(this.blockPlacements = new ButtonSetting(render, "Block placements", false));
    }

    @Override
    public String getInfo() {
        return MODES[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        int rotationMode = (int) this.rotationMode.getInput();
        this.rotationMode.setVisible(!this.isHypixel(), this);
        // Linear, Step and Organic use max angle; Distributed and Instant don't
        this.maxAngle.setVisible(
                !this.isHypixel() && (rotationMode == 0 || rotationMode == 2 || rotationMode == 3), this);
        this.stepAngle.setVisible(!this.isHypixel() && rotationMode == 2, this);
        this.driftIntensity.setVisible(!this.isHypixel() && rotationMode == 3, this);
        this.jitterIntensity.setVisible(!this.isHypixel() && rotationMode == 3, this);
        this.extend.setVisible(!this.isHypixel(), this);
        this.ascendOnJump.setVisible(!this.isHypixel(), this);
        this.towerMovement.setVisible(!this.isHypixel(), this);
        this.unSneakDelay.setVisible(this.safeWalkSneak.isToggled(), this);
        this.earlySneak.setVisible(this.safeWalkSneak.isToggled(), this);
    }

    private boolean isHypixel() {
        return this.mode.getInput() == 1.0;
    }

    // ================================================================ enable / disable

    @Override
    public void onEnable() {
        this.resetState();
        if (mc.player != null) {
            this.originalSlot = mc.player.inventory.selectedSlot;
            float yaw = mc.player.yaw;
            float forward = this.forwardInput();
            float strafe = this.strafeInput();
            float angle = forward < 0.0F ? -45.0F : forward > 0.0F ? 45.0F : 90.0F;
            if (forward < 0.0F) {
                yaw += 180.0F;
            }
            if (strafe > 0.0F) {
                yaw -= angle;
            }
            if (strafe < 0.0F) {
                yaw += angle;
            }
            this.targetYaw = yaw - (RANDOM.nextFloat() - 0.5F + 180.0F);
            this.targetPitch = RotationUtils.serverRotations[1];
            this.baseY = (int) mc.player.y;
        } else {
            this.baseY = Integer.MAX_VALUE;
        }
    }

    @Override
    public void onDisable() {
        Stasis stasis = (Stasis) ModuleManager.getModule(Stasis.class);
        if (stasis != null && stasis.isEnabled() && stasis.isAuto()) {
            stasis.disable();
        }
        this.resetState();
        this.resetHypixel();
        this.placedUp = false;
        if (this.sneaking) {
            KeyBinding.set(mc.options.sneakKey.getKeyCode(), Utils.isBindDown(mc.options.sneakKey));
        }
        this.sneaking = false;
        this.unsneakTicks = 0;
        safeWalkRequest = false;
        RotationHelper.get().forceMovementFix = false;
        if (mc.player != null && this.originalSlot >= 0 && this.originalSlot < 9) {
            mc.player.inventory.selectedSlot = this.originalSlot;
        }
        this.originalSlot = -1;
    }

    private void resetState() {
        this.placeReady = false;
        this.ticksSincePlace = 100;
        this.placement = null;
        this.lastPlacement = null;
        this.lastPlacePos = null;
        this.lastPlaceBox = null;
        this.predicted = null;
        this.hasAngles = false;
        this.firstTurn = false;
        this.rotationJitter = 0.0F;
        this.validSlotTicks = 0;
        this.noAscendActive = false;
        this.keepBaseY = false;
        this.fastForward = false;
        this.towerKeyOnGround = false;
        this.yawOffset = null;
        this.nextRotation = null;
        this.requested = false;
        this.returning = false;
        this.records.clear();
        this.pendingPos = null;
        this.rejectTicks = 0;
    }

    private void resetHypixel() {
        this.stage = Stage.NONE;
        this.stageTicks = 0;
        this.forwardTicks = 0;
        this.lastGroundSupport = null;
        this.towering = false;
    }

    // ================================================================ main tick

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        LocalClientPlayerEntity player = mc.player;
        if (player.onGround) {
            this.onGroundTicks++;
            this.offGroundTicks = 0;
        } else {
            this.offGroundTicks++;
            this.onGroundTicks = 0;
        }
        if (mc.interactionManager.getGameMode() == WorldSettings.GameMode.ADVENTURE
                || mc.interactionManager.getGameMode() == WorldSettings.GameMode.SPECTATOR) {
            this.disable();
            return;
        }
        this.requested = false;
        this.nextRotation = null;
        this.tickTower();
        this.onTick();
        this.finishRotation();
        this.onPlaceStage();
    }

    // Adjust: ScaffoldModule.onTick (PreClientTickEvent)
    private void onTick() {
        LocalClientPlayerEntity player = mc.player;
        this.placement = null;
        this.removeExpired();
        this.noAscendActive = this.isNoAscendActive(true);
        Integer ground = this.predict(this.currentRotation(this.targetYaw, this.targetPitch));
        boolean nearGround = ground != null && ground < 5;
        boolean hasBlock = this.selectBlock(nearGround);
        if (hasBlock) {
            this.validSlotTicks++;
            // Adjust waits one extra tick after switching on Hypixel
            this.placeReady = this.validSlotTicks >= 2;
        } else {
            this.validSlotTicks = 0;
            this.placeReady = false;
        }
        if (this.placeReady) {
            this.hasAngles = true;
            this.updateSafeWalk(ground);
            if (ground == null) {
                this.ticksSincePlace++;
            }
            if (this.canPlaceNow()) {
                if (this.safeWalkEnabled.isToggled() && this.safeWalkSneak.isToggled()) {
                    BlockPos below = new BlockPos(player.x, player.y - 1.0, player.z);
                    if (mc.world.getBlockState(below).getBlock().canBeReplaced(mc.world, below)) {
                        this.startSneak();
                    }
                }
                if (ground != null && ground <= this.getExtend()) {
                    this.placement = this.predicted;
                    if (this.placement != null) {
                        this.lastPlacePos = this.placement.pos;
                        this.lastPlaceBox = this.placement.placedPos;
                    }
                }
            }
        } else {
            this.updateSafeWalk(null);
        }

        boolean rotatedThisTick = false;
        if (this.isRotationWindowActive() && this.rotationsEnabled.isToggled()) {
            boolean lookForward = this.shouldLookForward();
            boolean airborne = player.onGround || this.willLandOnBlock(player.velocityY) || lookForward;
            if (player.onGround) {
                this.firstTurn = true;
            }
            boolean forward = this.shouldRotateForward(!airborne);
            if (forward && airborne) {
                this.rotateTowardsMovement(this.getForwardRotationModel(), this.targetPitch, 0.3F);
                this.fastForward = true;
            } else {
                if (!forward) {
                    this.fastForward = false;
                }
                boolean snapGuard = this.snap.isToggled() && !this.isHypixel();
                if (!snapGuard || this.placement != null) {
                    this.rotateToPlacement(snapGuard);
                    rotatedThisTick = true;
                }
            }
        }
        // Adjust: mode.onPostRotation()
        if (this.isHypixel()) {
            this.stageTicks++;
            if (this.stage != Stage.BACK && this.stage != Stage.NONE) {
                this.forwardTicks++;
            }
        }
        if (this.placeReady && !rotatedThisTick) {
            this.placement = this.recompute(this.placement, null);
        }
    }

    // Adjust: ScaffoldModule.onPreUpdate (IngameActionEvent): place with the rotation that gets sent this tick
    private void onPlaceStage() {
        LocalClientPlayerEntity player = mc.player;
        Target current = this.placement;
        if (current != null) {
            float[] rot = this.nextRotation != null
                    ? this.nextRotation
                    : new float[] {RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]};
            HitResult hit = this.rayTrace(player.getEyePosition(1.0F), rot[0], rot[1]);
            if (hit != null
                    && hit.type == HitResult.Type.BLOCK
                    && hit.getPos().equals(current.pos)
                    && current.facing == hit.face) {
                this.placement = new Target(current.pos, current.facing, hit.facePos, rot[0], rot[1]);
            } else if (this.useRaycast()) {
                this.placement = null;
            }
        }
        if (this.placeReady && this.placement != null) {
            this.lastPlacePos = this.placement.pos;
            this.lastPlaceBox = this.placement.placedPos;
            this.handlePlacement(this.placement);
        }
    }

    private void handlePlacement(Target target) {
        LocalClientPlayerEntity player = mc.player;
        ItemStack held = player.getDisplayItemInHand();
        if (held == null || !(held.getItem() instanceof BlockItem)) {
            return;
        }
        if (!BlockUtils.canPlaceBlockOnSide(held, target.pos, target.facing)) {
            return;
        }
        boolean placed =
                mc.interactionManager.useBlock(player, mc.world, held, target.pos, target.facing, target.facePos);
        if (placed) {
            if (this.clientsideSwing.isToggled()) {
                player.swingArm();
            } else {
                mc.getNetworkHandler().sendPacket(new ArmSwingC2SPacket());
            }
        }
        this.lastPlacement = target;
        if (this.isHypixel()) {
            // Adjust: placementLimiter, nothing to do here
        } else if (this.canTowerNow() && target.facing == Direction.UP) {
            this.placedUp = true;
        }
        this.trackPlacement(target);
    }

    // ================================================================ rotation handling

    private float[] currentRotation(float yaw, float pitch) {
        return new float[] {yaw, pitch};
    }

    private float serverYaw() {
        return RotationUtils.serverRotations[0];
    }

    private float serverPitch() {
        return RotationUtils.serverRotations[1];
    }

    private boolean isRotationWindowActive() {
        return this.hasAngles && ROTATION_WINDOW > this.ticksSincePlace;
    }

    private void rotateTo(RotModel model, float yaw, float pitch) {
        float[] from = new float[] {this.serverYaw(), this.serverPitch()};
        this.nextRotation = applyModel(model, from, new float[] {yaw, pitch});
        this.requested = true;
        this.lastModel = model;
    }

    // sends the rotation; when nothing was requested, turns back to the camera like Adjust's RotationHandler
    private void finishRotation() {
        RotationHelper helper = RotationHelper.get();
        if (this.requested && this.nextRotation != null) {
            this.returning = true;
        } else if (this.returning) {
            float[] from = new float[] {this.serverYaw(), this.serverPitch()};
            float[] camera = new float[] {mc.player.yaw, mc.player.pitch};
            RotModel model = this.lastModel != null ? this.lastModel : new Linear(43.0);
            float[] next = applyModel(model, from, camera);
            if (Math.abs(MathHelper.wrapDegrees(next[0] - camera[0])) < 1.0F && Math.abs(next[1] - camera[1]) < 1.0F) {
                this.returning = false;
                return;
            }
            this.nextRotation = next;
        }
        if (this.nextRotation != null) {
            this.nextRotation[1] = RotationUtils.clampPitch(this.nextRotation[1]);
            helper.setRotations(this.nextRotation[0], this.nextRotation[1]);
            helper.forceMovementFix = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        if (this.nextRotation != null && Utils.nullCheck()) {
            e.claimExactRotations(this.nextRotation[0], this.nextRotation[1], false);
        }
    }

    private static float wrap(float f) {
        return MathHelper.wrapDegrees(f);
    }

    private static float[] applyModel(RotModel model, float[] from, float[] to) {
        float[] stepped = model.apply(from, to);
        float sensitivity = mc.options.mouseSensitivity * 0.6F + 0.2F;
        double gcd = (double) sensitivity * sensitivity * sensitivity * 8.0 * 0.15F;
        float yaw = (float) (Math.round((stepped[0] - from[0]) / gcd) * gcd + from[0]);
        float pitch = (float) (Math.round((stepped[1] - from[1]) / gcd) * gcd + from[1]);
        return new float[] {yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)};
    }

    private boolean useFastRotations() {
        return this.fastForward || this.isHypixel();
    }

    private RotModel getRotationModel(
            boolean instant, boolean first, boolean dryRun, float playerYaw, float targetYaw, float jitter) {
        if (!dryRun) {
            RotModel modeModel = this.getPlaceRotationModel();
            if (modeModel != null) {
                return modeModel;
            }
        }
        RotModel model;
        if (this.useFastRotations()) {
            float limit = first ? 73.0F : 43.0F;
            if (!first && Math.abs(wrap(playerYaw - targetYaw)) >= 1.0F) {
                limit -= jitter;
            }
            float sub =
                    dryRun ? (float) MOVEMENT_ADJUSTMENT + 1.0F : randomFloat(0.1F, (float) MOVEMENT_ADJUSTMENT + 1.0F);
            model = new Linear(limit - sub);
        } else {
            model = this.createSettingModel();
        }
        return instant ? Instant.INSTANCE : model;
    }

    private static float updateJitter(boolean dryRun, float playerYaw, float targetYaw, float jitter) {
        if (Math.abs(wrap(playerYaw - targetYaw)) < 1.0F) {
            return 0.0F;
        }
        if (jitter < 10.0F) {
            return jitter + (dryRun ? 4.0F : randomFloat(3.0F, 4.0F));
        }
        return jitter;
    }

    // Adjust: ScaffoldModule.k()
    private void rotateToPlacement(boolean instant) {
        RotModel model = this.getRotationModel(
                instant, this.firstTurn, false, this.serverYaw(), this.targetYaw, this.rotationJitter);
        final float[] current = new float[] {this.serverYaw(), this.serverPitch()};
        if (this.useFastRotations()) {
            if (this.firstTurn) {
                this.fastForward = false;
                this.rotationJitter = 0.0F;
                this.firstTurn = false;
            } else {
                this.rotationJitter = updateJitter(false, this.serverYaw(), this.targetYaw, this.rotationJitter);
            }
        } else {
            this.firstTurn = true;
        }
        RotFn fn = rot -> applyModel(model, current, rot);
        this.placement = this.recompute(this.placement, fn);
        if (this.placement == null) {
            this.rotateTo(model, this.targetYaw, this.targetPitch);
            return;
        }
        if (this.useRaycast()) {
            this.targetYaw = this.placement.yaw;
            this.targetPitch = this.placement.pitch;
            this.rotateTo(Instant.INSTANCE, this.targetYaw, this.targetPitch);
        } else {
            this.rotateTo(model, this.targetYaw, this.targetPitch);
        }
    }

    private int simulateRotationTicks(float[] target) {
        boolean first = mc.player.onGround || this.firstTurn;
        float jitter = this.rotationJitter;
        float[] current = new float[] {this.serverYaw(), this.serverPitch()};
        for (int ticks = 0; ticks < 4; ticks++) {
            RotModel model = this.getRotationModel(false, first, true, current[0], target[0], jitter);
            jitter = first ? 0.0F : updateJitter(true, current[0], target[0], jitter);
            first = false;
            float[] desired = applyModel(Instant.INSTANCE, current, target);
            float[] next = applyModel(model, current, target);
            if (Math.abs(wrap(next[0] - desired[0])) < 1.0E-3F && Math.abs(next[1] - desired[1]) < 1.0E-3F) {
                return ticks;
            }
            current = next;
        }
        return 4;
    }

    private void rotateTowardsMovement(RotModel model, float pitch, float yawSpread) {
        float yaw = mc.player.yaw;
        boolean back = mc.options.backKey.isPressed();
        if (mc.options.forwardKey.isPressed() || back) {
            if (back) {
                yaw += 180.0F;
            }
        } else {
            if (mc.options.leftKey.isPressed()) {
                yaw -= 90.0F;
            }
            if (mc.options.rightKey.isPressed()) {
                yaw += 90.0F;
            }
        }
        yaw += (RANDOM.nextFloat() * 2.0F - 1.0F) * yawSpread;
        float randomPitch = pitch + (RANDOM.nextFloat() - 0.5F) * 0.03F;
        this.lastForwardYaw = yaw;
        this.rotateTo(model, yaw, randomPitch);
    }

    // ================================================================ checks

    private boolean canPlaceNow() {
        if (this.hasPending()) {
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        if (player.hasItemInUse() || player.vehicle != null) {
            return false;
        }
        return !mc.options.attackKey.isPressed();
    }

    private boolean willLandOnBlock(double dy) {
        LocalClientPlayerEntity player = mc.player;
        if (player.velocityY >= 0.0 || player.onGround) {
            return false;
        }
        int level = this.noAscendActive ? this.baseY : (int) player.y;
        if (level <= player.y + dy) {
            return false;
        }
        return isColliding(player.getShape().expanded(0.0, dy, 0.0));
    }

    private boolean isSprintKeyDown() {
        if (mc.options.sprintKey.isPressed()) {
            return true;
        }
        Module sprint = ModuleManager.getModule(keystrokesmod.module.impl.movement.Sprint.class);
        return sprint != null && sprint.isEnabled();
    }

    private boolean shouldRotateForward(boolean checkSneak) {
        if (!this.allowForwardTurn() || !this.allowSprint.isToggled()) {
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        if (checkSneak && player.input != null && player.input.sneaking) {
            return false;
        }
        if (isColliding(player.getShape().moved(0.0, 1.0, 0.0))) {
            return false;
        }
        if (!mc.options.jumpKey.isPressed()) {
            if (!this.isNoAscendActive(false) || !this.autoJump.isToggled()) {
                return false;
            }
        }
        if (!this.isMoving() || !this.isSprintKeyDown() || this.rejectTicks >= 1) {
            return false;
        }
        return this.noAscendEnabled.isToggled();
    }

    private boolean allowForwardTurn() {
        if (!this.isHypixel()) {
            return true;
        }
        // Hypixel mode: no ascend on jump is forced, so holding jump never turns forward
        if (mc.options.jumpKey.isPressed()) {
            return false;
        }
        if (!this.snap.isToggled()) {
            return true;
        }
        float direction = Math.abs(this.getMovementDirection(mc.player.yaw) % 90.0F);
        return direction <= 5.0F || direction >= 85.0F;
    }

    private boolean isNoAscendActive(boolean retainAirborne) {
        if (!this.noAscendEnabled.isToggled() || this.hasPending()) {
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        if (this.isHypixel() && player.hasStatusEffect(StatusEffect.SPEED)) {
            return false;
        }
        boolean force = retainAirborne && this.noAscendActive && !player.onGround;
        if (mc.options.jumpKey.isPressed() && (this.isHypixel() || this.ascendOnJump.isToggled())) {
            return false;
        }
        if (this.towering) {
            return false;
        }
        if (this.towerEnabled.isToggled() && this.towerKeyOnGround) {
            return false;
        }
        if ((int) player.y - 1 >= 255) {
            return false;
        }
        if (!this.isMoving() && !force) {
            return false;
        }
        return !player.hasStatusEffect(StatusEffect.JUMP_BOOST);
    }

    private boolean isMoving() {
        return this.forwardInput() != 0.0F || this.strafeInput() != 0.0F;
    }

    private float forwardInput() {
        return (mc.options.forwardKey.isPressed() ? 1.0F : 0.0F) - (mc.options.backKey.isPressed() ? 1.0F : 0.0F);
    }

    private float strafeInput() {
        return (mc.options.leftKey.isPressed() ? 1.0F : 0.0F) - (mc.options.rightKey.isPressed() ? 1.0F : 0.0F);
    }

    private float getMovementDirection(float yaw) {
        float forward = this.forwardInput();
        float strafe = this.strafeInput();
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

    private boolean useRaycast() {
        return this.raycast.isToggled() || this.isHypixel();
    }

    // ================================================================ Hypixel mode (HypixelScaffoldMode)

    // The movement input was remapped by the movement fix relative to the last sent yaw, so simulate with that yaw
    // (Adjust's player.rotationYaw is the server rotation).
    private static SimulatedPlayer newSim(Input input) {
        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(input);
        sim.rotationYaw = RotationUtils.serverRotations[0];
        sim.rotationPitch = RotationUtils.serverRotations[1];
        return sim;
    }

    private Integer predictLandingTick() {
        int targetY = this.baseY;
        if (!this.noAscendActive) {
            targetY++;
        }
        SimulatedPlayer sim = newSim(mc.player.input);
        for (int tick = 1; tick <= 20; tick++) {
            sim.tick();
            double y = sim.getPos().y;
            if (sim.onGround) {
                return y >= targetY - 0.01 ? tick : null;
            }
            if (targetY - 1.0 > y) {
                return null;
            }
        }
        return null;
    }

    /**
     * Adjust: MovementFixModule "Optimize scaffold". Called by RotationHelper's movement fix with the camera move
     * direction; pulls it toward the center of the last placed block when that is 10-55° off.
     */
    public double optimizeMoveDirection(double desired) {
        LocalClientPlayerEntity player = mc.player;
        if (!this.centerMovement.isToggled()
                || player == null
                || this.stableYaw.isToggled() && player.onGround && !this.isJumpingInput()
                || this.lastPlacement == null
                || this.lastPlacement.placedPos == null) {
            return desired;
        }
        BlockPos pos = this.lastPlacement.placedPos;
        double dx = player.x - (pos.getX() + 0.5);
        double dz = player.z - (pos.getZ() + 0.5);
        if (Math.sqrt(dx * dx + dz * dz) <= 0.001) {
            return desired;
        }
        float towardCenter = yawBetween(
                new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                new Vec3d(player.x, player.y, player.z));
        double difference = Math.abs(MathHelper.wrapDegrees(towardCenter - desired));
        return difference > 10.0 && difference <= 55.0 ? towardCenter : desired;
    }

    private boolean isJumpingInput() {
        return mc.options.jumpKey.isPressed() || mc.player.input != null && mc.player.input.jumping;
    }

    // Adjust: ScaffoldSettings.getExtend, Hypixel mode has no extend
    private int getExtend() {
        return this.isHypixel() ? 0 : (int) this.extend.getInput();
    }

    private boolean willStepOffGround() {
        Input input = new Input();
        Input real = mc.player.input;
        input.movementForward = real.movementForward > 0.0F
                ? MathHelper.ceil(real.movementForward)
                : MathHelper.floor(real.movementForward);
        input.movementSideways = real.movementSideways > 0.0F
                ? MathHelper.ceil(real.movementSideways)
                : MathHelper.floor(real.movementSideways);
        input.jumping = false;
        input.sneaking = real.sneaking;
        SimulatedPlayer sim = newSim(input);
        sim.tick();
        return !sim.onGround;
    }

    private boolean shouldLookForward() {
        if (!this.isHypixel()) {
            return false;
        }
        if (!this.shouldRotateForward(false)) {
            this.stage = Stage.NONE;
            this.stageTicks = 0;
            this.forwardTicks = 0;
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        if (player.onGround) {
            if (this.stage != Stage.GROUND) {
                this.stage = Stage.GROUND;
                this.stageTicks = 0;
            }
            BlockPos below = new BlockPos(
                    MathHelper.floor(player.x), MathHelper.floor(player.y - 1.0), MathHelper.floor(player.z));
            if (!mc.world.getBlockState(below).getBlock().canBeReplaced(mc.world, below)) {
                this.lastGroundSupport = below;
            }
            return false;
        }
        switch (this.stage) {
            case BACK:
                if (this.predictLandingTick() != null) {
                    this.stage = Stage.RETURN;
                    this.stageTicks = 0;
                    return true;
                }
                return false;
            case RETURN:
                if (this.predictLandingTick() == null) {
                    this.stage = Stage.BACK;
                    this.stageTicks = 0;
                    this.forwardTicks = 0;
                    return false;
                }
                return true;
            default:
                boolean outOfReach = !this.noAscendActive;
                if (this.noAscendActive) {
                    BlockPos lastPlaced = this.lastPlacement != null ? this.lastPlacement.placedPos : null;
                    double reach = mc.interactionManager.getReach();
                    SimulatedPlayer sim = newSim(player.input);
                    Set<BlockPos> airColumns = new HashSet<>();
                    int simTicks = 0;
                    while (simTicks < 20) {
                        sim.tick();
                        simTicks++;
                        Vec3d pos = sim.getPos();
                        if (simTicks == 3) {
                            Vec3d eyes = pos.add(0.0, player.getEyeHeight(), 0.0);
                            double minDistance = Double.MAX_VALUE;
                            if (lastPlaced != null) {
                                minDistance =
                                        new Vec3d(lastPlaced).add(0.5, 0.5, 0.5).distanceTo(eyes);
                            }
                            if (this.lastGroundSupport != null) {
                                minDistance = Math.min(
                                        minDistance,
                                        new Vec3d(this.lastGroundSupport)
                                                .add(0.5, 0.5, 0.5)
                                                .distanceTo(eyes));
                            }
                            if (minDistance != Double.MAX_VALUE && minDistance > reach - 0.5) {
                                outOfReach = true;
                                break;
                            }
                        }
                        if (sim.onGround || pos.y < this.baseY) {
                            break;
                        }
                        for (double dz = -0.3; dz <= 0.3; dz += 0.6) {
                            for (double dx = -0.3; dx <= 0.3; dx += 0.6) {
                                BlockPos check = new BlockPos(pos.x + dx, this.baseY - 1, pos.z + dz);
                                if (mc.world.getBlockState(check).getBlock().canBeReplaced(mc.world, check)) {
                                    airColumns.add(check);
                                }
                            }
                        }
                    }
                    if (simTicks > airColumns.size() + 5 && !outOfReach) {
                        this.stage = Stage.FORWARD;
                        return true;
                    }
                }
                this.stage = Stage.BACK;
                this.stageTicks = 0;
                this.forwardTicks = 0;
                return false;
        }
    }

    private RotModel getForwardRotationModel() {
        if (!this.isHypixel()) {
            return Instant.INSTANCE;
        }
        if (mc.player.onGround && this.willStepOffGround()) {
            return Instant.INSTANCE;
        }
        int remaining = 2 - this.forwardTicks;
        if (remaining >= 2) {
            return new Weighted(FORWARD_WEIGHTS, remaining);
        }
        return Instant.INSTANCE;
    }

    private RotModel getPlaceRotationModel() {
        if (this.isHypixel() && this.stage == Stage.BACK && this.stageTicks < 3 && !mc.player.onGround) {
            return new Weighted(BACK_WEIGHTS, 3 - this.stageTicks);
        }
        return null;
    }

    private boolean canTowerNow() {
        if (!this.towerEnabled.isToggled()) {
            return false;
        }
        LocalClientPlayerEntity player = mc.player;
        if (player.isInWater() || player.isInLava() || player.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
            return false;
        }
        return mc.options.jumpKey.isPressed();
    }

    // Adjust: HypixelScaffoldMode.onMotion (MoveFlyingEvent post) / NormalScaffoldMode.onMotion
    private void tickTower() {
        LocalClientPlayerEntity player = mc.player;
        if (this.isHypixel()) {
            if (player.onGround || this.offGroundTicks >= 11) {
                this.towering = false;
            }
            if (!this.towerEnabled.isToggled() || this.offGroundTicks != 6 || this.lastMoveHadPosition) {
                return;
            }
            BlockPos below = new BlockPos(player.x, player.y - 2.0, player.z);
            if (mc.world.getBlockState(below).getBlock().canBeReplaced(mc.world, below)) {
                return;
            }
            // Adjust only pulls down on Hypixel; other anticheats (Grim) flag it as Simulation/GroundSpoof
            if (Utils.isHypixel() || Boolean.getBoolean("meower.forcehypixeltower")) {
                player.velocityY -= 0.25 - MOVEMENT_ADJUSTMENT * 0.001F;
            }
            this.towering = true;
            return;
        }
        if (!this.canTowerNow()) {
            this.towering = false;
            this.placedUp = false;
            return;
        }
        if (this.placedUp) {
            double jumpMotion = 0.42F;
            if (player.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
                jumpMotion += (player.getEffectInstance(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F;
            }
            if (!isColliding(player.getShape().moved(0.0, jumpMotion, 0.0))) {
                player.velocityY = jumpMotion;
            }
            this.towering = true;
        }
        int movement = (int) this.towerMovement.getInput();
        if (movement == 1) {
            player.velocityX = 0.0;
            player.velocityZ = 0.0;
        } else if (movement == 2) {
            double cx = Math.floor(player.x) + 0.5;
            double cz = Math.floor(player.z) + 0.5;
            player.velocityX = MathHelper.clamp(cx - player.x, -0.2, 0.2);
            player.velocityZ = MathHelper.clamp(cz - player.z, -0.2, 0.2);
        }
        this.placedUp = false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPlayerInput(PrePlayerInputEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        LocalClientPlayerEntity player = mc.player;
        // Adjust: ScaffoldModule.onMoveInput: auto jump while going flat (runs before the Hypixel jump hold below)
        boolean canAutoJump = this.isHypixel() ? this.rejectTicks < 1 : player.onGround && this.onGroundTicks >= 1;
        if (this.isRotationWindowActive()
                && this.noAscendActive
                && !this.hasPending()
                && !player.abilities.flying
                && this.autoJump.isToggled()
                && canAutoJump) {
            ((IAccessorEntityLivingBase) player).setJumpTicks(0);
            e.setJump(true);
        }
        // Adjust: HypixelScaffoldMode.onMoveInput: hold the jump until the server yaw matches the forward yaw
        if (this.isHypixel()
                && e.isJump()
                && player.onGround
                && this.stage == Stage.GROUND
                && this.shouldRotateForward(false)
                && this.requested
                && this.nextRotation != null) {
            float target = this.lastForwardYaw;
            boolean serverAligned = this.forwardTicks >= 2 || Math.abs(wrap(this.serverYaw() - target)) < 2.0F;
            if ((!serverAligned || Math.abs(wrap(this.nextRotation[0] - target)) >= 2.0F)
                    && !this.willStepOffGround()) {
                e.setJump(false);
            }
        }
        if (this.isHypixel() && this.towering) {
            e.setForward(0.0F);
            e.setStrafe(0.0F);
        }
    }

    private float lastForwardYaw;

    @SubscribeEvent
    public void onPreMotion(PreMotionEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        LocalClientPlayerEntity player = mc.player;
        if (player.onGround) {
            this.towerKeyOnGround = this.towerEnabled.isToggled() && mc.options.jumpKey.isPressed();
        }
        if (player.collidingVertically && !player.onGround) {
            this.keepBaseY = false;
        }
        if (player.onGround || this.baseY > player.y || this.towering) {
            if (this.keepBaseY) {
                this.keepBaseY = false;
            } else {
                this.baseY = (int) player.y;
            }
        }
    }

    // ================================================================ slot

    private boolean isUsableBlock(ItemStack stack) {
        if (stack == null || stack.size <= 0 || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (BlockUtils.isInteractable(block)
                || block instanceof DoorBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof StairsBlock
                || block instanceof WallBlock
                || block instanceof PumpkinBlock
                || block instanceof AbstractPressurePlateBlock
                || block instanceof PlantBlock
                || block instanceof FallingBlock
                || block == Blocks.TNT
                || block == Blocks.WEB
                || block == Blocks.LADDER) {
            return false;
        }
        return block.getMaxZ() - block.getMinZ() == 1.0
                && block.getMaxX() - block.getMinX() == 1.0
                && block.getMaxY() - block.getMinY() == 1.0;
    }

    private boolean selectBlock(boolean force) {
        LocalClientPlayerEntity player = mc.player;
        int held = player.inventory.selectedSlot;
        if (this.isUsableBlock(player.inventory.getItem(held))) {
            return true;
        }
        if (!this.changeSlot.isToggled() || !force) {
            return false;
        }
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.getItem(i);
            if (this.isUsableBlock(stack) && stack.size > bestCount) {
                best = i;
                bestCount = stack.size;
            }
        }
        if (best == -1) {
            return false;
        }
        player.inventory.selectedSlot = best;
        return true;
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START
                && Utils.nullCheck()
                && this.spoofItem.isToggled()
                && this.originalSlot != -1
                && this.originalSlot != mc.player.inventory.selectedSlot) {
            ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemInHandRenderer()).setCancelReset(true);
        }
    }

    private int getBlockCount() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getItem(i);
            if (this.isUsableBlock(stack)) {
                count += stack.size;
            }
        }
        return count;
    }

    // ================================================================ safe walk (SafeWalkHelper)

    private void updateSafeWalk(Integer ground) {
        safeWalkRequest = this.safeWalkEnabled.isToggled()
                && !this.safeWalkSneak.isToggled()
                && (this.noAscendActive ? 1 : 0) < this.onGroundTicks;
        if (ground == null) {
            this.stopSneak();
            return;
        }
        int delay = (int) this.earlySneak.getInput();
        if (delay >= 1) {
            delay = jitter(delay);
        }
        if (!this.safeWalkEnabled.isToggled() || !this.safeWalkSneak.isToggled() || ground + 1 > delay) {
            this.stopSneak();
            return;
        }
        LocalClientPlayerEntity player = mc.player;
        BlockPos below = new BlockPos(player.x, player.y - 2.0, player.z);
        if (canReplace(mc.world.getBlockState(below).getBlock(), below)) {
            this.startSneak();
        }
    }

    private void startSneak() {
        if (this.unsneakTicks == 0) {
            int delay = (int) this.unSneakDelay.getInput();
            this.unsneakTicks = delay < 1 ? 0 : jitter(delay);
        }
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), true);
        this.sneaking = true;
    }

    private void stopSneak() {
        if (!this.sneaking) {
            return;
        }
        if (this.unsneakTicks != 0) {
            this.unsneakTicks--;
            return;
        }
        KeyBinding.set(mc.options.sneakKey.getKeyCode(), Utils.isBindDown(mc.options.sneakKey));
        this.sneaking = false;
    }

    private static int jitter(int delay) {
        if (RANDOM.nextBoolean()) {
            return delay + (RANDOM.nextBoolean() ? 1 : 0);
        }
        return delay;
    }

    // ================================================================ placement tracker

    private boolean hasPending() {
        if (this.pendingPos == null) {
            return false;
        }
        if (System.currentTimeMillis() >= this.pendingUntil
                || this.pendingPos.distanceTo(mc.player.getCommandSourcePos()) > 3.0) {
            this.pendingPos = null;
            return false;
        }
        return true;
    }

    private void trackPlacement(Target target) {
        BlockPos placed = target.pos.offset(target.facing);
        Iterator<Record> it = this.records.iterator();
        while (it.hasNext()) {
            Record r = it.next();
            if (r.pos.equals(placed)) {
                if (!r.confirmed) {
                    return;
                }
                it.remove();
                break;
            }
        }
        this.records.add(new Record(placed, mc.player.getCommandSourcePos(), mc.player.inventory.selectedSlot));
    }

    private void removeExpired() {
        long fade = 550L + this.getPing();
        long now = System.currentTimeMillis();
        this.records.removeIf(r -> now - r.time >= fade);
    }

    private int getPing() {
        if (mc.getNetworkHandler() == null) {
            return 0;
        }
        net.minecraft.client.network.PlayerInfo info = mc.getNetworkHandler().getOnlinePlayer(mc.player.getUuid());
        return info == null ? 0 : Math.max(0, info.getPing());
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof BlockUpdateS2CPacket) {
            final BlockUpdateS2CPacket packet = (BlockUpdateS2CPacket) e.getPacket();
            mc.executeTask(() -> this.onBlockChange(packet));
        } else if (e.getPacket() instanceof PlayerMoveS2CPacket) {
            mc.executeTask(() -> {
                this.towering = false;
            });
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof PlayerMoveC2SPacket) {
            this.lastMoveHadPosition = ((PlayerMoveC2SPacket) e.getPacket()).hasPos();
        }
    }

    private void onBlockChange(BlockUpdateS2CPacket packet) {
        if (mc.player == null || !this.isEnabled()) {
            return;
        }
        Record entry = null;
        for (Record r : this.records) {
            if (r.pos.equals(packet.getPos())) {
                entry = r;
                break;
            }
        }
        if (entry == null) {
            return;
        }
        boolean wasConfirmed = entry.confirmed;
        entry.confirmed = true;
        boolean accepted = packet.getBlockState().getBlock() != Blocks.AIR;
        if (accepted) {
            if (!wasConfirmed && this.rejectTicks >= 1) {
                this.rejectTicks--;
            }
            return;
        }
        if (this.isHypixel()) {
            this.rejectTicks = 9;
            this.pendingPos = entry.playerPos;
            this.pendingUntil = System.currentTimeMillis() + 750L;
        }
        if (this.autoStasis.isToggled()) {
            Stasis stasis = (Stasis) ModuleManager.getModule(Stasis.class);
            if (stasis != null && !stasis.isEnabled()) {
                stasis.enableAuto();
                Utils.sendMessage("&7[Scaffold] &cBlock rejected, Stasis on");
            }
        }
    }

    // ================================================================ placement prediction (PlacementPredictor)

    private Integer predict(float[] rotation) {
        this.predicted = null;
        LocalClientPlayerEntity player = mc.player;
        // one simulation gives every future state: state[t] = player after t ticks
        List<State> states = new ArrayList<>(10);
        states.add(new State(player));
        SimulatedPlayer sim = newSim(player.input);
        for (int i = 0; i < 8; i++) {
            sim.tick();
            states.add(new State(sim));
        }
        int mode = (int) this.noAscendMode.getInput();
        for (int pass = 0; pass < 2; pass++) {
            boolean secondPass = pass != 0;
            for (int tick = 0; tick < 5; tick++) {
                State current = states.get(tick);
                List<State> path = states.subList(tick, tick + 5);
                State first = path.get(0);
                State second = path.get(1);
                this.geometryYaw = yawBetween(second.pos(), first.pos());
                double heightAbove = current.y - this.baseY;
                boolean lowOnly;
                boolean noAscend;
                boolean airborneOk;
                boolean buffering;
                boolean downward;
                if (this.towering) {
                    lowOnly = this.isHypixel();
                    noAscend = false;
                    airborneOk = false;
                    buffering = false;
                    downward = false;
                } else if (!this.noAscendActive) {
                    lowOnly = false;
                    noAscend = false;
                    airborneOk = false;
                    buffering = false;
                    downward = false;
                } else if (this.keepBaseY) {
                    lowOnly = false;
                    noAscend = true;
                    airborneOk = false;
                    buffering = false;
                    downward = true;
                } else if ((int) current.y - 1 < 255) {
                    boolean falling = mode == 1 && current.fallDistance > 0.0F;
                    boolean belowNextLevel = false;
                    if (mode == 1 && falling) {
                        double nextLevel = this.baseY + 1;
                        belowNextLevel = nextLevel > current.y + current.velocityY && !(current.y < nextLevel);
                    }
                    lowOnly = belowNextLevel;
                    noAscend = true;
                    airborneOk = false;
                    buffering = falling;
                    downward = !falling;
                } else {
                    lowOnly = false;
                    noAscend = true;
                    airborneOk = true;
                    buffering = true;
                    downward = false;
                }

                Vec3d position = new Vec3d(current.x, downward ? (double) this.baseY : current.y, current.z);
                BlockPos playerBlock = new BlockPos(position);
                Box playerBox = playerBoundsAt(position);
                Set<BlockPos> supports = new LinkedHashSet<>();
                Target pendingTarget = this.lastPlacement;
                BlockPos referencePos = null;
                if (second.y - first.y < -0.5 && pendingTarget != null && pendingTarget.placedPos != null) {
                    referencePos = pendingTarget.placedPos.getY() > first.y ? null : pendingTarget.placedPos;
                }
                List<BlockPos> free = new ArrayList<>();
                List<BlockPos> preferred = new ArrayList<>();
                boolean jumpKey = mc.options.jumpKey.isPressed();
                double speed = Math.hypot(player.velocityX, player.velocityZ);
                for (int dx = -4; dx < 4; dx++) {
                    for (int dy = -3; dy < 0; dy++) {
                        for (int dz = -4; dz < 4; dz++) {
                            BlockPos candidate = new BlockPos(
                                    (int) Math.floor(position.x + dx), (int) Math.floor(position.y + dy), (int)
                                            Math.floor(position.z + dz));
                            if (!hasCollision(candidate)) {
                                continue;
                            }
                            free.add(candidate);
                            for (Direction facing : Direction.values()) {
                                BlockPos neighbour = candidate.offset(facing);
                                if (lowOnly && neighbour.getY() != (int) position.y - 1) {
                                    continue;
                                }
                                if (facing == Direction.UP) {
                                    if (path.get(0).onGround) {
                                        continue;
                                    }
                                    if (neighbour.getX() != playerBlock.getX()
                                            || neighbour.getZ() != playerBlock.getZ()) {
                                        continue;
                                    }
                                } else if (jumpKey && !this.isMoving() && speed < 0.035 && current.velocityY > -0.2) {
                                    continue;
                                }
                                double cx = neighbour.getX() + 0.5;
                                double cz = neighbour.getZ() + 0.5;
                                double horizontal = Math.sqrt(
                                        (cx - position.x) * (cx - position.x) + (cz - position.z) * (cz - position.z));
                                float angle = (float) (Math.atan2(position.z - cz, position.x - cx) * 180.0 / Math.PI)
                                        - 90.0F;
                                double factor = isDiagonalYaw(angle) ? 1.5 : 1.0;
                                int id = Math.min((int) (horizontal * factor + 0.7), path.size() - 1);
                                State expected = path.get(id);
                                if (neighbour.getY() > expected.y
                                        && (referencePos == null || !candidate.equals(referencePos))) {
                                    continue;
                                }
                                Box cell = blockBox(neighbour);
                                if (cell.intersects(playerBox)) {
                                    continue;
                                }
                                supports.add(neighbour);
                                if (referencePos != null && candidate.equals(referencePos)) {
                                    preferred.add(candidate);
                                } else if (pendingTarget != null
                                        && pendingTarget.facing != Direction.UP
                                        && facing == Direction.UP
                                        && candidate.equals(pendingTarget.placedPos)) {
                                    preferred.add(candidate);
                                }
                            }
                        }
                    }
                }

                List<BlockPos> sorted = new ArrayList<>(supports);
                sorted.sort(Comparator.comparingDouble(pos -> {
                    double distance = this.distanceTo(position, pos, true);
                    return preferred.contains(pos) ? distance * 0.01 : distance;
                }));

                if (this.stableYaw.isToggled()
                        && this.rotationsEnabled.isToggled()
                        && player.onGround
                        && !jumpKey
                        && !this.towering
                        && !noAscend) {
                    List<Vec3d> pathPositions = new ArrayList<>();
                    for (State s : path) {
                        pathPositions.add(s.pos());
                    }
                    Target solved = this.solveAlongPath(current, sorted, pathPositions, rotation, secondPass, tick);
                    if (solved == null) {
                        continue;
                    }
                    return this.accept(solved, tick);
                }

                this.yawOffset = null;
                double minimumDistance = 0.0;
                boolean any = false;
                for (BlockPos pos : free) {
                    double d = this.distanceTo(position, pos, false);
                    if (!any || d < minimumDistance) {
                        minimumDistance = d;
                        any = true;
                    }
                }

                for (BlockPos pos : sorted) {
                    double distance = this.distanceTo(position, pos, false);
                    if (minimumDistance < distance) {
                        continue;
                    }
                    Target target = this.findBestPlacement(current.eyes(), pos, rotation, null);
                    if (target == null) {
                        continue;
                    }
                    boolean markPending = false;
                    if (target.facing == Direction.UP) {
                        if (!buffering && pos.getY() >= 255) {
                            continue;
                        }
                        if (noAscend && !lowOnly) {
                            if (heightAbove < 1.0 || heightAbove >= 2.0 || this.keepBaseY || !buffering) {
                                continue;
                            }
                            if (current.velocityY < 0.0) {
                                if (!airborneOk && mode == 1) {
                                    Vec3d next = new Vec3d(
                                            current.x + player.velocityX,
                                            current.y + player.velocityY,
                                            current.z + player.velocityZ);
                                    if (!blockBox(pos).intersects(playerBoundsAt(next))) {
                                        continue;
                                    }
                                }
                                markPending = true;
                            }
                        } else if (lowOnly && current.velocityY < 0.0) {
                            markPending = true;
                        }
                    } else if (!noAscend || downward) {
                        if (lowOnly && current.velocityY < 0.0) {
                            markPending = true;
                        }
                    } else {
                        BlockPos below = new BlockPos(current.x, this.baseY - 1, current.z);
                        if (!canReplace(mc.world.getBlockState(below).getBlock(), below)) {
                            break;
                        }
                        if (lowOnly && current.velocityY < 0.0) {
                            markPending = true;
                        }
                    }
                    if (markPending) {
                        this.keepBaseY = true;
                    }
                    return this.accept(target, tick);
                }
            }
            if (!(this.stableYaw.isToggled() && this.yawOffset != null)) {
                return null;
            }
        }
        return null;
    }

    private Integer accept(Target target, int tick) {
        this.ticksSincePlace = 0;
        this.targetYaw = target.yaw;
        this.targetPitch = target.pitch;
        this.predicted = target;
        return tick;
    }

    // ================================================================ geometry (PlacementFinder)

    private double distanceTo(Vec3d from, BlockPos pos, boolean horizontalOnly) {
        double radians = Math.toRadians(this.geometryYaw);
        double sx = pos.getX() + 0.5 - Math.sin(radians) * 0.2;
        double sy = pos.getY() + 0.5;
        double sz = pos.getZ() + 0.5 + Math.cos(radians) * 0.2;
        double dx = from.x - sx;
        double dz = from.z - sz;
        if (!horizontalOnly) {
            double dy = from.y - sy;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        return Math.sqrt(dx * dx + dz * dz);
    }

    private Target recompute(Target previous, RotFn fn) {
        if (previous == null) {
            return null;
        }
        if (!this.useRaycast()) {
            return previous;
        }
        LocalClientPlayerEntity player = mc.player;
        final float[] current = new float[] {this.serverYaw(), this.serverPitch()};
        RotFn filter = fn != null ? fn : ignored -> current;
        BlockPos target = previous.placedPos;
        if (this.stableYaw.isToggled()
                && this.yawOffset != null
                && player.onGround
                && !mc.options.jumpKey.isPressed()
                && !this.towering
                && !this.noAscendActive) {
            State self = new State(player);
            Target result = this.solve(self, Collections.singletonList(target), current, false, 0, filter);
            if (result != null) {
                return result;
            }
            return this.solve(self, Collections.singletonList(target), current, true, 0, filter);
        }
        return this.findBestPlacement(player.getEyePosition(1.0F), target, current, filter);
    }

    private List<Candidate> findPlacementCandidates(Vec3d eyes, BlockPos pos) {
        List<Candidate> candidates = new ArrayList<>();
        Box box = blockBox(pos);
        if (!mc.world.isUnobstructed(box, mc.player)) {
            return candidates;
        }
        if (getCollisionBox(pos) != null) {
            return candidates;
        }
        for (Direction facing : Direction.values()) {
            BlockPos neighbour = pos.offset(facing);
            BlockState state = mc.world.getBlockState(neighbour);
            Block block = state.getBlock();
            if (!canReplace(block, neighbour)) {
                Box neighbourBox = block.getCollisionShape(mc.world, neighbour, state);
                if (neighbourBox != null) {
                    sampleFace(eyes, neighbourBox, facing, candidates);
                }
            }
        }
        return candidates;
    }

    private static void sampleFace(Vec3d eyes, Box box, Direction facing, List<Candidate> out) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        double random1 = Math.random() * 0.045 + 0.9;
        double random2 = Math.random() * 0.045 + 0.9;
        Direction side = facing.getOpposite();
        double xHi = (maxX - minX) * random1 + minX;
        double xLo = 1.0 - random1 + minX;
        boolean xFixed = false;
        if (side == Direction.EAST) {
            xFixed = true;
            xHi = maxX;
            xLo = maxX;
        }
        if (side == Direction.WEST) {
            xFixed = true;
            xHi = minX;
            xLo = minX;
        }
        double yHi = (maxY - minY) * random2 + minY;
        double yLo = 1.0 - random2 + minY;
        boolean yFixed = false;
        if (side == Direction.UP) {
            yFixed = true;
            yHi = maxY;
            yLo = maxY;
        }
        if (side == Direction.DOWN) {
            yFixed = true;
            yHi = minY;
            yLo = minY;
        }
        double zHi = random1 * (maxZ - minZ) + minZ;
        double zLo = 1.0 - random1 + minZ;
        boolean zFixed = false;
        if (side == Direction.SOUTH) {
            zFixed = true;
            zHi = maxZ;
            zLo = maxZ;
        }
        if (side == Direction.NORTH) {
            zFixed = true;
            zHi = minZ;
            zLo = minZ;
        }
        int freeAxes = (zFixed ? 0 : 1) + (yFixed ? 0 : 1) + (xFixed ? 0 : 1);
        int xSteps = xFixed ? 1 : 8;
        int ySteps = yFixed ? 1 : 8;
        int zSteps = zFixed ? 1 : 8;
        double centreX = (maxX + minX) / 2.0;
        double centreY = (maxY + minY) / 2.0;
        double centreZ = (maxZ + minZ) / 2.0;
        for (int i = 0; i < xSteps; i++) {
            double x = xFixed ? xLo : i * (xHi - xLo) / (xSteps - 1) + xLo;
            for (int j = 0; j < ySteps; j++) {
                double y = yFixed ? yLo : j * (yHi - yLo) / (ySteps - 1) + yLo;
                for (int k = 0; k < zSteps; k++) {
                    double z = zFixed ? zLo : (zHi - zLo) * k / (zSteps - 1) + zLo;
                    if (freeAxes > 0) {
                        boolean nearCentre = true;
                        if (!xFixed) {
                            nearCentre = Math.abs(x - centreX) < 0.2;
                        }
                        if (!yFixed) {
                            nearCentre &= Math.abs(y - centreY) < 0.2;
                        }
                        if (!zFixed) {
                            nearCentre &= Math.abs(z - centreZ) < 0.2;
                        }
                        if (nearCentre) {
                            continue;
                        }
                    }
                    double dx = x - eyes.x;
                    double dy = y - eyes.y;
                    double dz = z - eyes.z;
                    double horizontal = Math.sqrt(dx * dx + dz * dz);
                    float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
                    float pitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
                    out.add(new Candidate(yaw, pitch));
                }
            }
        }
    }

    private Target findBestPlacement(Vec3d eyes, BlockPos target, final float[] current, RotFn fn) {
        List<Candidate> candidates = this.findPlacementCandidates(eyes, target);
        candidates.sort(Comparator.comparingDouble(c -> {
            float yawDelta = Math.abs(wrap(current[0] - c.yaw));
            float pitchDelta = Math.abs(current[1] - c.pitch);
            return pitchDelta * pitchDelta + yawDelta * yawDelta;
        }));
        Set<BlockPos> targets = Collections.singleton(target);
        for (Candidate candidate : candidates) {
            Target placement = this.raycastPlacement(eyes, new float[] {candidate.yaw, candidate.pitch}, targets, fn);
            if (placement != null) {
                return placement;
            }
        }
        return null;
    }

    private HitResult rayTrace(Vec3d eyes, float yaw, float pitch) {
        double reach = mc.interactionManager.getReach();
        Vec3d look = getRotationVector(pitch, yaw);
        Vec3d end = eyes.add(look.x * reach, look.y * reach, look.z * reach);
        return mc.world.rayTrace(eyes, end, false, false, true);
    }

    private Target raycastPlacement(Vec3d eyes, float[] rotation, Collection<BlockPos> targets, RotFn fn) {
        float[] rot = fn == null ? rotation : fn.apply(rotation);
        float pitch = MathHelper.clamp(rot[1], -90.0F, 90.0F);
        HitResult mop = this.rayTrace(eyes, rot[0], pitch);
        if (mop == null || mop.type != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos hitPos = mop.getPos();
        if (!targets.contains(hitPos.offset(mop.face))) {
            return null;
        }
        if (canReplace(mc.world.getBlockState(hitPos).getBlock(), hitPos) || getCollisionBox(hitPos) == null) {
            return null;
        }
        Target placement = new Target(hitPos, mop.face, mop.facePos, rot[0], pitch);
        return placement.isInvalidHitVec() ? null : placement;
    }

    // ================================================================ stable yaw (RotationSolver)

    private Target solve(
            State player, Collection<BlockPos> positions, float[] current, boolean strict, int maxBlocks, RotFn fn) {
        int belowY = (int) Math.floor(mc.player.y) - 1;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : positions) {
            if (pos.getY() != belowY || getCollisionBox(pos) != null) {
                continue;
            }
            if (!canReplace(mc.world.getBlockState(pos).getBlock(), pos)) {
                continue;
            }
            if (mc.world.isUnobstructed(blockBox(pos), mc.player)) {
                candidates.add(pos);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        float f1 = mc.player.yaw; // camera yaw
        List<Float> yaws = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) {
            yaws.add(f1 + i * 45.0F);
        }
        float moveYaw = this.getMovementDirection(f1);
        boolean moveDiagonal = isDiagonalYaw(moveYaw);
        boolean f1Diagonal = isDiagonalYaw(f1);
        float preferredYaw = this.yawOffset != null ? f1 + this.yawOffset : current[0];
        float axisDeviation = wrap(moveYaw - Math.round(moveYaw / 90.0F) * 90.0F);
        float backward = moveYaw + 180.0F;
        float backwardDelta = wrap(backward - f1);
        float absDeviation = Math.abs(axisDeviation);
        float diagonalTarget =
                absDeviation <= 1.0F ? preferredYaw : Math.round((backward - 45.0F) / 90.0F) * 90.0F + 45.0F;
        float nearest45 = Math.round(backwardDelta / 45.0F) * 45.0F + f1;
        boolean notF1Diagonal = !f1Diagonal;
        final float preferredYawF = preferredYaw;
        final Float offset = this.yawOffset;
        Comparator<Float> order = Comparator.<Float>comparingDouble(yaw -> {
                    if (strict && offset != null) {
                        return Math.abs(wrap(preferredYawF - yaw));
                    }
                    return 0.0;
                })
                .thenComparingInt(yaw -> {
                    if (moveDiagonal && Math.abs(wrap(yaw - nearest45)) < 0.01F) {
                        return -1;
                    }
                    float relative = (yaw - f1) % 360.0F;
                    if (relative < 0.0F) {
                        relative += 360.0F;
                    }
                    boolean oddMultiple = angularDistance(relative, 45.0F) <= 22.5F
                            || angularDistance(relative, 135.0F) <= 22.5F
                            || angularDistance(relative, 225.0F) <= 22.5F
                            || angularDistance(relative, 315.0F) <= 22.5F;
                    return oddMultiple == notF1Diagonal ? 0 : 1;
                })
                .thenComparingDouble(yaw -> Math.abs(wrap(diagonalTarget - yaw)))
                .thenComparingDouble(yaw -> Math.abs(wrap(preferredYawF - yaw)));
        yaws.sort(order);

        List<FaceBox> faces = new ArrayList<>();
        for (BlockPos candidate : candidates) {
            for (Direction facing : Direction.BY_ID_HORIZONTAL) {
                BlockPos neighbour = candidate.offset(facing);
                BlockState state = mc.world.getBlockState(neighbour);
                Box box = state.getBlock().getCollisionShape(mc.world, neighbour, state);
                if (box != null) {
                    faces.add(new FaceBox(box, facing.getOpposite()));
                }
            }
        }

        if (absDeviation > 7.5F) {
            if (strict && this.yawOffset != null) {
                yaws.removeIf(yaw -> Math.abs(wrap(yaw - nearest45)) < 0.01F);
                yaws.add(0, nearest45);
            }
        } else {
            yaws.removeIf(yaw -> Math.abs(wrap(yaw - nearest45)) < 0.01F);
        }

        float yawDelta = wrap(preferredYaw - f1);
        if (this.yawOffset != null && !strict && !yaws.isEmpty()) {
            float chosen;
            if (!moveDiagonal) {
                float snapped = Math.round(yawDelta / 45.0F) * 45.0F + f1;
                boolean useSnapped = false;
                if (absDeviation > 7.5F || Math.abs(wrap(snapped - nearest45)) >= 0.01F) {
                    useSnapped = isDiagonalYaw(snapped - f1) == notF1Diagonal;
                }
                chosen = useSnapped ? snapped : yaws.get(0);
            } else {
                chosen = nearest45;
            }
            yaws.clear();
            yaws.add(chosen);
        }

        Target result = null;
        float chosenYaw = 0.0F;
        boolean fromCurrent = false;
        boolean tryCandidates = false;
        if (strict && this.yawOffset != null) {
            float diff = wrap(preferredYaw - current[0]);
            if (45.0F > Math.abs(diff)) {
                result = this.solvePitch(player, candidates, faces, current, fn, current[0]);
                if (result != null) {
                    fromCurrent = true;
                    chosenYaw = current[0];
                } else {
                    tryCandidates = true;
                }
            } else {
                tryCandidates = true;
            }
        }
        if (result == null && tryCandidates) {
            List<Candidate> rotations = new ArrayList<>();
            for (BlockPos candidate : candidates) {
                rotations.addAll(this.findPlacementCandidates(player.eyes(), candidate));
            }
            rotations.removeIf(c -> Math.abs(wrap(preferredYawF - c.yaw)) >= 45.0F);
            rotations.sort(Comparator.<Candidate>comparingDouble(c -> Math.abs(wrap(current[0] - c.yaw)))
                    .thenComparingDouble(c -> Math.abs(current[1] - c.pitch)));
            for (Candidate candidate : rotations) {
                Target ray = this.raycastPlacement(
                        player.eyes(), new float[] {candidate.yaw, candidate.pitch}, candidates, fn);
                if (ray != null && ray.facing != Direction.UP && ray.facing != Direction.DOWN) {
                    result = ray;
                    fromCurrent = true;
                    chosenYaw = candidate.yaw;
                    break;
                }
            }
        }
        if (result == null) {
            for (Float yaw : yaws) {
                result = this.solvePitch(player, candidates, faces, current, fn, yaw);
                if (result != null) {
                    fromCurrent = false;
                    chosenYaw = yaw;
                    break;
                }
            }
        }
        if (result == null) {
            return null;
        }
        if (fn == null && strict && this.yawOffset != null) {
            if (maxBlocks > this.simulateRotationTicks(new float[] {result.yaw, result.pitch}) + this.getExtend()) {
                return null;
            }
        }
        float difference = wrap(preferredYaw - chosenYaw);
        boolean farFromPreferred = Math.abs(difference) > 0.01F && 45.0F > Math.abs(difference);
        if (this.yawOffset == null) {
            farFromPreferred = false;
        }
        if (farFromPreferred || fromCurrent) {
            return result;
        }
        if (!moveDiagonal && this.yawOffset != null && Math.abs(wrap(chosenYaw - nearest45)) < 0.01F) {
            return result;
        }
        this.yawOffset = wrap(chosenYaw - f1);
        return result;
    }

    private Target solveAlongPath(
            State player, List<BlockPos> blocks, List<Vec3d> path, float[] current, boolean strict, int maxBlocks) {
        int belowY = (int) Math.floor(mc.player.y) - 1;
        Box gap = null;
        for (Vec3d position : path) {
            Box candidateGap = new Box(
                    position.x - 0.3F,
                    belowY + 0.99,
                    position.z - 0.3F,
                    position.x + 0.3F,
                    belowY + 1.0,
                    position.z + 0.3F);
            if (!isColliding(candidateGap)) {
                gap = candidateGap;
                break;
            }
        }
        if (gap == null) {
            return null;
        }
        List<BlockPos> nearby = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (gap.intersects(blockBox(pos))) {
                nearby.add(pos);
            }
        }
        if (nearby.isEmpty()) {
            for (BlockPos pos : blocks) {
                if (pos.getY() != belowY) {
                    continue;
                }
                BlockState state = mc.world.getBlockState(pos);
                Block block = state.getBlock();
                if (block.getCollisionShape(mc.world, pos, state) != null || !canReplace(block, pos)) {
                    continue;
                }
                for (Direction facing : Direction.BY_ID_HORIZONTAL) {
                    BlockPos shifted = pos.offset(facing);
                    if (!gap.intersects(blockBox(shifted))) {
                        continue;
                    }
                    if (canReplace(mc.world.getBlockState(shifted).getBlock(), shifted)) {
                        nearby.add(pos);
                        break;
                    }
                }
            }
        }
        return this.solve(player, nearby, current, strict, maxBlocks, null);
    }

    private Target solvePitch(
            State player, Collection<BlockPos> positions, List<FaceBox> faces, float[] current, RotFn fn, float yaw) {
        float yawDelta = wrap(yaw - this.serverYaw());
        float basePitch = MathHelper.clamp(current[1], 0.0F, 90.0F);
        double reach = mc.interactionManager.getReach();
        float lookYaw = yawDelta + this.serverYaw();
        double playerX = player.x;
        double eyeY = player.y + mc.player.getEyeHeight();
        double playerZ = player.z;
        Vec3d look = getRotationVector(0.0F, lookYaw);
        double dx = reach * look.x;
        double dy = reach * look.y;
        double dz = reach * look.z;
        List<Float> pitches = new ArrayList<>();
        pitches.add(basePitch);
        for (FaceBox face : faces) {
            Box box = face.box;
            double centerY = (box.minY + box.maxY) * 0.5;
            Vec3d start = new Vec3d(playerX, centerY, playerZ);
            Vec3d end = new Vec3d(playerX + dx, centerY + dy, playerZ + dz);
            HitResult hit = box.clip(start, end);
            if (hit == null || hit.face != face.facing) {
                continue;
            }
            float distF = (float) Math.sqrt((hit.facePos.x - playerX) * (hit.facePos.x - playerX)
                    + (hit.facePos.z - playerZ) * (hit.facePos.z - playerZ));
            if (0.0F >= distF || distF >= (float) reach) {
                continue;
            }
            float lo = Math.max(0.0F, (float) Math.toDegrees(Math.atan2(eyeY - box.maxY, distF)));
            float upperByBox = (float) Math.toDegrees(Math.atan2(eyeY - box.minY, distF));
            float upperByReach = (float) Math.toDegrees(Math.acos(distF / reach));
            float hi = Math.min(upperByBox, upperByReach);
            if (lo >= hi) {
                continue;
            }
            float margin = Math.min((hi - lo) * 0.1F, 0.05F);
            float hiInner = hi - margin;
            float loInner = lo + margin;
            float firstPitch = loInner > basePitch ? loInner : basePitch > hiInner ? hiInner : basePitch;
            pitches.add(firstPitch);
            float span = hiInner - loInner;
            for (int i = 0; i <= 8; i++) {
                pitches.add(span * i / 8.0F + loInner);
            }
        }
        pitches.sort(Comparator.comparingDouble(p -> Math.abs(p - basePitch)));
        for (Float pitch : pitches) {
            Target ray = this.raycastPlacement(player.eyes(), new float[] {lookYaw, pitch}, positions, fn);
            if (ray != null && ray.facing != Direction.UP && ray.facing != Direction.DOWN) {
                return ray;
            }
        }
        return null;
    }

    // ================================================================ render

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || !this.blockPlacements.isToggled()) {
            return;
        }
        if (this.lastPlaceBox != null) {
            RenderUtils.renderBlock(this.lastPlaceBox, 0x3CC87820, true, true);
        }
        if (this.lastPlacePos != null) {
            RenderUtils.renderBlock(this.lastPlacePos, 0x3C0ADC20, true, true);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(ForgeEvents.TickEvent.RenderTickEvent e) {
        // Feral has no RenderGameOverlayEvent: drawn at the end of the render tick (like Scaffold's block count).
        if (e.phase != ForgeEvents.TickEvent.Phase.END
                || !Utils.nullCheck()
                || mc.options.hideGui
                || !this.blockCount.isToggled()
                || mc.screen != null) {
            return;
        }
        Window sr = new Window(mc);
        int count = this.getBlockCount();
        String text = count + " blocks";
        int color = count > 64 ? 0xFF55FF55 : count > 16 ? 0xFFFFFF55 : 0xFFFF5555;
        mc.textRenderer.drawWithShadow(
                text,
                sr.getWidth() / 2.0F - mc.textRenderer.getWidth(text) / 2.0F,
                sr.getHeight() / 2.0F + 12.0F,
                color);
    }

    // ================================================================ world helpers

    private static boolean canReplace(Block block, BlockPos pos) {
        return block instanceof LiquidBlock || block.canBeReplaced(mc.world, pos);
    }

    private static Box getCollisionBox(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock().getCollisionShape(mc.world, pos, state);
    }

    private static boolean hasCollision(BlockPos pos) {
        Box box = getCollisionBox(pos);
        return box != null && box.maxY - box.minY != 0.0;
    }

    private static boolean isColliding(Box box) {
        for (int x = MathHelper.floor(box.minX); x < MathHelper.floor(box.maxX + 1.0); x++) {
            for (int z = MathHelper.floor(box.minZ); z < MathHelper.floor(box.maxZ + 1.0); z++) {
                for (int y = MathHelper.floor(box.minY) - 1; y < MathHelper.floor(box.maxY + 1.0); y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(pos);
                    List<Box> collisions = new ArrayList<>(1);
                    state.getBlock().addCollisions(mc.world, pos, state, box, collisions, null);
                    if (!collisions.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Box blockBox(BlockPos pos) {
        return new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    private static Box playerBoundsAt(Vec3d position) {
        return new Box(
                position.x - 0.3F,
                position.y,
                position.z - 0.3F,
                position.x + 0.3F,
                position.y + 1.8F,
                position.z + 0.3F);
    }

    private static Vec3d getRotationVector(float pitch, float yaw) {
        float f = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float f1 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float f2 = -MathHelper.cos(-pitch * 0.017453292F);
        float f3 = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3d(f1 * f2, f3, f * f2);
    }

    private static float yawBetween(Vec3d target, Vec3d origin) {
        double x = target.x - origin.x;
        double z = target.z - origin.z;
        return (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
    }

    private static boolean isDiagonalYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized < 0.0F) {
            normalized += 360.0F;
        }
        for (float diagonal : new float[] {45.0F, 135.0F, 225.0F, 315.0F}) {
            float delta = Math.abs(normalized - diagonal);
            if (Math.min(delta, 360.0F - delta) <= 22.5F) {
                return true;
            }
        }
        return false;
    }

    private static float angularDistance(float a, float b) {
        float d = Math.abs(a - b);
        return Math.min(d, 360.0F - d);
    }

    private static float randomFloat(float min, float max) {
        return min + RANDOM.nextFloat() * (max - min);
    }

    // ================================================================ small types

    private interface RotFn {
        float[] apply(float[] rotation);
    }

    private interface RotModel {
        float[] apply(float[] from, float[] to);
    }

    private static final class Instant implements RotModel {
        static final Instant INSTANCE = new Instant();

        @Override
        public float[] apply(float[] from, float[] to) {
            return new float[] {from[0] + wrap(to[0] - from[0]), to[1]};
        }
    }

    // Adjust: RotationMode factories (rotation/RotationMode.java)
    private RotModel createSettingModel() {
        double max = this.maxAngle.getInput();
        switch ((int) this.rotationMode.getInput()) {
            case 0:
                return new LinearAngle(max + randomFloat(0.01F, 0.05F));
            case 1:
                return Distributed.INSTANCE;
            case 2:
                return new Step(max, Math.min(Math.max(max - 5.0, 5.0), this.stepAngle.getInput()));
            case 3:
                return new Organic(max, this.driftIntensity.getInput(), this.jitterIntensity.getInput());
            default:
                return Instant.INSTANCE;
        }
    }

    // Adjust: LinearRotationModel, limits yaw and pitch together
    private static final class LinearAngle implements RotModel {
        private final double speed;

        LinearAngle(double speed) {
            this.speed = speed;
        }

        @Override
        public float[] apply(float[] from, float[] to) {
            float dx = wrap(to[0] - from[0]);
            float dy = to[1] - from[1];
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length == 0.0F) {
                return new float[] {from[0] + dx, from[1] + dy};
            }
            double maxYaw = Math.abs(dx / length) * this.speed;
            double maxPitch = Math.abs(dy / length) * this.speed;
            return new float[] {
                from[0] + (float) Math.max(-maxYaw, Math.min(maxYaw, dx)),
                from[1] + (float) Math.max(-maxPitch, Math.min(maxPitch, dy))
            };
        }
    }

    // Adjust: RandomizedLinearRotationModel ("Step"): instant below the threshold, otherwise stops short by residual
    private static final class Step implements RotModel {
        private final double threshold;
        private final double residual;

        Step(double threshold, double residual) {
            this.threshold = threshold;
            this.residual = residual;
        }

        @Override
        public float[] apply(float[] from, float[] to) {
            float distance = Math.abs(wrap(from[0] - to[0]));
            if (distance <= this.threshold) {
                return Instant.INSTANCE.apply(from, to);
            }
            return new LinearAngle(distance - this.residual + randomFloat(0.01F, 0.05F)).apply(from, to);
        }
    }

    // Adjust: DistributedRotationModel, speed scales with the angle, faster after repeated corrections
    private static final class Distributed implements RotModel {
        static final Distributed INSTANCE = new Distributed();
        private long lastBig;
        private float corrections;

        @Override
        public float[] apply(float[] from, float[] to) {
            float yaw = Math.abs(wrap(from[0] - to[0]));
            float pitch = Math.abs(from[1] - to[1]);
            long now = System.currentTimeMillis();
            this.corrections = now - this.lastBig < 70L ? this.corrections + 1.0F : 0.0F;
            if (yaw > 11.0F) {
                this.lastBig = now;
            }
            float extra = this.corrections <= 5.0F ? this.corrections * 8.0F : 40.0F;
            float base = (1.0F - yaw / 360.0F) * 35.0F + 57.0F + extra;
            double yawSpeed = Math.max(yaw / 317.0F * base, 8.5 + 2.5 * (Math.random() - 0.5));
            double pitchSpeed = Math.max(pitch / 75.0F * base, 3.5 + 0.5 * (Math.random() - 0.5));
            yawSpeed += randomFloat(0.01F, 0.05F);
            pitchSpeed += randomFloat(0.01F, 0.05F);
            float[] yawStep = new LinearAngle(yawSpeed).apply(from, to);
            float[] pitchStep = new LinearAngle(pitchSpeed).apply(from, to);
            return new float[] {yawStep[0], pitchStep[1]};
        }
    }

    // Adjust: OrganicRotationModel, linear step plus sine drift and random jitter (new instance per tick like Adjust)
    private static final class Organic implements RotModel {
        private final double speed;
        private final double drift;
        private final double jitter;

        Organic(double speed, double drift, double jitter) {
            this.speed = speed;
            this.drift = drift;
            this.jitter = jitter;
        }

        @Override
        public float[] apply(float[] from, float[] to) {
            java.util.Random rng = new java.util.Random();
            double yawFrequency1 = rng.nextDouble() * 0.3 + 0.1;
            double yawFrequency2 = rng.nextDouble() * 0.5 + 0.5;
            double pitchFrequency1 = rng.nextDouble() * 0.3 + 0.1;
            double pitchFrequency2 = rng.nextDouble() * 0.5 + 0.5;
            double yawPhase1 = rng.nextDouble() * Math.PI * 2.0;
            double yawPhase2 = rng.nextDouble() * Math.PI * 2.0;
            double pitchPhase1 = rng.nextDouble() * Math.PI * 2.0;
            double pitchPhase2 = rng.nextDouble() * Math.PI * 2.0;
            float dx = wrap(to[0] - from[0]);
            float dy = to[1] - from[1];
            double distance = Math.hypot(dx, dy);
            if (this.drift > distance) {
                return new float[] {from[0] + dx, from[1] + dy};
            }
            if (distance > 0.0) {
                float yawLimit = (float) (Math.abs(dx) / distance * this.speed);
                float pitchLimit = (float) (Math.abs(dy) / distance * this.speed);
                dx = Math.max(-yawLimit, Math.min(yawLimit, dx));
                dy = Math.max(-pitchLimit, Math.min(pitchLimit, dy));
            }
            double time = 1.0;
            double yawWave = Math.sin(time * yawFrequency1 + yawPhase1)
                    + randomFloat(0.45F, 0.55F) * Math.sin(time * yawFrequency2 + yawPhase2);
            double pitchWave = Math.sin(time * pitchFrequency1 + pitchPhase1)
                    + randomFloat(0.45F, 0.55F) * Math.sin(time * pitchFrequency2 + pitchPhase2);
            float yawJitter = (float) ((rng.nextDouble() * 2.0 - 1.0) * this.jitter);
            float pitchJitter = (float) ((rng.nextDouble() * 2.0 - 1.0) * this.jitter);
            return new float[] {
                (float) (yawWave * this.drift) + dx + yawJitter + from[0],
                (float) (pitchWave * this.drift) + dy + pitchJitter + from[1]
            };
        }
    }

    // Adjust: ScaffoldLinearRotationModel, limits the yaw step only
    private static final class Linear implements RotModel {
        private final double speed;

        Linear(double speed) {
            this.speed = speed + randomFloat(0.01F, 0.05F);
        }

        @Override
        public float[] apply(float[] from, float[] to) {
            float dx = wrap(to[0] - from[0]);
            float dy = to[1] - from[1];
            double length = Math.sqrt(dx * dx + dy * dy);
            if (length == 0.0) {
                return new float[] {from[0] + dx, from[1] + dy};
            }
            double maxYaw = Math.abs(dx / length) * this.speed;
            double yaw = Math.max(-maxYaw, Math.min(maxYaw, dx));
            return new float[] {(float) yaw + from[0], from[1] + dy};
        }
    }

    // Adjust: ScaffoldWeightedRotationModel
    private static final class Weighted implements RotModel {
        private final float factor;

        Weighted(float[] weights, int remainingTicks) {
            float sum = 0.0F;
            for (int i = weights.length - remainingTicks; i < weights.length; i++) {
                sum += weights[i];
            }
            this.factor = weights[weights.length - remainingTicks] / sum;
        }

        @Override
        public float[] apply(float[] from, float[] to) {
            return new float[] {from[0] + wrap(to[0] - from[0]) * this.factor, from[1] + (to[1] - from[1]) * this.factor
            };
        }
    }

    private static final class Candidate {
        final float yaw;
        final float pitch;

        Candidate(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static final class FaceBox {
        final Box box;
        final Direction facing;

        FaceBox(Box box, Direction facing) {
            this.box = box;
            this.facing = facing;
        }
    }

    private static final class Target {
        final BlockPos pos;
        final Direction facing;
        final Vec3d facePos;
        final float yaw;
        final float pitch;
        final BlockPos placedPos;

        Target(BlockPos pos, Direction facing, Vec3d facePos, float yaw, float pitch) {
            this.pos = pos;
            this.facing = facing;
            this.facePos = facePos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.placedPos = canReplace(mc.world.getBlockState(pos).getBlock(), pos) ? pos : pos.offset(facing);
        }

        boolean isInvalidHitVec() {
            double x = this.facePos.x - this.pos.getX();
            double y = this.facePos.y - this.pos.getY();
            double z = this.facePos.z - this.pos.getZ();
            if (x < 0 || x > 1 || y < 0 || y > 1 || z < 0 || z > 1) {
                return true;
            }
            switch (this.facing) {
                case NORTH:
                    return z != 0;
                case EAST:
                    return x != 1;
                case SOUTH:
                    return z != 1;
                case WEST:
                    return x != 0;
                case UP:
                    return y != 1;
                case DOWN:
                    return y != 0;
                default:
                    return false;
            }
        }
    }

    private static final class Record {
        final BlockPos pos;
        final Vec3d playerPos;
        final int slot;
        final long time = System.currentTimeMillis();
        boolean confirmed;

        Record(BlockPos pos, Vec3d playerPos, int slot) {
            this.pos = pos;
            this.playerPos = playerPos;
            this.slot = slot;
        }
    }

    // snapshot of the real or a simulated player
    private static final class State {
        final double x;
        final double y;
        final double z;
        final boolean onGround;
        final double velocityY;
        final float fallDistance;

        State(LocalClientPlayerEntity player) {
            this.x = player.x;
            this.y = player.y;
            this.z = player.z;
            this.onGround = player.onGround;
            this.velocityY = player.velocityY;
            this.fallDistance = player.fallDistance;
        }

        State(SimulatedPlayer sim) {
            Vec3d pos = sim.getPos();
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
            this.onGround = sim.onGround;
            this.velocityY = sim.motionY;
            this.fallDistance = sim.fallDistance;
        }

        Vec3d pos() {
            return new Vec3d(this.x, this.y, this.z);
        }

        Vec3d eyes() {
            return new Vec3d(this.x, this.y + mc.player.getEyeHeight(), this.z);
        }
    }
}
