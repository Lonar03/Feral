package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.Autoblock;
import keystrokesmod.module.impl.render.BlockOverlay;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BedBlock.Part;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.PaneBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.living.player.PlayerInventory;
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
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class BedAura extends Module {
    private static final String[] MODES = new String[] {"Assist", "Auto"};
    private final SliderSetting mode;
    private final SliderSetting fov;
    private final SliderSetting range;
    private final SliderSetting rate;
    private final SliderSetting speed;
    private final SliderSetting breakDelay;
    private final SliderSetting breakSpeed;
    private final ButtonSetting silentAim;
    private final ButtonSetting breakBed;
    private final ButtonSetting onlyOnDamage;
    private final ButtonSetting breakNearBlock;
    private final ButtonSetting onlyNecessaryRotations;
    private final ButtonSetting groundSpoof;
    private final ButtonSetting prioritizeKillAura;
    private final GroupSetting swapGroup;
    private final ButtonSetting swapToTools;
    private final ButtonSetting switchBackWhenDone;
    private final ButtonSetting overrideSwapBack;
    private final ButtonSetting renderOutline;
    private final ColorSetting outlineColor;
    private static final int MS_PER_TICK = 50;
    private static final int LEGIT_DAMAGE_WINDOW_TICKS = 20;
    private static final double LEGIT_RAY_INSET = 0.05;
    private static final double LEGIT_MIN_FACE_ALIGNMENT = 0.08;
    private static final double BED_FIND_EXTRA_BLOCKS = 1.0;
    private final List<BlockPos[]> bedPairsCache = new ArrayList<>();
    private int scanCooldown;
    private int activeMode = -1;
    private int legitDamageTicksRemaining;
    private int legitDamageCountdownTick = Integer.MIN_VALUE;
    private int previousHurtTime;
    private boolean legitDamageAssistActive;
    private BlockPos targetPos;
    private Vec3d targetHitVec;
    private Direction targetSide;
    private BlockPos legitBedFoot;
    private boolean miningActive;
    private boolean packetMiningActive;
    private boolean sendingMiningPackets;
    private float[] miningPacketRotations;
    private boolean activeNecessaryRotations;
    private boolean controlsInput;
    private int hotbarProgrammaticDepth;
    private boolean hasSwapped;
    private int previousSlot = -1;

    public BedAura() {
        super("BedAura", Module.category.player);
        this.registerSetting(this.mode = new SliderSetting("Mode", 1, MODES));
        this.registerSetting(this.breakSpeed = new SliderSetting("Break speed", "x", 1.0, 1.0, 2.0, 0.02));
        this.registerSetting(this.breakDelay = new SliderSetting("Break delay", "ms", 250.0, 0.0, 250.0, 50.0));
        this.registerSetting(this.fov = new SliderSetting("FOV", "", 180.0, 30.0, 360.0, 1.0));
        this.registerSetting(this.range = new SliderSetting("Range", " block", 4.5, 2.0, 6.0, 0.1));
        this.registerSetting(this.rate = new SliderSetting("Rate", "ms", 250.0, 50.0, 2000.0, 50.0));
        this.registerSetting(this.speed = new SliderSetting("Aim speed", 10.0, 1.0, 30.0, 1.0));
        this.registerSetting(this.silentAim = new ButtonSetting("Silent aim", false));
        this.registerSetting(this.onlyOnDamage = new ButtonSetting("Activate on damage", false));
        this.registerSetting(this.breakBed = new ButtonSetting("Break bed", false));
        this.registerSetting(this.breakNearBlock = new ButtonSetting("Break near block", true));
        this.registerSetting(this.onlyNecessaryRotations = new ButtonSetting("Only necessary rotations", false));
        this.registerSetting(this.groundSpoof = new ButtonSetting("Ground spoof", false));
        this.registerSetting(this.prioritizeKillAura = new ButtonSetting("Prioritize KillAura", false));
        this.registerSetting(this.swapGroup = new GroupSetting("Swap"));
        this.registerSetting(this.swapToTools = new ButtonSetting(this.swapGroup, "Swap to tools", true));
        this.registerSetting(
                this.switchBackWhenDone =
                        new ButtonSetting(this.swapGroup, "Switch back when done", true, "Swap to previous slot"));
        this.registerSetting(this.overrideSwapBack = new ButtonSetting(this.swapGroup, "Override swap back", true));
        this.registerSetting(this.renderOutline = new ButtonSetting("Render block outline", true));
        this.registerSetting(this.outlineColor = new ColorSetting("Outline color", 255, 64, 64, 229));
    }

    @Override
    public String getInfo() {
        return MODES[(int) this.mode.getInput()];
    }

    @Override
    public void guiUpdate() {
        boolean autoMode = this.mode.getInput() == 1.0;
        this.speed.setVisible(!autoMode, this);
        this.silentAim.setVisible(!autoMode, this);
        this.breakBed.setVisible(!autoMode, this);
        this.onlyOnDamage.setVisible(!autoMode, this);
        this.breakNearBlock.setVisible(autoMode, this);
        this.onlyNecessaryRotations.setVisible(autoMode, this);
        this.swapGroup.setVisible(autoMode, this);
        this.swapToTools.setVisible(autoMode, this);
        this.switchBackWhenDone.setVisible(autoMode && this.swapToTools.isToggled(), this);
        this.overrideSwapBack.setVisible(autoMode && this.swapToTools.isToggled(), this);
        this.outlineColor.setVisible(this.renderOutline.isToggled(), this);
    }

    @Override
    public void onDisable() {
        this.resetMining();
        this.miningPacketRotations = null;
        this.bedPairsCache.clear();
        this.scanCooldown = 0;
        this.activeMode = -1;
        this.resetLegitDamageCountdown();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        this.applyMiningKeyState();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (!this.usesNecessaryRotations() && this.shouldSuppressManualMouse()) {
            if (e.button == 0 || e.button == 1) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onPreAttack(PreAttackEvent e) {
        if (this.usesNecessaryRotations() && this.isActivelyMining()) {
            if (e.objectMouseOver == null || e.objectMouseOver.type != Type.ENTITY) {
                e.setCanceled(true);
            }
        } else if (this.shouldSuppressManualMouse()) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSlotScroll(PreSlotScrollEvent e) {
        if (this.shouldSuppressManualMouse()) {
            if (this.hasSwapped && this.overrideSwapBack.isToggled() && Utils.nullCheck()) {
                int slot = Integer.compare(e.slot, 0);
                this.previousSlot =
                        Math.floorMod(mc.player.inventory.selectedSlot - slot, PlayerInventory.getHotbarSize());
            }

            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGH)
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (this.shouldSuppressManualMouse() && this.hotbarProgrammaticDepth <= 0) {
            if (this.hasSwapped && this.overrideSwapBack.isToggled()) {
                this.previousSlot = e.slot;
            }

            e.setCanceled(true);
        }
    }

    private boolean shouldSuppressManualMouse() {
        return this.isAutoMode()
                && this.miningActive
                && this.isEnabled()
                && Utils.nullCheck()
                && mc.screen == null
                && this.canMineBlocks()
                && !this.shouldYieldToKillAura();
    }

    public void applyMiningKeyState() {
        if (this.canMineBlocks() && !this.shouldYieldToKillAura()) {
            if (this.usesNecessaryRotations()) {
                this.restoreInput();
            } else {
                boolean shouldBreak = this.isAutoMode() || this.breakBed.isToggled() && this.isLookingAtTarget();
                if (this.miningActive && shouldBreak && this.isEnabled() && Utils.nullCheck() && mc.screen == null) {
                    int atk = mc.options.attackKey.getKeyCode();
                    this.controlsInput = true;
                    if (this.isAutoMode()) {
                        KeyBinding.set(atk, false);
                        KeyBinding.set(mc.options.useKey.getKeyCode(), false);
                    }

                    KeyBinding.set(atk, true);
                } else {
                    this.restoreInput();
                }
            }
        } else {
            if (this.miningActive) {
                this.resetMining();
            }
        }
    }

    public BlockPos getAuraTargetPos() {
        return this.miningActive && this.canMineBlocks() ? this.targetPos : null;
    }

    public boolean isActivelyMining() {
        return this.miningActive
                && (this.isAutoMode() || this.breakBed.isToggled() && this.isLookingAtTarget())
                && this.isEnabled()
                && Utils.nullCheck()
                && mc.screen == null
                && this.canMineBlocks()
                && !this.shouldYieldToKillAura();
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
        return Math.max(0, Math.min(5, (int) (this.breakDelay.getInput() / 50.0)));
    }

    public float getAuraBreakProgress() {
        if (this.canMineBlocks() && this.miningActive && mc.interactionManager != null) {
            IAccessorPlayerControllerMP pc = (IAccessorPlayerControllerMP) mc.interactionManager;
            BlockPos currentBlock = pc.getCurrentBlock();
            return this.targetPos != null && currentBlock != null && this.targetPos.equals(currentBlock)
                    ? pc.getCurBlockDamageMP()
                    : 0.0F;
        } else {
            return 0.0F;
        }
    }

    public boolean shouldOverrideMouseOver() {
        return this.isEnabled()
                && !this.usesNecessaryRotations()
                && this.miningActive
                && Utils.nullCheck()
                && this.canMineBlocks()
                && this.targetPos != null
                && this.targetHitVec != null
                && this.targetSide != null
                && !this.shouldYieldToKillAura();
    }

    public void modifyMouseOverFromGetMouseOver(float partialTicks) {
        if (this.shouldOverrideMouseOver()) {
            if (this.isAutoMode()) {
                if (mc.getCamera() != null) {
                    HitResult mop = new HitResult(this.targetHitVec, this.targetSide, this.targetPos);
                    mc.crosshairTarget = mop;
                    mc.targetEntity = null;
                    GameRenderer renderer = mc.gameRenderer;
                    if (renderer instanceof IAccessorEntityRenderer) {
                        ((IAccessorEntityRenderer) renderer).setPointedEntity(null);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        int selectedMode = (int) this.mode.getInput();
        boolean necessaryRotations = this.usesNecessaryRotations();
        if (this.activeMode != selectedMode || this.activeNecessaryRotations != necessaryRotations) {
            this.resetMining();
            this.bedPairsCache.clear();
            this.scanCooldown = 0;
            this.activeMode = selectedMode;
            this.activeNecessaryRotations = necessaryRotations;
            this.resetLegitDamageCountdown();
        }

        if (!this.isEnabled() || !Utils.nullCheck() || mc.screen != null || !this.canMineBlocks()) {
            this.resetMining();
        } else if (this.shouldYieldToKillAura()) {
            this.resetMining();
        } else if (e.scriptRotations) {
            this.resetMining();
        } else if (!this.isAutoMode() && !this.legitConditionsMet()) {
            this.resetMining();
        } else {
            double reach = this.range.getInput();
            double reachSq = reach * reach;
            if (--this.scanCooldown <= 0) {
                this.scanCooldown = Math.max(1, (int) Math.round(this.rate.getInput() / 50.0));
                this.rebuildBedPairsCache(reach + 1.0);
            }

            if (this.bedPairsCache.isEmpty()) {
                if (!this.isAutoMode() && this.legitDamageAssistActive) {
                    this.resetLegitDamageCountdown();
                }

                this.resetMining();
            } else {
                BedAura.Choice best =
                        this.isAutoMode() ? this.chooseBestTarget(reachSq) : this.chooseBestLegitTarget(reachSq);
                if (best == null) {
                    if (!this.isAutoMode() && this.legitDamageAssistActive) {
                        this.resetLegitDamageCountdown();
                    }

                    this.resetMining();
                } else {
                    this.targetPos = best.pos;
                    this.targetHitVec = best.hitVec;
                    this.targetSide = best.side;
                    this.miningActive = true;
                    if (!this.isAutoMode() && this.onlyOnDamage.isToggled()) {
                        this.legitDamageAssistActive = true;
                    }

                    if (this.isAutoMode()) {
                        this.equipBestHotbarTool(BlockUtils.getBlock(this.targetPos));
                    }

                    if (!necessaryRotations) {
                        if (this.isAutoMode() || this.silentAim.isToggled()) {
                            float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                            float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                            float[] targetRotations = RotationUtils.getRotationsToPoint(
                                    this.targetHitVec.x, this.targetHitVec.y, this.targetHitVec.z, baseYaw, basePitch);
                            float[] r = this.isAutoMode()
                                    ? targetRotations
                                    : RotationUtils.smoothRotation(
                                            baseYaw, basePitch, targetRotations[0], targetRotations[1], (int)
                                                    this.speed.getInput());
                            e.setYaw(r[0]);
                            e.setPitch(r[1]);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (this.isEnabled()
                && !this.isAutoMode()
                && !this.silentAim.isToggled()
                && this.miningActive
                && Utils.nullCheck()
                && mc.screen == null
                && this.canMineBlocks()
                && !this.shouldYieldToKillAura()
                && this.legitConditionsMet()) {
            float baseYaw = mc.player.yaw;
            float basePitch = mc.player.pitch;
            float[] targetRotations = RotationUtils.getRotationsToPoint(
                    this.targetHitVec.x, this.targetHitVec.y, this.targetHitVec.z, baseYaw, basePitch);
            float[] r = RotationUtils.smoothRotation(
                    baseYaw, basePitch, targetRotations[0], targetRotations[1], (int) this.speed.getInput());
            mc.player.yaw = r[0];
            mc.player.pitch = r[1];
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (this.isEnabled()
                && this.renderOutline.isToggled()
                && this.miningActive
                && this.targetPos != null
                && Utils.nullCheck()
                && this.canMineBlocks()) {
            BlockState st = mc.world.getBlockState(this.targetPos);
            Block b = st.getBlock();
            if (b != null && b != Blocks.AIR) {
                int c = this.outlineColor.getColor();
                BlockOverlay.renderBlockOutline(this.targetPos, c, c, 2.0F, true);
            }
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
        this.targetHitVec = null;
        this.targetSide = null;
        this.legitBedFoot = null;
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

    private void rebuildBedPairsCache(double searchRange) {
        this.bedPairsCache.clear();
        Set<BlockPos> seenFeet = new HashSet<>();
        int ri = (int) Math.ceil(searchRange);
        int originX = MathHelper.floor(mc.player.x);
        int originY = MathHelper.floor(mc.player.y);
        int originZ = MathHelper.floor(mc.player.z);

        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos p = new BlockPos(originX + dx, originY + dy, originZ + dz);
                    BlockPos[] pair = this.footHeadPair(p);
                    if (pair != null) {
                        BlockPos foot = pair[0];
                        if (!seenFeet.contains(foot) && this.bedInSearchRange(pair, searchRange)) {
                            Vec3d center = this.bedCenter(pair);
                            if (this.inFov(center, (float) this.fov.getInput())) {
                                seenFeet.add(foot);
                                this.bedPairsCache.add(pair);
                            }
                        }
                    }
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
        if (eye.squaredDistanceTo(onBox) <= r2) {
            return true;
        }

        Vec3d mid = new Vec3d((u.minX + u.maxX) * 0.5, (u.minY + u.maxY) * 0.5, (u.minZ + u.maxZ) * 0.5);
        return eye.squaredDistanceTo(mid) <= r2;
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
        double dot = look.x * to.x + look.y * to.y + look.z * to.z;
        double ang = Math.acos(MathHelper.clamp(dot, -1.0, 1.0)) * (180.0 / Math.PI);
        return ang <= fovDeg * 0.5;
    }

    private BedAura.Choice chooseBestLegitTarget(double reachSq) {
        List<BlockPos[]> exposed = new ArrayList<>();

        for (BlockPos[] pair : this.bedPairsCache) {
            if (this.isBedExposed(pair)) {
                exposed.add(pair);
            }
        }

        this.sortBedsByEyeDistance(exposed);
        if (this.legitBedFoot != null) {
            for (BlockPos[] pair : exposed) {
                if (this.legitBedFoot.equals(pair[0])) {
                    BedAura.Choice locked = this.getLockedLegitChoice(pair, reachSq);
                    if (locked != null) {
                        return locked;
                    }

                    BedAura.Choice replacement = this.chooseLegitPoint(pair, reachSq);
                    if (replacement != null) {
                        return replacement;
                    }
                    break;
                }
            }

            this.legitBedFoot = null;
        }

        for (BlockPos[] pair : exposed) {
            BedAura.Choice choice = this.chooseLegitPoint(pair, reachSq);
            if (choice != null) {
                this.legitBedFoot = pair[0];
                return choice;
            }
        }

        return null;
    }

    private BedAura.Choice chooseLegitPoint(BlockPos[] pair, double reachSq) {
        Vec3d eye = mc.player.getEyePosition(1.0F);
        List<BedAura.Choice> candidates = new ArrayList<>();
        Box bedBox = BlockUtils.unionBlockBounds(pair[0], pair[1]);
        this.addLegitBedCandidates(pair, bedBox, reachSq, candidates);
        return candidates.isEmpty()
                ? null
                : Collections.min(
                        candidates,
                        Comparator.<BedAura.Choice>comparingDouble(choice -> this.legitAimScore(choice, eye))
                                .thenComparingDouble(choice -> eye.squaredDistanceTo(choice.hitVec)));
    }

    private BedAura.Choice getLockedLegitChoice(BlockPos[] pair, double reachSq) {
        if (this.targetPos != null
                && this.targetHitVec != null
                && this.targetSide != null
                && (this.targetPos.equals(pair[0]) || this.targetPos.equals(pair[1]))) {
            Vec3d eye = mc.player.getEyePosition(1.0F);
            if (eye.squaredDistanceTo(this.targetHitVec) > reachSq + 0.001) {
                return null;
            }

            Vec3d inside = this.targetHitVec.add(
                    -this.targetSide.getOffsetX() * 0.05,
                    -this.targetSide.getOffsetY() * 0.05,
                    -this.targetSide.getOffsetZ() * 0.05);
            HitResult trace = mc.world.rayTrace(eye, inside, false, false, false);
            return trace != null
                            && trace.type == Type.BLOCK
                            && this.targetPos.equals(trace.getPos())
                            && trace.facePos != null
                            && trace.face != null
                            && this.isCenteredBedHit(trace.getPos(), trace.facePos, trace.face)
                            && !(this.faceAlignment(eye, trace.facePos, trace.face) < 0.08)
                    ? new BedAura.Choice(this.targetPos, this.targetHitVec, this.targetSide)
                    : null;
        } else {
            return null;
        }
    }

    private void addLegitBedCandidates(BlockPos[] pair, Box box, double reachSq, List<BedAura.Choice> out) {
        Vec3d eye = mc.player.getEyePosition(1.0F);
        double[] samples = new double[] {0.5, 0.25, 0.75, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8};

        for (Direction face : Direction.values()) {
            if (face != Direction.DOWN) {
                for (double first : samples) {
                    for (double second : samples) {
                        double x = (box.minX + box.maxX) * 0.5;
                        double y = (box.minY + box.maxY) * 0.5;
                        double z = (box.minZ + box.maxZ) * 0.5;
                        if (face == Direction.UP) {
                            x = this.interpolate(box.minX, box.maxX, first);
                            y = box.maxY;
                            z = this.interpolate(box.minZ, box.maxZ, second);
                        } else if (face != Direction.NORTH && face != Direction.SOUTH) {
                            x = face == Direction.WEST ? box.minX : box.maxX;
                            y = this.interpolate(box.minY, box.maxY, first);
                            z = this.interpolate(box.minZ, box.maxZ, second);
                        } else {
                            x = this.interpolate(box.minX, box.maxX, first);
                            y = this.interpolate(box.minY, box.maxY, second);
                            z = face == Direction.NORTH ? box.minZ : box.maxZ;
                        }

                        this.addLegitBedCandidate(pair, face, new Vec3d(x, y, z), eye, reachSq, out);
                    }
                }
            }
        }
    }

    private void addLegitBedCandidate(
            BlockPos[] pair, Direction face, Vec3d point, Vec3d eye, double reachSq, List<BedAura.Choice> out) {
        Vec3d inside = point.add(-face.getOffsetX() * 0.05, -face.getOffsetY() * 0.05, -face.getOffsetZ() * 0.05);
        HitResult trace = mc.world.rayTrace(eye, inside, false, false, false);
        if (trace != null
                && trace.type == Type.BLOCK
                && trace.facePos != null
                && trace.face != null
                && (pair[0].equals(trace.getPos()) || pair[1].equals(trace.getPos()))) {
            if (!(eye.squaredDistanceTo(trace.facePos) > reachSq + 0.001)) {
                if (this.isCenteredBedHit(trace.getPos(), trace.facePos, trace.face)) {
                    if (!(this.faceAlignment(eye, trace.facePos, trace.face) < 0.08)) {
                        out.add(new BedAura.Choice(trace.getPos(), trace.facePos, trace.face));
                    }
                }
            }
        }
    }

    private boolean isCenteredBedHit(BlockPos pos, Vec3d hit, Direction side) {
        Box box = BlockUtils.getBlockSelectionBox(pos);
        if (box == null) {
            return false;
        } else {
            double marginX = Math.min(0.15, (box.maxX - box.minX) * 0.25);
            double marginY = Math.min(0.12, (box.maxY - box.minY) * 0.25);
            double marginZ = Math.min(0.15, (box.maxZ - box.minZ) * 0.25);
            boolean centeredX = hit.x >= box.minX + marginX && hit.x <= box.maxX - marginX;
            boolean centeredY = hit.y >= box.minY + marginY && hit.y <= box.maxY - marginY;
            boolean centeredZ = hit.z >= box.minZ + marginZ && hit.z <= box.maxZ - marginZ;
            if (side != Direction.UP && side != Direction.DOWN) {
                return side != Direction.NORTH && side != Direction.SOUTH
                        ? centeredY && centeredZ
                        : centeredX && centeredY;
            } else {
                return centeredX && centeredZ;
            }
        }
    }

    private double legitAimScore(BedAura.Choice choice, Vec3d eye) {
        Box box = BlockUtils.getBlockSelectionBox(choice.pos);
        if (box == null) {
            return Double.POSITIVE_INFINITY;
        }

        double x = (choice.hitVec.x - (box.minX + box.maxX) * 0.5) / (box.maxX - box.minX);
        double y = (choice.hitVec.y - (box.minY + box.maxY) * 0.5) / (box.maxY - box.minY);
        double z = (choice.hitVec.z - (box.minZ + box.maxZ) * 0.5) / (box.maxZ - box.minZ);
        double alignment = this.faceAlignment(eye, choice.hitVec, choice.side);
        return x * x + y * y + z * z + (1.0 - alignment) * 0.75;
    }

    private double faceAlignment(Vec3d eye, Vec3d hit, Direction side) {
        double x = eye.x - hit.x;
        double y = eye.y - hit.y;
        double z = eye.z - hit.z;
        double length = Math.sqrt(x * x + y * y + z * z);
        return length < 1.0E-6
                ? 1.0
                : Math.max(0.0, (x * side.getOffsetX() + y * side.getOffsetY() + z * side.getOffsetZ()) / length);
    }

    private double interpolate(double min, double max, double amount) {
        return min + (max - min) * amount;
    }

    private BedAura.Choice chooseBestTarget(double reachSq) {
        IAccessorPlayerControllerMP pc = (IAccessorPlayerControllerMP) mc.interactionManager;
        float curProg = pc.getCurBlockDamageMP();
        BlockPos breaking = pc.getCurrentBlock();
        List<BlockPos[]> exposed = new ArrayList<>();
        List<BlockPos[]> covered = new ArrayList<>();

        for (BlockPos[] pair : this.bedPairsCache) {
            if (this.isBedExposed(pair)) {
                exposed.add(pair);
            } else {
                covered.add(pair);
            }
        }

        this.sortBedsByEyeDistance(exposed);
        this.sortBedsByEyeDistance(covered);
        BedAura.Choice c = this.pickBestOnClosestBedWithCandidates(exposed, reachSq, curProg, breaking);
        return c != null ? c : this.pickBestOnClosestBedWithCandidates(covered, reachSq, curProg, breaking);
    }

    private void sortBedsByEyeDistance(List<BlockPos[]> pairs) {
        Vec3d eye = mc.player.getEyePosition(1.0F);
        pairs.sort(Comparator.comparingDouble(p -> eye.squaredDistanceTo(this.bedCenter(p))));
    }

    private BedAura.Choice pickBestOnClosestBedWithCandidates(
            List<BlockPos[]> sortedPairs, double reachSq, float curProg, BlockPos breaking) {
        for (BlockPos[] pair : sortedPairs) {
            List<BedAura.Choice> candidates = this.buildCandidates(pair, reachSq);
            if (!candidates.isEmpty()) {
                BedAura.Choice best = null;
                double bestScore = Double.POSITIVE_INFINITY;

                for (BedAura.Choice ch : candidates) {
                    double score = this.scoreChoice(ch, curProg, breaking);
                    if (score < bestScore) {
                        bestScore = score;
                        best = ch;
                    }
                }

                return best;
            }
        }

        return null;
    }

    private double scoreChoice(BedAura.Choice ch, float curProg, BlockPos breaking) {
        Block block = BlockUtils.getBlock(ch.pos);
        float bestHotbar = BlockUtils.maxDigRateAcrossSlots(block, PlayerInventory.getHotbarSize())
                * this.getGroundSpoofMultiplier();
        if (bestHotbar <= 0.0F) {
            return Double.POSITIVE_INFINITY;
        }

        double timeEst = 1.0 / bestHotbar;
        if (breaking != null && breaking.equals(ch.pos) && curProg > 0.02F) {
            timeEst -= curProg * 12.0;
        }

        Vec3d eye = mc.player.getEyePosition(1.0F);
        return timeEst + eye.squaredDistanceTo(ch.hitVec) * 0.002;
    }

    private List<BedAura.Choice> buildCandidates(BlockPos[] pair, double reachSq) {
        List<BedAura.Choice> out = new ArrayList<>();
        if (!this.breakNearBlock.isToggled()) {
            for (BlockPos bp : pair) {
                this.addBlockCandidate(bp, reachSq, out);
            }

            return out;
        } else {
            boolean exposed = this.isBedExposed(pair);
            if (exposed) {
                for (BlockPos bp : pair) {
                    this.addBlockCandidate(bp, reachSq, out);
                }
            } else {
                Set<BlockPos> seen = new HashSet<>();

                for (BlockPos bp : pair) {
                    for (Direction f : Direction.values()) {
                        if (f != Direction.DOWN) {
                            BlockPos n = bp.offset(f);
                            if (!seen.contains(n)) {
                                BlockState st = mc.world.getBlockState(n);
                                Block b = st.getBlock();
                                if (!this.canHitThrough(b) && !(b instanceof BedBlock)) {
                                    float hard = b.getMiningTime(mc.world, n);
                                    if (!(hard < 0.0F)) {
                                        seen.add(n);
                                        this.addBlockCandidate(n, reachSq, out);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            return out;
        }
    }

    private boolean isBedExposed(BlockPos[] pair) {
        for (BlockPos bp : pair) {
            for (Direction f : Direction.values()) {
                BlockPos n = bp.offset(f);
                if (this.canHitThrough(mc.world.getBlockState(n).getBlock())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean canHitThrough(Block block) {
        return block == Blocks.AIR
                || block == Blocks.IRON_BARS
                || block == Blocks.WATER
                || block == Blocks.FLOWING_WATER
                || block == Blocks.LAVA
                || block == Blocks.FLOWING_LAVA
                || block == Blocks.TALLGRASS
                || block == Blocks.RED_FLOWER
                || block == Blocks.YELLOW_FLOWER
                || block == Blocks.DOUBLE_PLANT
                || block == Blocks.DEADBUSH
                || block == Blocks.VINE
                || block == Blocks.END_PORTAL_FRAME
                || block instanceof FenceBlock
                || block instanceof WallBlock
                || block instanceof FenceGateBlock
                || block instanceof PaneBlock;
    }

    private void addBlockCandidate(BlockPos pos, double reachSq, List<BedAura.Choice> out) {
        BlockState st = mc.world.getBlockState(pos);
        Block block = st.getBlock();
        if (block != Blocks.AIR) {
            float hard = block.getMiningTime(mc.world, pos);
            if (!(hard < 0.0F)) {
                Box bb = BlockUtils.getBlockSelectionBox(pos);
                if (bb != null) {
                    Vec3d eye = mc.player.getEyePosition(1.0F);
                    Vec3d hit = RotationUtils.closestPointOnAabb(bb, eye);
                    if (!(eye.squaredDistanceTo(hit) > reachSq + 0.001)) {
                        HitResult trace = block.rayTrace(
                                mc.world,
                                pos,
                                eye,
                                hit.add((hit.x - eye.x) * 0.01, (hit.y - eye.y) * 0.01, (hit.z - eye.z) * 0.01));
                        Direction side = BlockUtils.facingFromBlockCenterToPoint(pos, hit);
                        if (trace != null
                                && trace.facePos != null
                                && trace.face != null
                                && pos.equals(trace.getPos())) {
                            hit = trace.facePos;
                            side = trace.face;
                        }

                        if (!(block instanceof BedBlock) || side != Direction.DOWN) {
                            out.add(new BedAura.Choice(pos, hit, side));
                        }
                    }
                }
            }
        }
    }

    private void equipBestHotbarTool(Block block) {
        if (this.swapToTools.isToggled()) {
            int slot = Utils.getTool(block);
            if (slot >= 0) {
                if (this.previousSlot == -1 && slot != mc.player.inventory.selectedSlot) {
                    this.previousSlot = mc.player.inventory.selectedSlot;
                }

                if (slot != mc.player.inventory.selectedSlot) {
                    this.setSlot(slot);
                }
            }
        }
    }

    private void setSlot(int slot) {
        if (this.swapToTools.isToggled() && slot != -1 && slot != mc.player.inventory.selectedSlot) {
            this.hotbarProgrammaticDepth++;

            try {
                mc.player.inventory.selectedSlot = slot;
                this.hasSwapped = true;
                ((IAccessorPlayerControllerMP) mc.interactionManager).callSyncCurrentPlayItem();
            } finally {
                this.hotbarProgrammaticDepth--;
            }
        }
    }

    private boolean canMineBlocks() {
        return Utils.nullCheck()
                && mc.interactionManager != null
                && mc.player.abilities.canModifyWorld
                && !mc.player.abilities.creativeMode
                && !mc.player.isSpectator();
    }

    private boolean isAutoMode() {
        return this.mode.getInput() == 1.0;
    }

    private boolean usesNecessaryRotations() {
        return this.isAutoMode() && this.onlyNecessaryRotations.isToggled();
    }

    private boolean legitConditionsMet() {
        if (!this.onlyOnDamage.isToggled()) {
            this.resetLegitDamageCountdown();
            return true;
        }

        int currentTick = mc.player.ticks;
        if (this.legitDamageCountdownTick != currentTick) {
            int hurtTime = mc.player.damagedTimer;
            if (hurtTime <= 0 || this.previousHurtTime != 0 && hurtTime <= this.previousHurtTime) {
                if (!this.legitDamageAssistActive && this.legitDamageTicksRemaining > 0) {
                    this.legitDamageTicksRemaining--;
                }
            } else {
                this.legitDamageTicksRemaining = 20;
            }

            this.previousHurtTime = hurtTime;
            this.legitDamageCountdownTick = currentTick;
        }

        return this.legitDamageAssistActive || this.legitDamageTicksRemaining > 0;
    }

    private void resetLegitDamageCountdown() {
        this.legitDamageTicksRemaining = 0;
        this.legitDamageCountdownTick = Integer.MIN_VALUE;
        this.previousHurtTime = Utils.nullCheck() ? mc.player.damagedTimer : 0;
        this.legitDamageAssistActive = false;
    }

    private boolean isLookingAtTarget() {
        HitResult mouseOver = mc.crosshairTarget;
        return this.targetPos != null
                && mouseOver != null
                && mouseOver.type == Type.BLOCK
                && this.targetPos.equals(mouseOver.getPos());
    }

    private boolean shouldYieldToKillAura() {
        return !this.prioritizeKillAura.isToggled()
                ? false
                : ModuleManager.killAura != null && ModuleManager.killAura.hasPriorityTarget();
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
