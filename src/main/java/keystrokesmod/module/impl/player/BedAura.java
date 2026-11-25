// Ported from Lizzie's BedAura (a more legit take on Raven bS's BedAura), modified for Feral.
// Only necessary rotations and Ground spoof are kept from Raven bS 17's own BedAura.
package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.SlotUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Autoblock;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.render.BlockOverlay;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.KeySetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BedBlock.Part;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Mouse;

public class BedAura extends Module {
    private final SliderSetting fov;
    private final SliderSetting range;
    private final SliderSetting smoothing;
    private final SliderSetting breakDelay;
    private final SliderSetting breakSpeed;
    private final ButtonSetting breakNearBlock;
    private final ButtonSetting onlyNecessaryRotations;
    private final ButtonSetting groundSpoof;
    private final ButtonSetting prioritizeManualBreak;
    private final ButtonSetting exceptionItemsToggle;
    private final ButtonSetting breakExposedBedWithExceptionItem;
    private final ItemListSetting exceptionItems;
    private final ButtonSetting prioritizeKillAura;
    private final GroupSetting swapGroup;
    private final ButtonSetting swapToTools;
    private final ButtonSetting switchBackWhenDone;
    private final ButtonSetting overrideSwapBack;
    private final ButtonSetting renderOutline;
    private final ButtonSetting visualise;
    private final ColorSetting outlineColor;
    private static final int MS_PER_TICK = 50;
    private static final int ROTATION_IDLE = 0;
    private static final int ROTATION_TRACK = 1;
    private static final int ROTATION_RETURN = 3;
    private static final double BED_FIND_EXTRA_BLOCKS = 1.0;
    // Pixel-center samples (1/16 block spacing) let the route search find
    // narrow, one-pixel openings in an exposed bed surface.
    private static final double[] BED_FACE_SAMPLES = new double[] {
        0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875,
        0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };
    private static final int MAX_ROUTE_CELLS = 64;
    // A fresh route has to beat the committed one by this much before BedAura abandons a started tunnel.
    private static final double COMMIT_SWITCH_MARGIN_TICKS = 3.0;
    private static final int MAX_TUNNEL_DEPTH = 4;
    private static final double MOVING_DIAGONAL_PENALTY_TICKS = 2.0;
    private final List<BlockPos> committedRoute = new ArrayList<>();
    private final List<BlockPos[]> bedPairsCache = new ArrayList<>();
    private final Set<BlockPos> seenBedFeet = new HashSet<>();
    private final Set<BlockPos> routeDefenseBlocks = new HashSet<>();
    private final List<RouteStep> routeObstructions = new ArrayList<>();
    private int rotationState;
    private BlockPos targetPos;
    private BlockPos lockedAimBlock;
    private Vec3d targetHitVec;
    private Direction targetSide;
    // Only necessary rotations: mining runs through packets (handleAutomaticMining) with the aim in those packets only.
    private boolean packetMiningActive;
    private boolean sendingMiningPackets;
    private float[] miningPacketRotations;
    private boolean miningActive;
    private boolean exceptionPauseActive;
    private boolean manualBreakSequenceActive;
    private boolean manualBreakIdleUntilRelease;
    private boolean manualBreakButtonDown;
    private boolean controlsInput;
    private int hotbarProgrammaticDepth;
    private boolean hasSwapped;
    private int previousSlot = -1;
    private int manualBreakPreviousSlot = -1;

    public BedAura() {
        super("BedAura", Module.category.player);
        this.registerSetting(this.breakSpeed = new SliderSetting("Break speed", "x", 1.0, 1.0, 2.0, 0.02));
        this.registerSetting(this.breakDelay = new SliderSetting("Break delay", "ms", 250.0, 0.0, 250.0, 50.0));
        this.registerSetting(this.fov = new SliderSetting("FOV", "", 180.0, 30.0, 360.0, 1.0));
        this.registerSetting(this.range = new SliderSetting("Range", " block", 4.5, 2.0, 6.0, 0.1));
        this.registerSetting(this.smoothing = new SliderSetting("Smoothing", "%", 30.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.breakNearBlock = new ButtonSetting("Break near block", true));
        this.registerSetting(this.onlyNecessaryRotations = new ButtonSetting("Only necessary rotations", false));
        this.registerSetting(this.groundSpoof = new ButtonSetting("Ground spoof", false));
        this.registerSetting(this.prioritizeManualBreak = new ButtonSetting("Prioritize manual break", false));
        this.registerSetting(this.exceptionItemsToggle = new ButtonSetting("Pause on exception items", false));
        this.registerSetting(
                this.breakExposedBedWithExceptionItem =
                        new ButtonSetting("Break exposed bed with exception item", true));
        this.registerSetting(this.exceptionItems = new ItemListSetting("Exception items"));
        this.registerSetting(this.prioritizeKillAura = new ButtonSetting("Prioritize KillAura", false));
        this.registerSetting(this.swapGroup = new GroupSetting("Swap"));
        this.registerSetting(this.swapToTools = new ButtonSetting(this.swapGroup, "Swap to tools", true));
        this.registerSetting(
                this.switchBackWhenDone =
                        new ButtonSetting(this.swapGroup, "Switch back when done", true, "Swap to previous slot"));
        this.registerSetting(this.overrideSwapBack = new ButtonSetting(this.swapGroup, "Override swap back", true));
        this.registerSetting(this.renderOutline = new ButtonSetting("Render block outline", true));
        this.registerSetting(this.visualise = new ButtonSetting("Visualise", false));
        this.registerSetting(this.outlineColor = new ColorSetting("Outline color", 255, 64, 64, 229));
    }

    @Override
    public String getInfo() {
        return "Auto";
    }

    @Override
    public void guiUpdate() {
        this.exceptionItems.setVisible(this.exceptionItemsToggle.isToggled(), this);
        this.breakExposedBedWithExceptionItem.setVisible(this.exceptionItemsToggle.isToggled(), this);
        this.switchBackWhenDone.setVisible(this.swapToTools.isToggled(), this);
        this.overrideSwapBack.setVisible(this.swapToTools.isToggled(), this);
        this.outlineColor.setVisible(this.renderOutline.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.finishManualBreakTool();
        this.resetMining();
        this.miningPacketRotations = null;
        this.bedPairsCache.clear();
        this.seenBedFeet.clear();
        this.routeObstructions.clear();
        this.committedRoute.clear();
        this.resetRotationSmoothing();
        this.exceptionPauseActive = false;
        this.routeDefenseBlocks.clear();
        this.manualBreakSequenceActive = false;
        this.manualBreakIdleUntilRelease = false;
        this.manualBreakButtonDown = false;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        this.applyMiningKeyState();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (!this.usesNecessaryRotations() && this.shouldSuppressManualMouse() && (e.button == 0 || e.button == 1)) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onPreAttack(PreAttackEvent e) {
        if (this.usesNecessaryRotations() && this.isActivelyMining()) {
            // Entities can still be hit while the bed is mined through packets.
            if (e.objectMouseOver == null || e.objectMouseOver.type != HitResult.Type.ENTITY) {
                e.setCanceled(true);
            }
        } else if (this.shouldSuppressManualMouse()) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSlotScroll(PreSlotScrollEvent e) {
        int nextSlot = Math.floorMod(e.previousSlot - Integer.compare(e.slot, 0), PlayerInventory.getHotbarSize());
        if (this.tryPauseForExceptionSlot(nextSlot)) {
            return;
        }
        if (!this.shouldSuppressManualMouse()) {
            return;
        }
        if (this.hasSwapped && this.overrideSwapBack.isToggled() && Utils.nullCheck()) {
            int slot = Integer.compare(e.slot, 0);
            this.previousSlot = Math.floorMod(mc.player.inventory.selectedSlot - slot, PlayerInventory.getHotbarSize());
        }
        e.setCanceled(true);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (this.tryPauseForExceptionSlot(e.slot)) {
            return;
        }
        if (!this.shouldSuppressManualMouse() || this.hotbarProgrammaticDepth > 0) {
            return;
        }
        if (this.hasSwapped && this.overrideSwapBack.isToggled()) {
            this.previousSlot = e.slot;
        }
        e.setCanceled(true);
    }

    private boolean shouldSuppressManualMouse() {
        return this.miningActive
                && this.isEnabled()
                && Utils.nullCheck()
                && mc.screen == null
                && this.canMineBlocks()
                && !this.shouldYieldToRotationModules()
                && !this.isManualBreakOverrideActive()
                && !this.exceptionPauseActive;
    }

    private boolean isManualBreakOverrideActive() {
        this.updateManualBreakState();
        return this.manualBreakSequenceActive;
    }

    private void updateManualBreakState() {
        boolean pressed = this.prioritizeManualBreak.isToggled() && Mouse.isButtonDown(0);
        if (!pressed) {
            this.manualBreakSequenceActive = false;
            this.manualBreakIdleUntilRelease = false;
            this.manualBreakButtonDown = false;
            return;
        }
        if (!this.manualBreakButtonDown) {
            boolean sequenceInProgress = this.miningActive || this.exceptionPauseActive;
            this.manualBreakSequenceActive = sequenceInProgress;
            this.manualBreakIdleUntilRelease = !sequenceInProgress;
        }
        this.manualBreakButtonDown = true;
    }

    private boolean tryPauseForExceptionSlot(int slot) {
        if (!this.miningActive
                || !this.exceptionItemsToggle.isToggled()
                || this.hotbarProgrammaticDepth > 0
                || slot < 0
                || slot >= PlayerInventory.getHotbarSize()
                || !Utils.nullCheck()) {
            return false;
        }
        ItemStack stack = mc.player.inventory.getItem(slot);
        if (!this.exceptionItems.matches(stack)) {
            return false;
        }
        this.exceptionPauseActive = true;
        // Keep the item the user selected; don't apply BedAura's switch-back.
        this.previousSlot = -1;
        this.manualBreakPreviousSlot = -1;
        this.resetMining();
        return true;
    }

    public void applyMiningKeyState() {
        if (!this.canMineBlocks()
                || this.shouldYieldToRotationModules()
                || this.isManualBreakOverrideActive()
                || this.exceptionPauseActive) {
            if (this.miningActive) {
                this.resetMining();
            }
            return;
        }
        if (this.usesNecessaryRotations()
                || !(this.miningActive && this.isEnabled() && Utils.nullCheck() && mc.screen == null)) {
            this.restoreInput();
            return;
        }
        int atk = mc.options.attackKey.getKeyCode();
        this.controlsInput = true;
        KeyBinding.set(atk, false);
        KeyBinding.set(mc.options.useKey.getKeyCode(), false);
        boolean silentAimOnTarget = this.isSilentAimOnTarget();
        if (this.lockedAimBlock != null && (!this.lockedAimBlock.equals(this.targetPos) || !silentAimOnTarget)) {
            this.lockedAimBlock = null;
        }
        if (this.lockedAimBlock == null && silentAimOnTarget) {
            this.lockedAimBlock = this.targetPos;
        }
        // Only hold attack once the server-side aim actually lands on the target block.
        if (this.lockedAimBlock != null && this.lockedAimBlock.equals(this.targetPos) && silentAimOnTarget) {
            KeyBinding.set(atk, true);
            // Vanilla sets leftClickCounter to 10000 whenever the game regains focus (closing chat, inventory, any
            // GUI) and only clears it once attack is released. Attack is held here the whole time, so without this
            // mining would never start after closing a GUI next to a bed.
            ((IAccessorMinecraft) mc).setLeftClickCounter(0);
        }
    }

    public BlockPos getAuraTargetPos() {
        return this.miningActive
                        && this.canMineBlocks()
                        && !this.shouldYieldToRotationModules()
                        && !this.isManualBreakOverrideActive()
                        && !this.exceptionPauseActive
                ? this.targetPos
                : null;
    }

    public boolean isActivelyMining() {
        return this.miningActive
                && this.isEnabled()
                && Utils.nullCheck()
                && mc.screen == null
                && this.canMineBlocks()
                && !this.shouldYieldToRotationModules()
                && !this.isManualBreakOverrideActive()
                && !this.exceptionPauseActive;
    }

    public boolean isPrioritizingKillAura() {
        return this.prioritizeKillAura.isToggled();
    }

    public boolean shouldSuspendAutoblock() {
        return this.isActivelyMining()
                && (!this.usesNecessaryRotations() || this.sendingMiningPackets || this.miningPacketRotations != null);
    }

    public void prepareForKillAura() {
        if (this.isEnabled()
                && this.prioritizeKillAura.isToggled()
                && this.hasSwapped
                && this.previousSlot != -1
                && Utils.nullCheck()) {
            this.setSlot(this.previousSlot);
        }
    }

    public boolean handleAutomaticMining() {
        if (this.usesNecessaryRotations()
                && this.isActivelyMining()
                && this.targetPos != null
                && this.targetSide != null) {
            this.packetMiningActive = true;
            this.sendingMiningPackets = true;

            try {
                if (mc.interactionManager.tickBlockMining(this.targetPos, this.targetSide)) {
                    mc.particleManager.addBlockMiningParticles(this.targetPos, this.targetSide);
                    mc.player.swingArm();
                }
            } finally {
                this.sendingMiningPackets = false;
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean sendMiningPacket(Packet<?> packet) {
        if (Utils.nullCheck() && packet instanceof PlayerHandActionC2SPacket) {
            PlayerHandActionC2SPacket digging = (PlayerHandActionC2SPacket) packet;
            Action action = digging.getAction();
            if (action != Action.START_DESTROY_BLOCK
                    && action != Action.STOP_DESTROY_BLOCK
                    && action != Action.ABORT_DESTROY_BLOCK) {
                return false;
            }

            BlockPos pos = digging.getPos();
            boolean spoofGround = this.groundSpoof.isToggled()
                    && (this.sendingMiningPackets || this.isActivelyMining() && pos.equals(this.targetPos));
            if (this.sendingMiningPackets) {
                boolean onGround = spoofGround || mc.player.onGround;
                Vec3d hit = pos.equals(this.targetPos) && this.targetHitVec != null
                        ? this.targetHitVec
                        : new Vec3d(pos).add(0.5, 0.5, 0.5);
                float yaw = RotationUtils.serverRotations[0];
                float pitch = RotationUtils.serverRotations[1];
                float[] rotations = RotationUtils.getRotationsToPoint(hit.x, hit.y, hit.z, yaw, pitch, 0.0F);
                this.miningPacketRotations = rotations;
                Autoblock autoblock = (Autoblock) ModuleManager.getModule(Autoblock.class);
                if (autoblock != null) {
                    autoblock.prepareForBedMining();
                }

                mc.player.networkHandler.sendPacket(new Angles(rotations[0], rotations[1], onGround));

                try {
                    mc.player.networkHandler.sendPacket(packet);
                } finally {
                    mc.player.networkHandler.sendPacket(new Angles(yaw, pitch, onGround));
                }

                return true;
            } else {
                if (!spoofGround) {
                    return false;
                }

                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket(true));
                mc.player.networkHandler.sendPacket(packet);
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean shouldOverrideFastMine() {
        return this.isActivelyMining();
    }

    public float getBreakSpeedMultiplier() {
        float multiplier = (float) this.breakSpeed.getInput();
        return multiplier > 1.0F ? multiplier : 1.0F;
    }

    public float getGroundSpoofMultiplier() {
        return this.groundSpoof.isToggled() && Utils.nullCheck() && !mc.player.onGround ? 5.0F : 1.0F;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreMotion(PreMotionEvent e) {
        if (this.isEnabled()
                && Utils.nullCheck()
                && this.usesNecessaryRotations()
                && this.miningPacketRotations != null) {
            e.setRotations(this.miningPacketRotations[0], this.miningPacketRotations[1]);
        }

        if (this.groundSpoof.isToggled() && this.isActivelyMining()) {
            e.setOnGround(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onGameTick(GameTickEvent e) {
        this.miningPacketRotations = null;
    }

    public int getBreakDelayTicks() {
        return Math.max(0, Math.min(5, (int) (this.breakDelay.getInput() / MS_PER_TICK)));
    }

    public float getAuraBreakProgress() {
        if (!this.canMineBlocks() || !this.miningActive || mc.interactionManager == null) {
            return 0.0F;
        }
        IAccessorPlayerControllerMP pc = (IAccessorPlayerControllerMP) mc.interactionManager;
        BlockPos target = pc.getCurrentBlock();
        if (this.targetPos == null || target == null || !this.targetPos.equals(target)) {
            return 0.0F;
        }
        return pc.getCurBlockDamageMP();
    }

    public boolean shouldOverrideMouseOver() {
        return this.isEnabled()
                && !this.usesNecessaryRotations()
                && this.miningActive
                && Utils.nullCheck()
                && this.canMineBlocks()
                && this.targetPos != null
                && this.targetHitVec != null
                && !this.shouldYieldToRotationModules()
                && !this.isManualBreakOverrideActive()
                && !this.exceptionPauseActive;
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (!this.shouldOverrideMouseOver() || mc.interactionManager == null) {
            return;
        }
        double reach = mc.interactionManager.getReach();
        Float serverYaw = RotationHelper.get().getServerYaw();
        Float serverPitch = RotationHelper.get().getServerPitch();
        float yaw = serverYaw == null ? RotationUtils.serverRotations[0] : serverYaw;
        float pitch = serverPitch == null ? RotationUtils.serverRotations[1] : serverPitch;
        // Block-only ray tracing keeps vanilla block occlusion but lets the aim
        // pass through entities, matching GhostHand's entity-ignore behavior.
        mc.crosshairTarget = RotationUtils.rayTraceCustom(reach, yaw, pitch);
        mc.targetEntity = null;
        GameRenderer renderer = mc.gameRenderer;
        if (renderer instanceof IAccessorEntityRenderer) {
            ((IAccessorEntityRenderer) renderer).setPointedEntity(null);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        this.updateManualBreakState();
        if (!(this.isEnabled() && Utils.nullCheck() && mc.screen == null && this.canMineBlocks())) {
            this.finishManualBreakTool();
            this.resetMining();
            this.resetRotationSmoothing();
            return;
        }
        boolean manualBreak = this.isManualBreakOverrideActive();
        if (!manualBreak) {
            this.finishManualBreakTool();
        }
        // Other rotation owners take precedence before BedAura can apply aim or
        // return its rotations, including while an exception pause is active.
        if (this.shouldYieldToRotationModules() || e.scriptRotations || e.yaw != null || e.pitch != null) {
            this.resetMining();
            this.resetRotationSmoothing();
            return;
        }
        boolean holdingExceptionItem = this.isHoldingExceptionItem();
        // Suppress the old mining target while deciding whether an exposed bed
        // can still be reached directly with the exception item selected.
        this.exceptionPauseActive = this.exceptionItemsToggle.isToggled() && holdingExceptionItem;
        if (this.exceptionPauseActive) {
            if (manualBreak && this.isManualBreakStartedOnDefense()) {
                this.exceptionPauseActive = false;
            } else {
                boolean canTryExposedBed =
                        holdingExceptionItem && this.breakExposedBedWithExceptionItem.isToggled() && !manualBreak;
                if (!canTryExposedBed) {
                    this.previousSlot = -1;
                    this.resetMining();
                    this.applySmoothReturn(e);
                    return;
                }
            }
        }
        if (manualBreak) {
            this.resetMining();
            this.updateManualBreakTool();
            this.applySmoothReturn(e);
            return;
        }
        if (this.manualBreakIdleUntilRelease) {
            this.resetMining();
            this.routeDefenseBlocks.clear();
            this.applySmoothReturn(e);
            return;
        }
        double reach = this.range.getInput();
        double reachSq = reach * reach;
        this.rebuildBedPairsCache(reach + BED_FIND_EXTRA_BLOCKS);
        if (this.bedPairsCache.isEmpty()) {
            this.routeDefenseBlocks.clear();
            this.resetMining();
            this.applySmoothReturn(e);
            return;
        }
        Choice best = this.chooseBestTarget(reachSq, holdingExceptionItem);
        if (best == null) {
            this.resetMining();
            this.exceptionPauseActive = holdingExceptionItem && this.exceptionItemsToggle.isToggled();
            this.applySmoothReturn(e);
            return;
        }
        this.exceptionPauseActive = false;
        this.targetPos = best.pos;
        this.targetHitVec = best.hitVec;
        this.targetSide = best.side;
        this.miningActive = true;
        this.equipBestHotbarTool(BlockUtils.getBlock(this.targetPos));
        if (this.usesNecessaryRotations()) {
            // The aim only goes into the mining packets (sendMiningPacket).
            this.applySmoothReturn(e);
            return;
        }
        float baseYaw = RotationUtils.serverRotations[0];
        float basePitch = RotationUtils.serverRotations[1];
        float[] targetRotations = RotationUtils.getRotationsToPoint(
                this.targetHitVec.x, this.targetHitVec.y, this.targetHitVec.z, baseYaw, basePitch, 0.0F);
        boolean keepLock =
                this.lockedAimBlock != null && this.lockedAimBlock.equals(this.targetPos) && this.isSilentAimOnTarget();
        this.rotationState = ROTATION_TRACK;
        if (keepLock) {
            this.claimMovementFixedRotations(e, targetRotations[0], targetRotations[1]);
        } else {
            this.lockedAimBlock = null;
            float[] smoothed = this.getRotationsSmoothed(baseYaw, basePitch, targetRotations[0], targetRotations[1]);
            this.claimMovementFixedRotations(e, smoothed[0], smoothed[1]);
        }
    }

    private void applySmoothReturn(ClientRotationEvent event) {
        if (this.rotationState != ROTATION_TRACK && this.rotationState != ROTATION_RETURN) {
            return;
        }
        if (!Utils.nullCheck() || mc.screen != null) {
            this.resetRotationSmoothing();
            return;
        }
        float cameraYaw = mc.player.yaw;
        float cameraPitch = mc.player.pitch;
        float baseYaw = event.yaw != null ? event.yaw : RotationUtils.serverRotations[0];
        float basePitch = event.pitch != null ? event.pitch : RotationUtils.serverRotations[1];
        float[] smoothed = this.getRotationsSmoothed(baseYaw, basePitch, cameraYaw, cameraPitch);
        if (Math.abs(MathHelper.wrapDegrees(smoothed[0] - cameraYaw)) < 0.5F
                && Math.abs(smoothed[1] - cameraPitch) < 0.5F) {
            this.resetRotationSmoothing();
            return;
        }
        this.rotationState = ROTATION_RETURN;
        this.claimMovementFixedRotations(event, smoothed[0], smoothed[1]);
    }

    private void claimMovementFixedRotations(ClientRotationEvent event, float yaw, float pitch) {
        RotationHelper.get().forceMovementFix = true;
        event.claimExactRotations(yaw, pitch, false);
    }

    private float[] getRotationsSmoothed(float currentYaw, float currentPitch, float targetYaw, float targetPitch) {
        float curYaw = currentYaw;
        float curPitch = currentPitch;
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - curYaw);
        float deltaPitch = targetPitch - curPitch;
        if (Math.abs(deltaYaw) < 0.1F) {
            curYaw = targetYaw;
        }
        if (Math.abs(deltaPitch) < 0.1F) {
            curPitch = targetPitch;
        }
        if (curYaw == targetYaw && curPitch == targetPitch) {
            return new float[] {curYaw, RotationUtils.clampPitch(curPitch)};
        }
        // Preserve the old 0-75% speed range, remapped across the full slider.
        float maxStep = 100.0F - (float) this.smoothing.getInput() * 0.75F;
        maxStep *= 1.0F - (float) (Math.random() * 0.2);
        float totalDelta = Math.abs(deltaYaw) + Math.abs(deltaPitch);
        if (totalDelta <= maxStep) {
            curYaw = targetYaw;
            curPitch = targetPitch;
        } else if (maxStep > 0.0F) {
            float scale = maxStep / totalDelta;
            curYaw += deltaYaw * scale;
            curPitch += deltaPitch * scale;
        }
        return new float[] {curYaw, RotationUtils.clampPitch(curPitch)};
    }

    private void resetRotationSmoothing() {
        this.rotationState = ROTATION_IDLE;
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (!(this.isEnabled()
                && (this.renderOutline.isToggled() || this.visualise.isToggled())
                && this.miningActive
                && this.targetPos != null
                && Utils.nullCheck()
                && this.canMineBlocks())) {
            return;
        }
        Block b = BlockUtils.getBlock(this.targetPos);
        if (b == null || b == Blocks.AIR) {
            return;
        }
        int c = this.outlineColor.getColor();
        if (this.renderOutline.isToggled()) {
            BlockOverlay.renderBlockOutline(this.targetPos, c, c, 2.0F, true);
        }
        if (this.visualise.isToggled() && this.targetHitVec != null) {
            Vec3d t = this.targetHitVec;
            double half = 0.055;
            RenderUtils.renderBox(t.x - half, t.y - half, t.z - half, half * 2, half * 2, half * 2, c, true, true);
            HitResult silentHit = mc.crosshairTarget;
            if (silentHit != null && silentHit.type == HitResult.Type.BLOCK && silentHit.facePos != null) {
                Vec3d s = silentHit.facePos;
                double sh = 0.025;
                RenderUtils.renderBox(s.x - sh, s.y - sh, s.z - sh, sh * 2, sh * 2, sh * 2, 0xFF55FFFF, true, true);
            }
            double dh = 0.0125;
            RenderUtils.renderBox(t.x - dh, t.y - dh, t.z - dh, dh * 2, dh * 2, dh * 2, 0xFFFFFFFF, true, true);
        }
    }

    private void resetMining() {
        if (this.packetMiningActive) {
            this.sendingMiningPackets = true;
            try {
                if (Utils.nullCheck() && mc.interactionManager != null) {
                    mc.interactionManager.stopMiningBlock();
                }
            } finally {
                this.sendingMiningPackets = false;
                this.packetMiningActive = false;
            }
        }
        this.miningActive = false;
        if (this.swapToTools.isToggled()
                && this.switchBackWhenDone.isToggled()
                && this.previousSlot != -1
                && Utils.nullCheck()) {
            this.setSlot(this.previousSlot);
        }
        this.restoreInput();
        this.hotbarProgrammaticDepth = 0;
        this.targetPos = null;
        this.lockedAimBlock = null;
        this.targetHitVec = null;
        this.targetSide = null;
        this.hasSwapped = false;
        this.previousSlot = -1;
    }

    private void restoreInput() {
        if (this.controlsInput) {
            KeyBinding.set(mc.options.attackKey.getKeyCode(), Mouse.isButtonDown(0));
            KeyBinding.set(mc.options.useKey.getKeyCode(), Mouse.isButtonDown(1));
            this.controlsInput = false;
        }
    }

    private boolean isSilentAimOnTarget() {
        if (this.targetPos == null || !Utils.nullCheck()) {
            return false;
        }
        HitResult mouseOver = mc.crosshairTarget;
        return mouseOver != null && mouseOver.type == HitResult.Type.BLOCK && this.targetPos.equals(mouseOver.getPos());
    }

    private void rebuildBedPairsCache(double searchRange) {
        this.bedPairsCache.clear();
        this.seenBedFeet.clear();
        int ri = (int) Math.ceil(searchRange);
        BlockPos origin = new BlockPos(mc.player);
        float fovDeg = (float) this.fov.getInput();
        for (int dx = -ri; dx <= ri; ++dx) {
            for (int dy = -ri; dy <= ri; ++dy) {
                for (int dz = -ri; dz <= ri; ++dz) {
                    BlockPos[] pair = this.footHeadPair(origin.add(dx, dy, dz));
                    if (pair == null
                            || this.seenBedFeet.contains(pair[0])
                            || !this.bedInSearchRange(pair, searchRange)
                            || !this.inFov(this.bedCenter(pair), fovDeg)) {
                        continue;
                    }
                    this.seenBedFeet.add(pair[0]);
                    this.bedPairsCache.add(pair);
                }
            }
        }
        if (ModuleManager.bedwars != null) {
            ModuleManager.bedwars.removeOwnBedPair(this.bedPairsCache);
        }
    }

    private BlockPos[] footHeadPair(BlockPos at) {
        BlockState st = mc.world.getBlockState(at);
        if (!(st.getBlock() instanceof BedBlock)) {
            return null;
        } else {
            Part part = (Part) st.get(BedBlock.PART);
            Direction facing = (Direction) st.get(BedBlock.FACING);
            BlockPos foot = part == Part.FOOT ? at : at.offset(facing.getOpposite());
            BlockState footSt = mc.world.getBlockState(foot);
            if (!(footSt.getBlock() instanceof BedBlock)) {
                return null;
            } else if (footSt.get(BedBlock.PART) != Part.FOOT) {
                return null;
            } else {
                Direction footFacing = (Direction) footSt.get(BedBlock.FACING);
                BlockPos head = foot.offset(footFacing);
                BlockState hs = mc.world.getBlockState(head);
                if (!(hs.getBlock() instanceof BedBlock)) {
                    return null;
                } else if (hs.get(BedBlock.PART) != Part.HEAD) {
                    return null;
                } else {
                    return hs.get(BedBlock.FACING) != footFacing ? null : new BlockPos[] {foot, head};
                }
            }
        }
    }

    private Vec3d bedCenter(BlockPos[] pair) {
        Box a = BlockUtils.unionBlockBounds(pair[0], pair[1]);
        return new Vec3d((a.minX + a.maxX) * 0.5, (a.minY + a.maxY) * 0.5, (a.minZ + a.maxZ) * 0.5);
    }

    private boolean bedInSearchRange(BlockPos[] pair, double searchRadius) {
        Vec3d eye = mc.player.getEyePosition(1.0F);
        double r2 = searchRadius * searchRadius + 1.0E-4;
        Box u = BlockUtils.unionBlockBounds(pair[0], pair[1]);
        Vec3d onBox = RotationUtils.closestPointOnAabb(u, eye);
        return eye.squaredDistanceTo(onBox) <= r2 || eye.squaredDistanceTo(this.bedCenter(pair)) <= r2;
    }

    private boolean inFov(Vec3d worldPoint, float fovDeg) {
        if (fovDeg >= 360.0F) {
            return true;
        }
        Vec3d eyes = mc.player.getEyePosition(1.0F);
        Vec3d look = mc.player.getRotationVector(1.0F);
        Vec3d to = worldPoint.subtract(eyes);
        double len = to.length();
        if (len < 1.0E-6) {
            return true;
        }
        to = new Vec3d(to.x / len, to.y / len, to.z / len);
        double dot = look.dot(to);
        double ang = Math.toDegrees(Math.acos(MathHelper.clamp(dot, -1.0, 1.0)));
        return ang <= fovDeg * 0.5;
    }

    private double interpolate(double min, double max, double amount) {
        return min + (max - min) * amount;
    }

    private double faceAlignment(Vec3d eye, Vec3d hit, Direction side) {
        double x = eye.x - hit.x;
        double y = eye.y - hit.y;
        double z = eye.z - hit.z;
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length < 1.0E-6) {
            return 1.0;
        }
        return Math.max(0.0, (x * side.getOffsetX() + y * side.getOffsetY() + z * side.getOffsetZ()) / length);
    }

    /**
     * Samples every non-bottom face of both bed halves and walks a ray from the eye to each sample.
     * The bed itself is only targeted when the ray reaches it unobstructed; otherwise the first
     * blocking block on a real line of sight becomes the target. No breaking through walls.
     */
    private Choice chooseBestTarget(double reachSq, boolean exposedBedsOnly) {
        this.routeDefenseBlocks.clear();
        IAccessorPlayerControllerMP pc = (IAccessorPlayerControllerMP) mc.interactionManager;
        float curProg = pc.getCurBlockDamageMP();
        BlockPos breaking = pc.getCurrentBlock();
        Vec3d eye = mc.player.getEyePosition(1.0F);
        this.routeObstructions.clear();
        RouteChoice best = null;
        for (BlockPos[] pair : this.bedPairsCache) {
            for (BlockPos half : pair) {
                Box box = BlockUtils.getBlockSelectionBox(half);
                if (box == null) {
                    continue;
                }
                for (Direction face : Direction.values()) {
                    if (face == Direction.DOWN) {
                        continue;
                    }
                    for (double u : BED_FACE_SAMPLES) {
                        for (double v : BED_FACE_SAMPLES) {
                            Vec3d surface = this.facePoint(box, face, u, v);
                            Vec3d endpoint = surface.add(
                                    -face.getOffsetX() * 0.001, -face.getOffsetY() * 0.001, -face.getOffsetZ() * 0.001);
                            RouteChoice candidate =
                                    this.traceBreakRoute(pair, eye, endpoint, reachSq, breaking, curProg);
                            if (candidate != null
                                    && exposedBedsOnly
                                    && (candidate.obstructionCount != 0
                                            || !(candidate.action.pos.equals(pair[0])
                                                    || candidate.action.pos.equals(pair[1])))) {
                                continue;
                            }
                            if (candidate != null && (best == null || candidate.betterThan(best))) {
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }
        if (!exposedBedsOnly && this.breakNearBlock.isToggled()) {
            for (BlockPos[] pair : this.bedPairsCache) {
                for (BlockPos half : pair) {
                    for (Direction face : Direction.values()) {
                        if (face == Direction.DOWN) {
                            continue;
                        }
                        RouteChoice tunnel = this.axisTunnelRoute(pair, half, face, eye, reachSq, breaking, curProg);
                        if (tunnel != null && (best == null || tunnel.betterThan(best))) {
                            best = tunnel;
                        }
                    }
                }
            }
        }
        if (!exposedBedsOnly) {
            Choice committed = this.continueCommittedRoute(eye, reachSq, best, breaking, curProg);
            if (committed != null) {
                return committed;
            }
        }
        this.committedRoute.clear();
        if (best == null) {
            return null;
        }
        this.committedRoute.addAll(best.cells);
        return best.action;
    }

    /**
     * A tunnel dug straight out from one face of a bed half. Straight-line routes from the eye are often diagonal
     * and only line up from the spot they were planned at, so walking a block sideways hides the rest of them. An
     * axis tunnel stays visible from a wide cone in front of it, so it survives strafing and walking, and it is
     * usually the fewest blocks too. Only the outermost block has to be visible right now.
     */
    private RouteChoice axisTunnelRoute(
            BlockPos[] pair,
            BlockPos half,
            Direction face,
            Vec3d eye,
            double reachSq,
            BlockPos breaking,
            float progress) {
        BlockPos cell = half.offset(face);
        if (cell.equals(pair[0]) || cell.equals(pair[1])) {
            return null;
        }
        List<BlockPos> outward = new ArrayList<>(MAX_TUNNEL_DEPTH);
        while (this.isRayHittable(cell)) {
            Block block = BlockUtils.getBlock(cell);
            if (outward.size() >= MAX_TUNNEL_DEPTH
                    || block instanceof BedBlock
                    || block.getMiningTime(mc.world, cell) < 0.0F) {
                return null;
            }
            outward.add(cell);
            cell = cell.offset(face);
        }
        if (outward.isEmpty()) {
            // That face is already open; the straight-line search handles exposed beds.
            return null;
        }
        Box bedBox = BlockUtils.getBlockSelectionBox(half);
        if (bedBox == null) {
            return null;
        }
        Vec3d bedFace = this.facePoint(bedBox, face, 0.5, 0.5);
        // Closest point of that face (a flat box), so a bed whose face center is just out of reach still counts.
        Vec3d bedFaceClosest = RotationUtils.closestPointOnAabb(this.faceBox(bedBox, face), eye);
        if (eye.squaredDistanceTo(bedFaceClosest) > reachSq + 0.001) {
            return null;
        }
        BlockPos entry = outward.get(outward.size() - 1);
        Choice entryHit = this.visibleHit(entry, eye, reachSq);
        if (entryHit == null) {
            return null;
        }
        List<BlockPos> cells = new ArrayList<>(outward.size() + 1);
        for (int i = outward.size() - 1; i >= 0; i--) {
            cells.add(outward.get(i));
        }
        cells.add(half);
        double time = this.routeTime(cells, breaking, progress);
        if (Double.isInfinite(time) || Double.isNaN(time)) {
            return null;
        }
        this.routeDefenseBlocks.addAll(cells);
        return new RouteChoice(
                entryHit,
                cells,
                time,
                outward.size(),
                eye.squaredDistanceTo(entryHit.hitVec),
                eye.squaredDistanceTo(bedFace),
                this.faceAlignment(eye, bedFace, face));
    }

    private Box faceBox(Box box, Direction face) {
        switch (face) {
            case UP:
                return new Box(box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ);
            case NORTH:
                return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ);
            case SOUTH:
                return new Box(box.minX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ);
            case WEST:
                return new Box(box.minX, box.minY, box.minZ, box.minX, box.maxY, box.maxZ);
            case EAST:
                return new Box(box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            default:
                return new Box(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
        }
    }

    private Vec3d facePoint(Box box, Direction face, double u, double v) {
        double x = this.interpolate(box.minX, box.maxX, u);
        double y = this.interpolate(box.minY, box.maxY, v);
        double z = (box.minZ + box.maxZ) * 0.5;
        if (face == Direction.UP || face == Direction.DOWN) {
            y = face == Direction.UP ? box.maxY : box.minY;
            z = this.interpolate(box.minZ, box.maxZ, v);
        } else if (face == Direction.NORTH || face == Direction.SOUTH) {
            z = face == Direction.NORTH ? box.minZ : box.maxZ;
        } else {
            x = face == Direction.WEST ? box.minX : box.maxX;
            z = this.interpolate(box.minZ, box.maxZ, u);
        }
        return new Vec3d(x, y, z);
    }

    private RouteChoice traceBreakRoute(
            BlockPos[] pair, Vec3d eye, Vec3d endpoint, double reachSq, BlockPos breaking, float progress) {
        int x = MathHelper.floor(eye.x);
        int y = MathHelper.floor(eye.y);
        int z = MathHelper.floor(eye.z);
        int endX = MathHelper.floor(endpoint.x);
        int endY = MathHelper.floor(endpoint.y);
        int endZ = MathHelper.floor(endpoint.z);
        double dx = endpoint.x - eye.x;
        double dy = endpoint.y - eye.y;
        double dz = endpoint.z - eye.z;
        int stepX = dx > 0.0 ? 1 : dx < 0.0 ? -1 : 0;
        int stepY = dy > 0.0 ? 1 : dy < 0.0 ? -1 : 0;
        int stepZ = dz > 0.0 ? 1 : dz < 0.0 ? -1 : 0;
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double tMaxX = stepX > 0 ? (x + 1.0 - eye.x) / dx : stepX < 0 ? (x - eye.x) / dx : Double.POSITIVE_INFINITY;
        double tMaxY = stepY > 0 ? (y + 1.0 - eye.y) / dy : stepY < 0 ? (y - eye.y) / dy : Double.POSITIVE_INFINITY;
        double tMaxZ = stepZ > 0 ? (z + 1.0 - eye.z) / dz : stepZ < 0 ? (z - eye.z) / dz : Double.POSITIVE_INFINITY;
        List<RouteStep> obstructions = this.routeObstructions;
        obstructions.clear();
        for (int guard = 0; guard < MAX_ROUTE_CELLS; guard++) {
            BlockPos pos = new BlockPos(x, y, z);
            Block block = BlockUtils.getBlock(pos);
            // Same test the vanilla crosshair ray uses: water, lava, fire etc. can't be hit, so the aim passes through
            // them. Targeting one of those used to leave BedAura aiming at it forever without ever attacking.
            if (this.isRayHittable(pos)) {
                HitResult hit = block.rayTrace(mc.world, pos, eye, endpoint);
                if (hit != null && hit.facePos != null && hit.face != null) {
                    boolean bedHalf = pos.equals(pair[0]) || pos.equals(pair[1]);
                    if (bedHalf) {
                        if (!(block instanceof BedBlock)) {
                            return null;
                        }
                        Choice action = obstructions.isEmpty()
                                ? new Choice(pos, hit.facePos, hit.face)
                                : new Choice(
                                        obstructions.get(0).pos, obstructions.get(0).hit, obstructions.get(0).side);
                        double actionDistanceSq = eye.squaredDistanceTo(action.hitVec);
                        if (actionDistanceSq > reachSq + 0.001) {
                            return null;
                        }
                        List<BlockPos> cells = new ArrayList<>(obstructions.size() + 1);
                        for (RouteStep step : obstructions) {
                            cells.add(step.pos);
                        }
                        cells.add(pos);
                        double time = this.routeTime(cells, breaking, progress);
                        if (Double.isInfinite(time) || Double.isNaN(time)) {
                            return null;
                        }
                        this.routeDefenseBlocks.addAll(cells);
                        RouteChoice route = new RouteChoice(
                                action,
                                cells,
                                time,
                                obstructions.size(),
                                actionDistanceSq,
                                eye.squaredDistanceTo(hit.facePos),
                                this.faceAlignment(eye, hit.facePos, hit.face));
                        // A straight line through 2+ blocks only lines up from (about) this spot; while moving, an
                        // equally fast axis tunnel is the safer pick.
                        if (obstructions.size() > 1 && this.isPlayerMoving()) {
                            route.rankTicks += MOVING_DIAGONAL_PENALTY_TICKS * (obstructions.size() - 1);
                        }
                        return route;
                    }
                    if (block instanceof BedBlock
                            || block.getMiningTime(mc.world, pos) < 0.0F
                            || !this.breakNearBlock.isToggled()) {
                        return null;
                    }
                    boolean seen = false;
                    for (RouteStep step : obstructions) {
                        if (step.pos.equals(pos)) {
                            seen = true;
                            break;
                        }
                    }
                    if (!seen) {
                        obstructions.add(new RouteStep(pos, hit.facePos, hit.face));
                    }
                }
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    /** Estimated ticks to mine every cell of a route in order; the last cell is the bed half. */
    private double routeTime(List<BlockPos> cells, BlockPos breaking, float progress) {
        double ticks = 0.0;
        IAccessorPlayerControllerMP pc = (IAccessorPlayerControllerMP) mc.interactionManager;
        boolean keepProgress = breaking != null && breaking.equals(cells.get(0)) && progress > 0.0F;
        if (!keepProgress) {
            ticks += Math.max(0, pc.getBlockHitDelay());
        }
        for (int i = 0; i < cells.size(); i++) {
            double time = this.stepTime(cells.get(i), keepProgress ? breaking : null, progress);
            if (Double.isInfinite(time)) {
                return Double.POSITIVE_INFINITY;
            }
            ticks += time;
            if (i < cells.size() - 1) {
                // The delay happens between mined blockers and the next block.
                ticks += this.getBreakDelayTicks();
            }
            keepProgress = false;
        }
        return ticks;
    }

    private boolean isPlayerMoving() {
        double mx = mc.player.x - mc.player.prevX;
        double mz = mc.player.z - mc.player.prevZ;
        return mx * mx + mz * mz > 0.05 * 0.05;
    }

    private boolean isRayHittable(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.getBlock().canRayTrace(state, false);
    }

    /**
     * Keeps mining the route picked earlier while its next block is still visible and in reach. Routes are
     * straight lines from the eye to the bed, so as soon as the player moves the line through a freshly mined
     * hole stops lining up and a fresh search starts a new tunnel somewhere else (or drops a half-mined block).
     * Finishing the started tunnel is almost always faster.
     */
    private Choice continueCommittedRoute(
            Vec3d eye, double reachSq, RouteChoice fresh, BlockPos breaking, float progress) {
        List<BlockPos> route = this.committedRoute;
        while (!route.isEmpty() && !this.isRayHittable(route.get(0))) {
            route.remove(0);
        }
        // Only the bed is left (the fresh search handles that), the bed is gone, or the bed is directly hittable.
        if (route.size() < 2
                || !(BlockUtils.getBlock(route.get(route.size() - 1)) instanceof BedBlock)
                || (fresh != null && fresh.obstructionCount == 0)) {
            route.clear();
            return null;
        }
        BlockPos next = route.get(0);
        if (fresh != null && fresh.action.pos.equals(next)) {
            return null;
        }
        double remaining = this.routeTime(route, breaking, progress);
        if (fresh != null && fresh.estimatedTicks + COMMIT_SWITCH_MARGIN_TICKS < remaining) {
            route.clear();
            return null;
        }
        Choice hit = this.visibleHit(next, eye, reachSq);
        if (hit == null) {
            route.clear();
            return null;
        }
        this.routeDefenseBlocks.addAll(route);
        return hit;
    }

    /** A point on the block the crosshair ray would actually land on, preferring the current aim point. */
    private Choice visibleHit(BlockPos pos, Vec3d eye, double reachSq) {
        if (pos.equals(this.targetPos)
                && this.targetHitVec != null
                && this.targetSide != null
                && this.rayLandsOn(pos, eye, this.targetHitVec, reachSq)) {
            return new Choice(pos, this.targetHitVec, this.targetSide);
        }
        Box box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) {
            return null;
        }
        Vec3d center = new Vec3d((box.minX + box.maxX) * 0.5, (box.minY + box.maxY) * 0.5, (box.minZ + box.maxZ) * 0.5);
        Vec3d best = null;
        Direction bestFace = null;
        double bestScore = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            Vec3d faceCenter = this.facePoint(box, face, 0.5, 0.5);
            if (this.faceAlignment(eye, faceCenter, face) <= 0.0) {
                continue;
            }
            for (int i = 1; i < BED_FACE_SAMPLES.length; i += 2) {
                for (int j = 1; j < BED_FACE_SAMPLES.length; j += 2) {
                    Vec3d point = this.facePoint(box, face, BED_FACE_SAMPLES[i], BED_FACE_SAMPLES[j]);
                    double score = point.squaredDistanceTo(center);
                    if (score < bestScore && this.rayLandsOn(pos, eye, point, reachSq)) {
                        bestScore = score;
                        best = point;
                        bestFace = face;
                    }
                }
            }
        }
        return best == null ? null : new Choice(pos, best, bestFace);
    }

    private boolean rayLandsOn(BlockPos pos, Vec3d eye, Vec3d point, double reachSq) {
        if (eye.squaredDistanceTo(point) > reachSq + 0.001) {
            return false;
        }
        // Extend slightly past the surface so the ray actually enters the block.
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-6) {
            return false;
        }
        Vec3d end = point.add(dx / len * 0.01, dy / len * 0.01, dz / len * 0.01);
        HitResult hit = mc.world.rayTrace(eye, end, false, false, false);
        return hit != null && hit.type == HitResult.Type.BLOCK && pos.equals(hit.getPos());
    }

    private double stepTime(BlockPos pos, BlockPos breaking, float progress) {
        Block block = BlockUtils.getBlock(pos);
        float digRate = this.swapToTools.isToggled()
                ? BlockUtils.maxDigRateAcrossSlots(block, PlayerInventory.getHotbarSize())
                : BlockUtils.getBlockHardness(
                        block, mc.player.inventory.getItem(mc.player.inventory.selectedSlot), false, false);
        if (digRate <= 0.0F) {
            return Double.POSITIVE_INFINITY;
        }
        double remaining = pos.equals(breaking) ? Math.max(0.0, 1.0 - Math.min(1.0, progress)) : 1.0;
        return remaining / (digRate * Math.max(1.0, this.breakSpeed.getInput()));
    }

    private void equipBestHotbarTool(Block block) {
        if (!this.swapToTools.isToggled()) {
            return;
        }
        int currentSlot = mc.player.inventory.selectedSlot;
        int slot = this.findBestHotbarToolSlot(block);
        if (this.previousSlot == -1 && slot != currentSlot) {
            this.previousSlot = currentSlot;
        }
        if (slot != currentSlot) {
            this.setSlot(slot);
        }
    }

    private int findBestHotbarToolSlot(Block block) {
        int slot = mc.player.inventory.selectedSlot;
        float bestRate = BlockUtils.getBlockHardness(block, mc.player.inventory.getItem(slot), false, false);
        for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
            float rate = BlockUtils.getBlockHardness(block, mc.player.inventory.getItem(i), false, false);
            if (rate > bestRate) {
                bestRate = rate;
                slot = i;
            }
        }
        return slot;
    }

    private void updateManualBreakTool() {
        if (!this.swapToTools.isToggled() || !Utils.nullCheck()) {
            return;
        }
        BlockPos pos = null;
        HitResult mouseOver = mc.crosshairTarget;
        if (mouseOver != null && mouseOver.type == HitResult.Type.BLOCK) {
            BlockPos aimed = mouseOver.getPos();
            if (aimed != null && BlockUtils.getBlock(aimed) != Blocks.AIR) {
                pos = aimed;
            }
        }
        if (pos == null && mc.interactionManager != null) {
            pos = ((IAccessorPlayerControllerMP) mc.interactionManager).getCurrentBlock();
        }
        if (pos == null) {
            return;
        }
        Block block = BlockUtils.getBlock(pos);
        if (block == null || block == Blocks.AIR) {
            return;
        }
        int currentSlot = mc.player.inventory.selectedSlot;
        int bestSlot = this.findBestHotbarToolSlot(block);
        if (bestSlot == currentSlot) {
            return;
        }
        if (this.manualBreakPreviousSlot == -1) {
            this.manualBreakPreviousSlot = currentSlot;
        }
        this.setSlot(bestSlot);
    }

    private void finishManualBreakTool() {
        this.manualBreakSequenceActive = false;
        if (this.manualBreakPreviousSlot == -1) {
            return;
        }
        int restoreSlot = this.manualBreakPreviousSlot;
        this.manualBreakPreviousSlot = -1;
        if (this.swapToTools.isToggled() && this.switchBackWhenDone.isToggled() && Utils.nullCheck()) {
            this.setSlot(restoreSlot);
        }
    }

    private void setSlot(int slot) {
        if (!this.swapToTools.isToggled() || slot == -1 || slot == mc.player.inventory.selectedSlot) {
            return;
        }
        ++this.hotbarProgrammaticDepth;
        try {
            mc.player.inventory.selectedSlot = slot;
            this.hasSwapped = true;
            ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
        } finally {
            --this.hotbarProgrammaticDepth;
        }
    }

    private boolean canMineBlocks() {
        return Utils.nullCheck()
                && mc.interactionManager != null
                && mc.player.abilities.canModifyWorld
                && !mc.player.abilities.creativeMode
                && !mc.player.isSpectator();
    }

    private boolean usesNecessaryRotations() {
        return this.onlyNecessaryRotations.isToggled();
    }

    private boolean isHoldingExceptionItem() {
        return this.exceptionItemsToggle.isToggled()
                && Utils.nullCheck()
                && this.exceptionItems.matches(mc.player.inventory.getItem(mc.player.inventory.selectedSlot));
    }

    private boolean shouldYieldToRotationModules() {
        if (ModuleManager.autoBlockin != null
                && ModuleManager.autoBlockin.isEnabled()
                && Utils.nullCheck()
                && mc.screen == null) {
            for (Setting setting : ModuleManager.autoBlockin.getSettings()) {
                if (setting instanceof KeySetting
                        && "Activation key".equals(setting.getName())
                        && ((KeySetting) setting).isPressed()) {
                    return true;
                }
            }
        }
        if (ModuleManager.killAura != null && ModuleManager.killAura.isEnabled() && KillAura.target != null) {
            return true;
        }
        Module clutch = ModuleManager.getModule(Clutch.class);
        return clutch instanceof Clutch && ((Clutch) clutch).isActiveWindow();
    }

    private boolean isManualBreakStartedOnDefense() {
        if (!Mouse.isButtonDown(0) || mc.interactionManager == null) {
            return false;
        }
        BlockPos breaking = ((IAccessorPlayerControllerMP) mc.interactionManager).getCurrentBlock();
        if (breaking == null) {
            return false;
        }
        if (this.routeDefenseBlocks.contains(breaking)) {
            return true;
        }
        for (BlockPos[] pair : this.bedPairsCache) {
            if (breaking.equals(pair[0]) || breaking.equals(pair[1])) {
                return true;
            }
            for (BlockPos bedHalf : pair) {
                for (Direction side : Direction.values()) {
                    if (side != Direction.DOWN && breaking.equals(bedHalf.offset(side))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final class RouteStep {
        final BlockPos pos;
        final Vec3d hit;
        final Direction side;

        RouteStep(BlockPos pos, Vec3d hit, Direction side) {
            this.pos = pos;
            this.hit = hit;
            this.side = side;
        }
    }

    private static final class RouteChoice {
        final Choice action;
        final List<BlockPos> cells;
        final double estimatedTicks;
        // estimatedTicks plus a penalty for routes that fall apart when the player moves (used for ranking only).
        double rankTicks;
        final double actionDistanceSq;
        final double bedDistanceSq;
        final double alignment;
        final int obstructionCount;

        RouteChoice(
                Choice action,
                List<BlockPos> cells,
                double estimatedTicks,
                int obstructionCount,
                double actionDistanceSq,
                double bedDistanceSq,
                double alignment) {
            this.action = action;
            this.cells = cells;
            this.rankTicks = estimatedTicks;
            this.estimatedTicks = estimatedTicks;
            this.obstructionCount = obstructionCount;
            this.actionDistanceSq = actionDistanceSq;
            this.bedDistanceSq = bedDistanceSq;
            this.alignment = alignment;
        }

        boolean betterThan(RouteChoice other) {
            int result = Double.compare(this.rankTicks, other.rankTicks);
            if (result != 0) {
                return result < 0;
            }
            result = Integer.compare(this.obstructionCount, other.obstructionCount);
            if (result != 0) {
                return result < 0;
            }
            result = Double.compare(this.actionDistanceSq, other.actionDistanceSq);
            if (result != 0) {
                return result < 0;
            }
            result = Double.compare(this.bedDistanceSq, other.bedDistanceSq);
            if (result != 0) {
                return result < 0;
            }
            return this.alignment > other.alignment;
        }
    }

    private static final class Choice {
        final BlockPos pos;
        final Vec3d hitVec;
        final Direction side;

        Choice(BlockPos pos, Vec3d hitVec, Direction side) {
            this.pos = pos;
            this.hitVec = hitVec;
            this.side = side;
        }
    }
}
