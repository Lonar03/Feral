package keystrokesmod.module.impl.player;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.SimulatedPlayer;
import keystrokesmod.utility.Utils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class BridgeAssist extends Module {
    private static final Direction[] SIDES =
            new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
    private final SliderSetting edgeOffset;
    private final SliderSetting unsneakDelay;
    private final SliderSetting sneakOnJump;
    private final SliderSetting minimumPitch;
    private final ButtonSetting avoidDoubleSneaking;
    private final ButtonSetting onlyWhileSneaking;
    private final ButtonSetting blocksOnly;
    private final ButtonSetting disableOnForward;
    private final ButtonSetting onlyOnBackward;
    private final ButtonSetting prePlace;
    private final ButtonSetting disableHotbarScrolling;
    private boolean sneakingFromModule;
    private boolean placed;
    private boolean forceRelease;
    private boolean cancelRightClickThisTick;
    private int rightClickGuard;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    public BridgeAssist() {
        super("Bridge Assist", Module.category.player);
        this.registerSetting(this.edgeOffset = new SliderSetting("Edge offset", " block", 0.0, 0.0, 0.3, 0.01));
        this.registerSetting(this.unsneakDelay = new SliderSetting("Unsneak delay", "ms", 50.0, 50.0, 300.0, 5.0));
        this.registerSetting(this.sneakOnJump = new SliderSetting("Sneak on jump", "ms", 0.0, 0.0, 500.0, 5.0));
        this.registerSetting(
                this.minimumPitch = new SliderSetting("Minimum pitch", "", "§cDisabled", 65.0, 19.0, 85.0, 1.0));
        this.registerSetting(this.avoidDoubleSneaking = new ButtonSetting("Avoid double sneaking", false));
        this.registerSetting(this.blocksOnly = new ButtonSetting("Blocks only", false));
        this.registerSetting(this.disableHotbarScrolling = new ButtonSetting("Disable hotbar scrolling", false));
        this.registerSetting(this.disableOnForward = new ButtonSetting("Disable on forward", false));
        this.registerSetting(this.onlyOnBackward = new ButtonSetting("Only on backward", false));
        this.registerSetting(this.onlyWhileSneaking = new ButtonSetting("Only while sneaking", false));
        this.registerSetting(this.prePlace = new ButtonSetting("Pre place", false));
        this.closetModule = true;
        this.liteModule = true;
    }

    @Override
    public String getInfo() {
        double offset = this.edgeOffset.getInput();
        return offset == Math.rint(offset) ? Integer.toString((int) offset) : Double.toString(Utils.round(offset, 2));
    }

    @Override
    public void onDisable() {
        this.sneakingFromModule = false;
        this.cancelRightClickThisTick = false;
        this.rightClickGuard = 0;
        this.resetUnsneak();
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        this.cancelRightClickThisTick = false;
        if (!this.avoidDoubleSneaking.isToggled()) {
            this.rightClickGuard = 0;
        } else if (mc.focused && this.rightClickGuard > 0) {
            this.rightClickGuard--;
        }

        if (Utils.nullCheck() && mc.screen == null && !mc.player.abilities.flying) {
            boolean manualSneak = this.isManualSneak();
            boolean requireSneak = this.onlyWhileSneaking.isToggled();
            if (manualSneak && !requireSneak) {
                this.resetUnsneak();
            } else if (!requireSneak || manualSneak && (e.getForward() != 0.0F || e.getStrafe() != 0.0F)) {
                if (this.disableOnForward.isToggled() && e.getForward() > 0.0F) {
                    this.clearSneak(e);
                } else if (this.onlyOnBackward.isToggled() && !mc.options.backKey.isPressed()) {
                    this.clearSneak(e);
                } else if (this.minimumPitch.getInput() > this.minimumPitch.getMin()
                        && mc.player.pitch < this.minimumPitch.getInput()) {
                    this.clearSneak(e);
                } else {
                    if (this.blocksOnly.isToggled()) {
                        ItemStack held = mc.player.getDisplayItemInHand();
                        if (held == null || !(held.getItem() instanceof BlockItem)) {
                            this.clearSneak(e);
                            return;
                        }
                    }

                    if (!e.isJump()
                            || !mc.player.onGround
                            || e.getForward() == 0.0F && e.getStrafe() == 0.0F
                            || !(this.sneakOnJump.getInput() > 0.0)
                            || requireSneak && !this.forceRelease) {
                        SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.player.input);
                        sim.movementInput.sneaking = false;
                        sim.tick();
                        double offset = this.computeEdgeOffset(sim.getEntityBoundingBox());
                        if (!Double.isNaN(offset)) {
                            if (offset > this.edgeOffset.getInput()) {
                                this.pressSneak(e, true);
                            } else if (this.sneakingFromModule) {
                                this.tryReleaseSneak(e, true);
                            }
                        } else {
                            if (e.isJump()
                                    && (this.sneakOnJump.getInput() <= 0.0
                                            || e.getForward() == 0.0F && e.getStrafe() == 0.0F)) {
                                if (this.sneakingFromModule) {
                                    this.tryReleaseSneak(e, true);
                                }
                            } else if (mc.player.onGround) {
                                this.pressSneak(e, true);
                            } else if (this.sneakingFromModule) {
                                this.tryReleaseSneak(e, true);
                            }
                        }
                    } else {
                        this.sneakJumpStartTick = mc.player.ticks;
                        double raw = this.sneakOnJump.getInput() / 50.0;
                        int base = (int) raw;
                        this.sneakJumpDelayTicks = base + (Math.random() < raw - base ? 1 : 0);
                        this.pressSneak(e, true);
                    }
                }
            } else {
                if (!manualSneak) {
                    this.resetUnsneak();
                }

                this.repressSneak(e);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRightClickMouse(RightClickMouseEvent e) {
        if (this.avoidDoubleSneaking.isToggled() && mc.focused && this.isAssistActive()) {
            ItemStack held = mc.player.getDisplayItemInHand();
            if (held != null && held.getItem() instanceof BlockItem && this.rightClickGuard != 20) {
                if (this.cancelRightClickThisTick && this.shouldCancelRightClick()) {
                    e.setCanceled(true);
                } else {
                    this.rightClickGuard = 20;
                }
            }
        }
    }

    private boolean shouldCancelRightClick() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null
                || hit.type != Type.BLOCK
                || hit.face == null
                || hit.face == Direction.UP
                || hit.face == Direction.DOWN
                || hit.facePos == null
                || hit.getPos() == null
                || !mc.player.onGround) {
            return false;
        }

        if (mc.options.backKey.isPressed() && !mc.options.leftKey.isPressed() && !mc.options.rightKey.isPressed()) {
            float yawSector = Math.abs((float) (mc.player.yaw % 90.0));
            if (!(yawSector < 30.0F) && !(yawSector > 60.0F)) {
                Vec3d playerPosition = new Vec3d(mc.player.x, mc.player.y, mc.player.z);
                Vec3d hitVec = hit.facePos;
                double dx = hitVec.x - playerPosition.x;
                double dy = hitVec.y - playerPosition.y;
                double dz = hitVec.z - playerPosition.z;
                if (!(Math.abs(dx) > 1.0) && !(dy < -1.0) && !(dy > 0.0) && !(Math.abs(dz) > 1.0)) {
                    BlockPos blockPos = hit.getPos();
                    double margin = playerPosition.z > blockPos.getZ() && playerPosition.z < blockPos.getZ() + 1.0
                            ? 0.25
                            : 0.15;
                    double fractionX = Math.abs(hitVec.x % 1.0);
                    double fractionZ = Math.abs(hitVec.z % 1.0);
                    return fractionX >= margin && fractionX <= 1.0 - margin
                            || fractionZ >= margin && fractionZ <= 1.0 - margin;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (e.getPacket() instanceof PlayerUseC2SPacket) {
            PlayerUseC2SPacket c08 = (PlayerUseC2SPacket) e.getPacket();
            if (c08.getFace() != 255 && this.sneakingFromModule && this.onlyWhileSneaking.isToggled()) {
                this.placed = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSlotScroll(PreSlotScrollEvent e) {
        if (this.disableHotbarScrolling.isToggled() && this.isAssistActive()) {
            e.setCanceled(true);
        }
    }

    private boolean isAssistActive() {
        if (this.isEnabled() && Utils.nullCheck() && mc.screen == null && !mc.player.abilities.flying) {
            float forward = mc.player.input.movementForward;
            float strafe = mc.player.input.movementSideways;
            boolean manualSneak = this.isManualSneak();
            if (manualSneak && !this.onlyWhileSneaking.isToggled()) {
                return false;
            }

            if (!this.onlyWhileSneaking.isToggled() || manualSneak && (forward != 0.0F || strafe != 0.0F)) {
                if (this.disableOnForward.isToggled() && forward > 0.0F) {
                    return false;
                }

                if (this.onlyOnBackward.isToggled() && !mc.options.backKey.isPressed()) {
                    return false;
                }

                if (this.minimumPitch.getInput() > this.minimumPitch.getMin()
                        && mc.player.pitch < this.minimumPitch.getInput()) {
                    return false;
                }

                if (this.blocksOnly.isToggled()) {
                    ItemStack held = mc.player.getDisplayItemInHand();
                    if (held == null || !(held.getItem() instanceof BlockItem)) {
                        return false;
                    }
                }

                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (this.prePlace.isToggled()) {
            if (Utils.nullCheck() && mc.screen == null && !mc.player.abilities.flying) {
                if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
                    ItemStack held = mc.player.getDisplayItemInHand();
                    if (held != null && held.getItem() instanceof BlockItem) {
                        if (!(this.minimumPitch.getInput() > this.minimumPitch.getMin())
                                || !(mc.player.pitch < this.minimumPitch.getInput())) {
                            if (!this.disableOnForward.isToggled() || !(mc.player.input.movementForward > 0.0F)) {
                                if (!this.onlyOnBackward.isToggled() || mc.options.backKey.isPressed()) {
                                    float basePitch = e.pitch != null ? e.pitch : RotationUtils.serverRotations[1];
                                    double reach = mc.interactionManager.getReach();
                                    BridgeAssist.TargetResult target = this.findTarget(basePitch, reach);
                                    if (target != null) {
                                        float baseYaw = e.yaw != null ? e.yaw : RotationUtils.serverRotations[0];
                                        float[] sm = RotationUtils.smoothRotation(
                                                baseYaw, basePitch, target.yaw, target.pitch, 15, 20.0F);
                                        e.setYaw(sm[0]);
                                        e.setPitch(sm[1]);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void pressSneak(PrePlayerInputEvent e, boolean resetDelay) {
        e.setSneak(true);
        this.sneakingFromModule = true;
        this.cancelRightClickThisTick = this.avoidDoubleSneaking.isToggled();
        if (resetDelay) {
            this.unsneakStartTick = -1;
        }

        this.repressSneak(e);
    }

    private void tryReleaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        int existed = mc.player.ticks;
        if (this.unsneakStartTick == -1 && this.sneakJumpStartTick == -1) {
            this.unsneakStartTick = existed;
            double raw = (this.unsneakDelay.getInput() - 50.0) / 50.0;
            int base = (int) raw;
            this.unsneakDelayTicks = base + (Math.random() < raw - base ? 1 : 0);
        }

        if (this.sneakJumpStartTick != -1 && existed - this.sneakJumpStartTick < this.sneakJumpDelayTicks) {
            this.pressSneak(e, false);
        } else if (this.unsneakStartTick != -1 && existed - this.unsneakStartTick < this.unsneakDelayTicks) {
            this.pressSneak(e, false);
        } else {
            this.releaseSneak(e, resetDelay);
        }
    }

    private void releaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        if (!this.onlyWhileSneaking.isToggled()) {
            e.setSneak(false);
        } else if (!this.sneakingFromModule || !this.isManualSneak() || !this.placed && mc.player.onGround) {
            if (this.forceRelease) {
                e.setSneak(false);
            }
        } else {
            KeyBinding.set(mc.options.sneakKey.getKeyCode(), false);
            e.setSneak(false);
            this.forceRelease = true;
        }

        this.sneakingFromModule = false;
        this.placed = false;
        if (resetDelay) {
            this.resetUnsneak();
        }
    }

    private void repressSneak(PrePlayerInputEvent e) {
        if (this.forceRelease && this.isManualSneak()) {
            KeyBinding.set(mc.options.sneakKey.getKeyCode(), true);
            e.setSneak(true);
        }

        this.forceRelease = false;
    }

    private void clearSneak(PrePlayerInputEvent e) {
        this.sneakingFromModule = false;
        this.resetUnsneak();
        if (this.onlyWhileSneaking.isToggled()) {
            this.repressSneak(e);
        }
    }

    private void resetUnsneak() {
        this.unsneakStartTick = -1;
        this.sneakJumpStartTick = -1;
        this.sneakJumpDelayTicks = -1;
        this.unsneakDelayTicks = -1;
    }

    private boolean isManualSneak() {
        return Utils.isBindDown(mc.options.sneakKey);
    }

    private double computeEdgeOffset(Box simBox) {
        Box groundCheck = new Box(simBox.minX, simBox.minY - 0.51, simBox.minZ, simBox.maxX, simBox.minY, simBox.maxZ);
        List<Box> groundBoxes = mc.world.getCollisions(mc.player, groundCheck);
        if (groundBoxes.isEmpty()) {
            return Double.NaN;
        }

        double feetX = (simBox.minX + simBox.maxX) / 2.0;
        double feetZ = (simBox.minZ + simBox.maxZ) / 2.0;
        double minDist = Double.MAX_VALUE;

        for (Box box : groundBoxes) {
            double closestX = Math.max(box.minX, Math.min(feetX, box.maxX));
            double closestZ = Math.max(box.minZ, Math.min(feetZ, box.maxZ));
            double dx = Math.abs(feetX - closestX);
            double dz = Math.abs(feetZ - closestZ);
            double dist = Math.max(dx, dz);
            minDist = Math.min(minDist, dist);
        }

        return minDist;
    }

    private BridgeAssist.TargetResult findTarget(float currentPitch, double reach) {
        float yaw = mc.player.yaw;
        Box bbox = mc.player.getShape();
        int standY = MathHelper.floor(bbox.minY) - 1;
        int minX = MathHelper.floor(bbox.minX);
        int maxX = MathHelper.floor(bbox.maxX);
        int minZ = MathHelper.floor(bbox.minZ);
        int maxZ = MathHelper.floor(bbox.maxZ);
        ArrayList<BridgeAssist.FaceTarget> targets = new ArrayList<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos standBlock = new BlockPos(x, standY, z);
                if (!BlockUtils.replaceable(standBlock)) {
                    for (Direction face : SIDES) {
                        BlockPos placed = standBlock.offset(face);
                        if (BlockUtils.replaceable(placed)) {
                            targets.add(new BridgeAssist.FaceTarget(standBlock, face));
                        }
                    }
                }
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        float bestDelta = Float.MAX_VALUE;
        float bestPitch = Float.NaN;
        BlockPos bestSupport = null;
        Direction bestFace = null;
        float randScale = 0.2F;
        float pitch = 60.0F;

        while (pitch <= 90.0F) {
            float step = 1.0F + (float) (Math.random() * 2.0 - 1.0) * (0.3F + randScale * 0.4F);
            if (step < 0.4F) {
                step = 0.4F;
            }

            if (step > 1.8F) {
                step = 1.8F;
            }

            pitch += step;
            float samplePitch = Math.min(pitch, 90.0F);
            HitResult mop = RotationUtils.rayCastBlock(reach, yaw, samplePitch);
            if (mop != null) {
                Direction hitFace = mop.face;
                if (hitFace != Direction.UP && hitFace != Direction.DOWN) {
                    BlockPos hitBlock = mop.getPos();
                    Iterator var23 = targets.iterator();

                    while (true) {
                        if (var23.hasNext()) {
                            BridgeAssist.FaceTarget t = (BridgeAssist.FaceTarget) var23.next();
                            if (!hitBlock.equals(t.block) || hitFace != t.face) {
                                continue;
                            }

                            float delta = Math.abs(samplePitch - currentPitch);
                            if (delta < bestDelta) {
                                bestDelta = delta;
                                bestPitch = samplePitch;
                                bestSupport = t.block;
                                bestFace = t.face;
                            }
                        }

                        if (pitch >= 90.0F) {
                            return bestSupport != null && bestFace != null && !Float.isNaN(bestPitch)
                                    ? new BridgeAssist.TargetResult(yaw, bestPitch, bestSupport, bestFace)
                                    : null;
                        }
                        break;
                    }
                }
            }
        }

        return bestSupport != null && bestFace != null && !Float.isNaN(bestPitch)
                ? new BridgeAssist.TargetResult(yaw, bestPitch, bestSupport, bestFace)
                : null;
    }

    private static class FaceTarget {
        final BlockPos block;
        final Direction face;

        FaceTarget(BlockPos block, Direction face) {
            this.block = block;
            this.face = face;
        }
    }

    private static class TargetResult {
        final float yaw;
        final float pitch;
        final BlockPos support;
        final Direction face;

        TargetResult(float yaw, float pitch, BlockPos support, Direction face) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
        }
    }
}
