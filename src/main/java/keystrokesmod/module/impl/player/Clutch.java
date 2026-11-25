package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Mouse;

public class Clutch extends Module {
    private static final Map<String, Integer> BLOCK_SCORE = new HashMap<>();
    private static final double HALF_WIDTH = 0.3;
    private static final double[][] CORNERS = new double[][] {{-0.3, -0.3}, {0.3, -0.3}, {-0.3, 0.3}, {0.3, 0.3}};
    private final SliderSetting reach;
    private final SliderSetting speed;
    private final SliderSetting snapbackSpeed;
    private final SliderSetting maxDistance;
    private final SliderSetting rotationTolerance;
    private final ButtonSetting simulateFuturePosition;
    private final ButtonSetting autoClutch;
    private final ButtonSetting requireVoid;
    private final SliderSetting minimumFallDistance;
    private final KeySetting selectKeybind;
    private final ItemListSetting itemBlacklist;
    private BlockPos placeAtBlock;
    private Direction hitSide;
    private Vec3d hitVec;
    private boolean placeQueued;
    private boolean placing;
    private boolean slotWasSwapped;
    private boolean autoClickerWasOn;
    private int prevSlot = -1;
    private int plannedSlot = -1;
    private float aimYaw;
    private float aimPitch;
    private BlockPos targetHitPos;
    private Direction targetSide;
    private boolean hasAim;
    private boolean resetting;
    private BlockPos lastPlaced;
    private int clutchBlocksPlaced;
    private boolean autoClutchActive;
    private boolean autoClutchChecking;
    private int autoClutchCheckCounter;
    private boolean autoClutchLandedGuard;
    private int autoClutchLandedTick;
    private int prevHurtTime = -1;

    public Clutch() {
        super("Clutch", Module.category.player);
        this.registerSetting(this.reach = new SliderSetting("Reach", " block", 4.5, 0.5, 4.5, 0.1));
        this.registerSetting(this.speed = new SliderSetting("Speed", 8.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.snapbackSpeed = new SliderSetting("Snapback Speed", 12.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.maxDistance = new SliderSetting("Max distance", " block", 10.0, 0.0, 20.0, 1.0));
        this.registerSetting(
                this.rotationTolerance = new SliderSetting("Rotation Tolerance", "°", 25.0, 20.0, 100.0, 1.0));
        this.registerSetting(
                this.minimumFallDistance = new SliderSetting("Minimum fall distance", " block", 10.0, 3.0, 20.0, 1.0));
        this.registerSetting(this.simulateFuturePosition = new ButtonSetting("Simulate future position", true));
        this.registerSetting(this.autoClutch = new ButtonSetting("Auto Clutch", false));
        this.registerSetting(this.requireVoid = new ButtonSetting("Require void", false));
        this.registerSetting(this.selectKeybind = new KeySetting("Select Keybind", 0));
        this.registerSetting(this.itemBlacklist = new ItemListSetting("Item blacklist"));
        this.closetModule = true;
    }

    @Override
    public void onEnable() {
        this.hasAim = false;
        this.resetting = false;
        this.clutchBlocksPlaced = 0;
        this.autoClutchActive = false;
        this.autoClutchChecking = false;
        this.autoClutchCheckCounter = 0;
        this.autoClutchLandedGuard = false;
        this.autoClutchLandedTick = 0;
        this.prevHurtTime = -1;
    }

    @Override
    public void onDisable() {
        this.clearAim(false);
        this.disablePlacing(true);
        this.placeQueued = false;
        this.autoClutchActive = false;
        this.autoClutchChecking = false;
        this.autoClutchLandedGuard = false;
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (Utils.nullCheck()) {
            if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
                this.runPrePlayerInteract();
                if (mc.screen != null) {
                    this.disablePlacing(false);
                }

                float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                if (this.resetting) {
                    this.aimYaw = mc.player.yaw;
                    this.aimPitch = mc.player.pitch;
                    float[] smoothed = this.getRotationsSmoothed(baseYaw, basePitch, this.aimYaw, this.aimPitch, true);
                    if (Math.abs(MathHelper.wrapDegrees(smoothed[0] - this.aimYaw)) < 0.5F
                            && Math.abs(smoothed[1] - this.aimPitch) < 0.5F) {
                        this.resetting = false;
                        this.restoreInputsAndAutoClicker();
                    } else {
                        RotationHelper.get().forceMovementFix = true;
                        e.setYaw(smoothed[0]);
                        e.setPitch(smoothed[1]);
                    }
                } else if (this.hasAim) {
                    float[] smoothed = this.getRotationsSmoothed(baseYaw, basePitch, this.aimYaw, this.aimPitch, false);
                    if (this.placing && this.targetHitPos != null) {
                        HitResult mop = RotationUtils.rayCastBlock(this.reach.getInput(), smoothed[0], smoothed[1]);
                        if (mop != null && this.targetHitPos.equals(mop.getPos()) && this.targetSide == mop.face) {
                            int maxBlocks = (int) this.maxDistance.getInput();
                            if (maxBlocks == 0 || this.clutchBlocksPlaced < maxBlocks) {
                                double tolerance = this.rotationTolerance.getInput();
                                if (Math.abs(MathHelper.wrapDegrees(smoothed[0] - RotationUtils.serverRotations[0]))
                                                <= tolerance
                                        && Math.abs(smoothed[1] - RotationUtils.serverRotations[1]) <= tolerance) {
                                    this.placeAtBlock = mop.getPos();
                                    this.hitSide = mop.face;
                                    this.hitVec = mop.facePos;
                                    this.placeQueued = true;
                                }
                            }
                        }
                    }

                    RotationHelper.get().forceMovementFix = true;
                    e.setYaw(smoothed[0]);
                    e.setPitch(smoothed[1]);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (Utils.nullCheck() && this.placeQueued) {
            this.placeQueued = false;
            if (this.canClutchHere()) {
                if (this.placeAtBlock != null
                        && this.hitSide != null
                        && this.hitVec != null
                        && mc.interactionManager.useBlock(
                                mc.player,
                                mc.world,
                                mc.player.getDisplayItemInHand(),
                                this.placeAtBlock,
                                this.hitSide,
                                this.hitVec)) {
                    if (this.hitSide != Direction.UP) {
                        this.clutchBlocksPlaced++;
                    }

                    this.lastPlaced = this.placeAtBlock;
                    mc.player.swingArm();
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if ((this.placing || this.resetting || this.hasAim) && e.button > -1) {
            int keyCode = e.button - 100;
            if (keyCode == mc.options.attackKey.getKeyCode() || keyCode == mc.options.useKey.getKeyCode()) {
                e.setCanceled(true);
            }
        }
    }

    public boolean isActiveWindow() {
        return this.isEnabled() && (this.placing || this.resetting || this.hasAim || this.placeQueued);
    }

    private void runPrePlayerInteract() {
        if (mc.player.onGround) {
            this.clutchBlocksPlaced = 0;
        }

        int ticksExisted = mc.player.ticks;
        this.updateAutoClutch(ticksExisted);
        boolean active = this.selectKeybind.isPressed() || this.autoClutchActive;
        if (mc.screen == null && active && this.canClutchHere()) {
            BlockPos below = new BlockPos(
                    MathHelper.floor(mc.player.x), MathHelper.floor(mc.player.y) - 1, MathHelper.floor(mc.player.z));
            if (!this.canPlaceThrough(below)) {
                this.disablePlacing(false);
            } else {
                int weakSlot = this.pickBlockSlot();
                if (weakSlot == -1) {
                    this.disablePlacing(false);
                } else {
                    this.plannedSlot = weakSlot;
                    Clutch.AimResult target = this.clutchAim();
                    if (target != null) {
                        this.targetHitPos = target.ray.getPos();
                        this.targetSide = target.ray.face;
                        this.aimYaw = target.yaw;
                        this.aimPitch = target.pitch;
                        this.hasAim = true;
                        this.resetting = false;
                    }

                    if (this.hasAim && !this.placing) {
                        this.enablePlacing();
                    }

                    if (this.placing || this.resetting || this.hasAim) {
                        KeyBinding.set(mc.options.attackKey.getKeyCode(), false);
                        KeyBinding.set(mc.options.useKey.getKeyCode(), false);
                        this.equipPlannedSlot();
                    }
                }
            }
        } else {
            this.clearAim(true);
            this.disablePlacing(false);
        }
    }

    private void updateAutoClutch(int ticksExisted) {
        if (this.autoClutch.isToggled()) {
            int curHurtTime = mc.player.damagedTimer;
            if (curHurtTime > this.prevHurtTime) {
                this.autoClutchChecking = true;
                this.autoClutchCheckCounter = 0;
                this.autoClutchLandedGuard = false;
            }

            this.prevHurtTime = curHurtTime;
            if (this.autoClutchChecking && !this.autoClutchActive && !this.autoClutchLandedGuard) {
                if ((this.autoClutchCheckCounter == 0 || this.autoClutchCheckCounter % 3 == 0)
                        && this.willFallFar(this.minimumFallDistance.getInput())) {
                    this.autoClutchActive = true;
                }

                this.autoClutchCheckCounter++;
            }

            if (this.autoClutchLandedGuard) {
                boolean expired = ticksExisted - this.autoClutchLandedTick >= 10;
                boolean jumped = mc.options.jumpKey.isPressed();
                boolean airborneUp = !mc.player.onGround && mc.player.velocityY > 0.0;
                if (expired || jumped || airborneUp) {
                    this.autoClutchActive = false;
                    this.autoClutchChecking = false;
                    this.autoClutchLandedGuard = false;
                }
            }

            if (this.autoClutchActive
                    && mc.player.onGround
                    && mc.player.damagedTimer < mc.player.damagedTime - 2
                    && !this.autoClutchLandedGuard) {
                this.autoClutchLandedGuard = true;
                this.autoClutchLandedTick = ticksExisted;
                if (!this.willFallSoon()) {
                    this.autoClutchActive = false;
                    this.autoClutchChecking = false;
                    this.autoClutchLandedGuard = false;
                }
            }

            if (!this.autoClutchActive
                    && !this.autoClutchLandedGuard
                    && mc.player.onGround
                    && mc.player.damagedTimer == 0) {
                this.autoClutchChecking = false;
                this.autoClutchCheckCounter = 0;
            }
        } else {
            this.autoClutchActive = false;
            this.autoClutchChecking = false;
            this.autoClutchLandedGuard = false;
            this.prevHurtTime = mc.player.damagedTimer;
        }
    }

    void adoptLadderSlot(int slot) {
        if (!this.slotWasSwapped) {
            this.prevSlot = slot;
            this.slotWasSwapped = true;
        }
    }

    private void enablePlacing() {
        if (!this.placing) {
            Module ladderClutch = ModuleManager.getModule(AutoLadder.class);
            if (ladderClutch instanceof AutoLadder) {
                int savedSlot = ((AutoLadder) ladderClutch).yieldToClutch();
                if (savedSlot >= 0 && !this.slotWasSwapped) {
                    this.prevSlot = savedSlot;
                    this.slotWasSwapped = true;
                }
            }

            this.placing = true;
            if (!this.slotWasSwapped) {
                this.prevSlot = mc.player.inventory.selectedSlot;
            }

            this.autoClickerWasOn =
                    this.autoClickerWasOn || ModuleManager.autoClicker != null && ModuleManager.autoClicker.isEnabled();
            if (this.autoClickerWasOn && ModuleManager.autoClicker != null) {
                ModuleManager.autoClicker.disable();
            }
        }
    }

    private void disablePlacing(boolean forceRestore) {
        if (this.placing || forceRestore) {
            this.placing = false;
            this.plannedSlot = -1;
            if ((forceRestore || !this.hasAim) && this.slotWasSwapped) {
                if (this.prevSlot != -1) {
                    mc.player.inventory.selectedSlot = this.prevSlot;
                }

                this.slotWasSwapped = false;
            }

            if (forceRestore) {
                this.prevSlot = -1;
                this.restoreInputsAndAutoClicker();
            }
        }
    }

    private void clearAim(boolean allowSnapback) {
        if (this.slotWasSwapped) {
            if (this.prevSlot != -1) {
                mc.player.inventory.selectedSlot = this.prevSlot;
            }

            this.slotWasSwapped = false;
        }

        this.targetHitPos = null;
        this.targetSide = null;
        this.lastPlaced = null;
        this.clutchBlocksPlaced = 0;
        if (allowSnapback && this.hasAim) {
            this.resetting = true;
        }

        this.hasAim = false;
        this.prevSlot = -1;
    }

    private void restoreInputsAndAutoClicker() {
        if (mc.screen == null) {
            KeyBinding.set(mc.options.attackKey.getKeyCode(), Mouse.isButtonDown(0));
            KeyBinding.set(mc.options.useKey.getKeyCode(), Mouse.isButtonDown(1));
        }

        if (this.autoClickerWasOn && ModuleManager.autoClicker != null) {
            ModuleManager.autoClicker.enable();
            this.autoClickerWasOn = false;
        }
    }

    private boolean willFallFar(double minFall) {
        double startY = mc.player.y;
        Clutch.PredictionState prediction = Clutch.PredictionState.fromPlayer();

        for (int t = 0; t < 60; t++) {
            prediction.tick(false);
            if (prediction.onGround) {
                return false;
            }

            double fall = startY - prediction.posY;
            if (fall > minFall) {
                return true;
            }
        }

        return false;
    }

    private boolean willFallSoon() {
        Clutch.PredictionState prediction = Clutch.PredictionState.fromPlayer();

        for (int t = 0; t < 10; t++) {
            prediction.tick(true);
            if (!prediction.onGround && prediction.motionY < 0.0) {
                return true;
            }
        }

        return false;
    }

    private Clutch.AimResult clutchAim() {
        Vec3d playerPos = new Vec3d(mc.player.x, mc.player.y, mc.player.z);
        Vec3d eye = mc.player.getEyePosition(1.0F);
        Vec3d futurePos = playerPos;
        if (this.simulateFuturePosition.isToggled()) {
            Clutch.PredictionState prediction = Clutch.PredictionState.fromPlayer();

            for (int t = 0; t < 20; t++) {
                prediction.tick(false);
                if (prediction.posY < playerPos.y - 2.0 || prediction.onGround) {
                    break;
                }
            }

            futurePos = prediction.getPos();
        }

        int feetX = MathHelper.floor(playerPos.x);
        int feetZ = MathHelper.floor(playerPos.z);
        int feetY = MathHelper.floor(playerPos.y);
        int minX = feetX - 5;
        int maxX = feetX + 4;
        int minZ = feetZ - 5;
        int maxZ = feetZ + 4;
        int maxY = feetY - 1;
        int minY = feetY - 4;
        ArrayList<Clutch.BlockCandidate> candidates = new ArrayList<>();

        for (int y = maxY; y >= minY; y--) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!this.canPlaceThrough(pos)) {
                        double currentDist = BlockUtils.dist2PointAABB(playerPos, pos);
                        double futureDist = BlockUtils.dist2PointAABB(futurePos, pos);
                        double score = this.simulateFuturePosition.isToggled()
                                ? currentDist * 0.3 + futureDist * 0.7
                                : currentDist;
                        if (pos.equals(this.lastPlaced)) {
                            score *= 0.95;
                        }

                        candidates.add(new Clutch.BlockCandidate(score, pos));
                    }
                }
            }
        }

        candidates.sort((a, b) -> Double.compare(a.score, b.score));
        ItemStack held =
                this.plannedSlot >= 0 && this.plannedSlot <= 8 ? mc.player.inventory.items[this.plannedSlot] : null;

        for (Clutch.BlockCandidate candidate : candidates) {
            boolean underPlayer = this.isBlockUnderPlayer(candidate.pos, playerPos);
            Clutch.AimResult result =
                    this.getBestRotationsToBlock(held, candidate.pos, eye, this.reach.getInput(), underPlayer);
            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private boolean isBlockUnderPlayer(BlockPos blockPos, Vec3d pos) {
        if (blockPos.getY() >= MathHelper.floor(pos.y)) {
            return false;
        }

        for (double[] corner : CORNERS) {
            int cx = MathHelper.floor(pos.x + corner[0]);
            int cz = MathHelper.floor(pos.z + corner[1]);
            if (blockPos.getX() == cx && blockPos.getZ() == cz) {
                return true;
            }
        }

        return false;
    }

    private Clutch.AimResult getBestRotationsToBlock(
            ItemStack held, BlockPos targetCell, Vec3d eye, double reachVal, boolean underPlayer) {
        double inset = 0.05;
        double step = 0.2;
        double jitter = step * 0.1;
        boolean faceSouth = Math.abs(eye.z - (targetCell.getZ() + 1)) < Math.abs(eye.z - targetCell.getZ());
        boolean faceEast = Math.abs(eye.x - (targetCell.getX() + 1)) < Math.abs(eye.x - targetCell.getX());
        float baseYaw = normYaw(RotationUtils.serverRotations[0]);
        float basePitch = RotationUtils.serverRotations[1];
        int n = (int) Math.round(1.0 / step);
        ArrayList<Clutch.RotationCandidate> candidates = new ArrayList<>();
        candidates.add(new Clutch.RotationCandidate(0.0, baseYaw, basePitch));

        for (int row = 0; row <= n; row++) {
            double v = clamp01(row * step + randomRange(-jitter, jitter));

            for (int col = 0; col <= n; col++) {
                double u = clamp01(col * step + randomRange(-jitter, jitter));
                if (underPlayer) {
                    float[] rV = getRotationsWrapped(
                            eye, targetCell.getX() + u, targetCell.getY() + 1 - inset, targetCell.getZ() + v);
                    double costV = Math.abs(wrapYawDelta(baseYaw, rV[0])) + Math.abs(rV[1] - basePitch);
                    candidates.add(new Clutch.RotationCandidate(costV, rV[0], rV[1]));
                }

                float[] rZ = getRotationsWrapped(
                        eye,
                        targetCell.getX() + u,
                        targetCell.getY() + v,
                        faceSouth ? targetCell.getZ() + 1 - inset : targetCell.getZ() + inset);
                double costZ = Math.abs(wrapYawDelta(baseYaw, rZ[0])) + Math.abs(rZ[1] - basePitch);
                candidates.add(new Clutch.RotationCandidate(costZ, rZ[0], rZ[1]));
                float[] rX = getRotationsWrapped(
                        eye,
                        faceEast ? targetCell.getX() + 1 - inset : targetCell.getX() + inset,
                        targetCell.getY() + v,
                        targetCell.getZ() + u);
                double costX = Math.abs(wrapYawDelta(baseYaw, rX[0])) + Math.abs(rX[1] - basePitch);
                candidates.add(new Clutch.RotationCandidate(costX, rX[0], rX[1]));
            }
        }

        candidates.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (Clutch.RotationCandidate candidate : candidates) {
            float yaw = unwrapYaw(candidate.yaw, RotationUtils.serverRotations[0]);
            HitResult ray = RotationUtils.rayCastBlock(reachVal, yaw, candidate.pitch);
            if (ray != null) {
                Direction face = ray.face;
                if (face != Direction.DOWN
                        && (face != Direction.UP || underPlayer)
                        && targetCell.equals(ray.getPos())
                        && BlockUtils.canPlaceBlockOnSide(held, ray.getPos(), face)) {
                    return new Clutch.AimResult(ray, yaw, candidate.pitch);
                }
            }
        }

        return null;
    }

    private int pickBlockSlot() {
        boolean playingBedwars = Utils.getBedwarsStatus() == 2;
        if (!playingBedwars) {
            int current = mc.player.inventory.selectedSlot;
            if (this.isBlockSlot(current)) {
                return current;
            }

            for (int slot = 8; slot >= 0; slot--) {
                if (this.isBlockSlot(slot)) {
                    return slot;
                }
            }

            return -1;
        } else {
            int best = -1;
            int bestScore = Integer.MIN_VALUE;

            for (int slot = 8; slot >= 0; slot--) {
                ItemStack stack = mc.player.inventory.items[slot];
                if (stack != null
                        && stack.size != 0
                        && stack.getItem() instanceof BlockItem
                        && !this.itemBlacklist.matches(stack)) {
                    Block block = ((BlockItem) stack.getItem()).getBlock();
                    Identifier id = (Identifier) Block.REGISTRY.getKey(block);
                    if (id != null) {
                        Integer score = BLOCK_SCORE.get(id.getPath());
                        if (score != null && score > bestScore) {
                            bestScore = score;
                            best = slot;
                        }
                    }
                }
            }

            return best;
        }
    }

    private boolean isBlockSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            ItemStack stack = mc.player.inventory.items[slot];
            return stack != null
                    && stack.size > 0
                    && stack.getItem() instanceof BlockItem
                    && !this.itemBlacklist.matches(stack);
        } else {
            return false;
        }
    }

    private void equipPlannedSlot() {
        int current = mc.player.inventory.selectedSlot;
        if (this.plannedSlot != -1 && this.plannedSlot != current) {
            mc.player.inventory.selectedSlot = this.plannedSlot;
            this.slotWasSwapped = true;
        }
    }

    private float[] getRotationsSmoothed(
            float currentYaw, float currentPitch, float targetYaw, float targetPitch, boolean snapback) {
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

        float maxStep = (float) (snapback ? this.snapbackSpeed.getInput() : this.speed.getInput());
        float factor = 1.0F - (float) randomRange(0.0, 0.2);
        maxStep *= factor;
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

    private boolean canPlaceThrough(BlockPos pos) {
        Block block = BlockUtils.getBlock(pos);
        Material material = block.getMaterial();
        return material == Material.AIR
                || material == Material.WATER
                || material == Material.LAVA
                || block == Blocks.FIRE;
    }

    private boolean canClutchHere() {
        return !this.requireVoid.isToggled() || Utils.overVoid(mc.player.x, mc.player.y, mc.player.z);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static double randomRange(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private static float normYaw(float yaw) {
        yaw = (yaw % 360.0F + 360.0F) % 360.0F;
        return yaw > 180.0F ? yaw - 360.0F : yaw;
    }

    private static float wrapYawDelta(float base, float target) {
        return MathHelper.wrapDegrees(target - base);
    }

    private static float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + MathHelper.wrapDegrees(yaw - prevYaw);
    }

    private static float[] getRotationsWrapped(Vec3d eye, double tx, double ty, double tz) {
        double dx = tx - eye.x;
        double dy = ty - eye.y;
        double dz = tz - eye.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, horizontalDistance));
        return new float[] {normYaw(yaw), RotationUtils.clampPitch(pitch)};
    }

    static {
        BLOCK_SCORE.put("obsidian", 0);
        BLOCK_SCORE.put("end_stone", 1);
        BLOCK_SCORE.put("planks", 2);
        BLOCK_SCORE.put("log", 2);
        BLOCK_SCORE.put("log2", 2);
        BLOCK_SCORE.put("glass", 3);
        BLOCK_SCORE.put("stained_glass", 3);
        BLOCK_SCORE.put("hardened_clay", 4);
        BLOCK_SCORE.put("stained_hardened_clay", 4);
        BLOCK_SCORE.put("stone", 5);
        BLOCK_SCORE.put("wool", 5);
    }

    private static class AimResult {
        final HitResult ray;
        final float yaw;
        final float pitch;

        AimResult(HitResult ray, float yaw, float pitch) {
            this.ray = ray;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class BlockCandidate {
        final double score;
        final BlockPos pos;

        BlockCandidate(double score, BlockPos pos) {
            this.score = score;
            this.pos = pos;
        }
    }

    private static class PredictionState {
        private Box box;
        private double motionX;
        private double motionY;
        private double motionZ;
        private double posY;
        private boolean onGround;

        static Clutch.PredictionState fromPlayer() {
            Clutch.PredictionState state = new Clutch.PredictionState();
            state.box = Clutch.mc.player.getShape();
            state.motionX = Clutch.mc.player.velocityX;
            state.motionY = Clutch.mc.player.velocityY;
            state.motionZ = Clutch.mc.player.velocityZ;
            state.posY = Clutch.mc.player.y;
            state.onGround = Clutch.mc.player.onGround;
            return state;
        }

        Vec3d getPos() {
            return new Vec3d(
                    (this.box.minX + this.box.maxX) / 2.0, this.box.minY, (this.box.minZ + this.box.maxZ) / 2.0);
        }

        void tick(boolean stopHorizontal) {
            if (stopHorizontal) {
                this.motionX = 0.0;
                this.motionZ = 0.0;
            }

            this.motionY -= 0.08;
            this.move(this.motionX, this.motionY, this.motionZ);
            this.motionY *= 0.98F;
            this.motionX *= 0.91;
            this.motionZ *= 0.91;
        }

        private void move(double x, double y, double z) {
            double originalX = x;
            double originalY = y;
            double originalZ = z;
            List<Box> collisions = Clutch.mc.world.getCollisions(Clutch.mc.player, this.box.expanded(x, y, z));

            for (Box collision : collisions) {
                y = collision.intersectY(this.box, y);
            }

            this.box = this.box.moved(0.0, y, 0.0);

            for (Box collision : collisions) {
                x = collision.intersectX(this.box, x);
            }

            this.box = this.box.moved(x, 0.0, 0.0);

            for (Box collision : collisions) {
                z = collision.intersectZ(this.box, z);
            }

            this.box = this.box.moved(0.0, 0.0, z);
            this.onGround = originalY != y && originalY < 0.0;
            this.posY = this.box.minY;
            if (originalX != x) {
                this.motionX = 0.0;
            }

            if (originalY != y) {
                this.motionY = 0.0;
            }

            if (originalZ != z) {
                this.motionZ = 0.0;
            }
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
}
