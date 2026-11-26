package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.AirBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.StairsBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.DyeColor;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Mouse;

public class WoolWars extends Module {
    public SliderSetting breakSpeed;
    public SliderSetting range;
    public SliderSetting breakDelay;
    public SliderSetting placeDelay;
    public ButtonSetting onlyMiddleClick;
    public ButtonSetting onlyVisible;
    private final int MIDDLE_POSITION_COLOR = new Color(255, 153, 204).getRGB();
    private final int MINING_COLOR = new Color(200, 100, 255).getRGB();
    private final int PLACE_COLOR = new Color(150, 70, 255).getRGB();
    private BlockPos middlePos;
    private BlockPos miningPos;
    private HitResult placeMop;
    private float curBlockDamageMP;
    private int delay;
    private int swapBack = -1;
    private double lastRange;
    private double rangeSq;
    private float placingYaw;
    private float placingPitch;
    private boolean fakeSwing;

    public WoolWars() {
        super("WoolWars", Module.category.minigames, 0);
        this.registerSetting(new DescriptionSetting("Nukes and places at control point."));
        this.registerSetting(this.breakSpeed = new SliderSetting("Break speed", 0.2, 0.0, 0.8, 0.05));
        this.registerSetting(this.breakDelay = new SliderSetting("Delay after breaking", 3.0, 1.0, 10.0, 1.0));
        this.registerSetting(this.placeDelay = new SliderSetting("Delay after placing", 1.0, 1.0, 10.0, 1.0));
        this.registerSetting(this.range = new SliderSetting("Range", 5.0, 1.0, 8.0, 0.5));
        this.registerSetting(this.onlyVisible = new ButtonSetting("Only visible", true));
        this.registerSetting(this.onlyMiddleClick = new ButtonSetting("Only while middle clicking", true));
    }

    @Override
    public void guiUpdate() {
        if (this.lastRange != this.range.getInput()) {
            this.lastRange = this.range.getInput();
            this.rangeSq = Math.pow(this.lastRange + 2.0, 2.0);
        }
    }

    @Override
    public void onDisable() {
        this.swapBack();
        this.reset();
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent event) {
        if (!this.isWoolWars()) {
            this.reset();
        } else {
            if (this.middlePos == null) {
                this.middlePos = this.getMiddlePos();
            } else if (!mc.player.abilities.canFly
                    && mc.player.getSquaredDistanceTo(this.middlePos) < this.rangeSq
                    && this.isActiveRound()
                    && (!this.onlyMiddleClick.isToggled() || Mouse.isButtonDown(2))) {
                if (this.swapBack == -1) {
                    this.swapBack = mc.player.inventory.selectedSlot;
                }

                if (this.delay > 0 && --this.delay > 0) {
                    if (this.fakeSwing) {
                        mc.player.swingArm();
                    }

                    return;
                }

                if (this.placeMop != null) {
                    return;
                }

                if (this.miningPos == null) {
                    List<BlockPos> posList = this.getPossiblePos(this.middlePos, true);
                    if (!posList.isEmpty()) {
                        BlockPos closestPos = this.getClosestPos(posList, true);
                        if (closestPos != null) {
                            int blockSlot = this.getBlockSlot();
                            if (blockSlot == -1) {
                                return;
                            }

                            Utils.switchSlot(blockSlot, true);

                            for (int i = 0; i < 360; i += 10) {
                                float yaw = (float) (mc.player.yaw + i + this.randomRotationOffset());

                                for (int j = 20; j < 90; j += 5) {
                                    float pitch = RotationUtils.clampPitch((float) (j + this.randomRotationOffset()));
                                    HitResult mop = Utils.getTarget(this.lastRange, yaw, pitch);
                                    if (mop != null
                                            && mop.type == Type.BLOCK
                                            && BlockUtils.isBlockPosEqual(BlockUtils.offsetPos(mop), closestPos)) {
                                        this.placeMop = mop;
                                        this.placingYaw = yaw;
                                        this.placingPitch = pitch;
                                        return;
                                    }
                                }
                            }

                            return;
                        }
                    }

                    posList = this.getPossiblePos(this.middlePos, false);
                    if (posList.isEmpty()) {
                        this.middlePos = null;
                        this.swapBack();
                        return;
                    }

                    BlockPos closestPos = this.getClosestPos(posList, false);
                    if (closestPos == null) {
                        return;
                    }

                    this.miningPos = closestPos;
                    this.switchToSlot(Utils.getTool(BlockUtils.getBlock(closestPos)));
                    this.miningPos = closestPos;
                    mc.player.swingArm();
                    startBreak(this.miningPos);
                } else if (!Utils.isPossibleToReach(this.miningPos, this.lastRange)) {
                    abortBreak(this.miningPos);
                    this.miningPos = null;
                    this.curBlockDamageMP = this.delay = 0;
                    return;
                }

                this.curBlockDamageMP = this.curBlockDamageMP
                        + BlockUtils.getBlockHardness(
                                BlockUtils.getBlock(this.miningPos), mc.player.getDisplayItemInHand(), false, false);
                if (this.curBlockDamageMP < this.breakSpeed.getInput()) {
                    this.curBlockDamageMP = (float) this.breakSpeed.getInput();
                }

                if (this.curBlockDamageMP >= 1.0F) {
                    stopBreak(this.miningPos);
                    mc.interactionManager.finishMiningBlock(this.miningPos, Direction.UP);
                    this.miningPos = null;
                    this.curBlockDamageMP = 0.0F;
                    this.delay = (int) this.breakDelay.getInput();
                    this.fakeSwing = true;
                }

                mc.world.updateBlockMiningProgress(
                        mc.player.getNetworkId(), this.miningPos, (int) (this.curBlockDamageMP * 10.0F) - 1);
                mc.player.swingArm();
            } else if (this.miningPos != null) {
                abortBreak(this.miningPos);
                this.miningPos = null;
                this.curBlockDamageMP = this.delay = 0;
                this.swapBack();
            } else if (this.swapBack != -1) {
                this.swapBack();
            }
        }
    }

    public double randomRotationOffset() {
        return Math.random() - 0.5;
    }

    public boolean switchToSlot(int slot) {
        if (slot == -1) {
            return false;
        }

        mc.player.inventory.selectedSlot = slot;
        return true;
    }

    private BlockPos getMiddlePos() {
        BlockPos middlePos = null;
        int y;
        int startY = y = (int) Math.floor(mc.player.y + 20.0);

        while (y > -1) {
            BlockPos pos = BlockUtils.pos(0.0, y, 0.0);
            if (BlockUtils.getBlock(pos.add(0, 0, 2)) instanceof StairsBlock
                    || isControlPointBlock(BlockUtils.getBlock(pos))) {
                middlePos = pos;
                break;
            }

            y--;
        }

        if (middlePos == null) {
            for (int var5 = startY; var5 > -1; var5--) {
                BlockPos pos = BlockUtils.pos(0.0, var5, 6.0);
                if (BlockUtils.getBlock(pos.add(0, 0, 2)) instanceof StairsBlock
                        || isControlPointBlock(BlockUtils.getBlock(pos))) {
                    middlePos = pos;
                    break;
                }
            }
        }

        return middlePos;
    }

    private List<BlockPos> getPossiblePos(BlockPos middlePos, boolean airOnly) {
        List<BlockPos> posList = new ArrayList<>();
        DyeColor teamColor = null;
        boolean teamColorResolved = false;

        for (int zOffset = -1; zOffset <= 1; zOffset++) {
            for (int xOffset = -1; xOffset <= 1; xOffset++) {
                BlockPos pos = new BlockPos(middlePos.getX() + xOffset, middlePos.getY(), middlePos.getZ() + zOffset);
                Block block = BlockUtils.getBlock(pos);
                boolean matches;
                if (airOnly) {
                    matches = block instanceof AirBlock;
                } else if (block == Blocks.WOOL) {
                    if (!teamColorResolved) {
                        teamColor = this.getTeamWoolColor();
                        teamColorResolved = true;
                    }
                    matches = BlockUtils.getWoolColor(BlockUtils.getBlockState(pos)) != teamColor;
                } else {
                    matches = isControlPointBlock(block);
                }
                if (matches) {
                    posList.add(pos);
                }
            }
        }

        return posList;
    }

    private BlockPos getClosestPos(List<BlockPos> posList, boolean down) {
        BlockPos closestPos = null;
        double leastDistSq = this.rangeSq + 1.0;

        for (BlockPos pos : posList) {
            if (Utils.isPossibleToReach(down ? pos.down() : pos, this.lastRange)
                    && (!this.onlyVisible.isToggled() || BlockUtils.canBlockBeSeen(pos))) {
                double distSq = mc.player.getSquaredDistanceTo(pos);
                if (!(distSq >= leastDistSq)) {
                    leastDistSq = distSq;
                    closestPos = pos;
                }
            }
        }

        return closestPos;
    }

    private static boolean isControlPointBlock(Block block) {
        return block == Blocks.WOOL || block == Blocks.SNOW || block == Blocks.QUARTZ_BLOCK;
    }

    private DyeColor getTeamWoolColor() {
        int slot = Utils.findHotbarSlot(stack -> stack != null
                && stack.getItem() instanceof BlockItem
                && ((BlockItem) stack.getItem()).getBlock() == Blocks.WOOL);
        return slot == -1
                ? null
                : DyeColor.byId(mc.player.inventory.getItem(slot).getMetadata());
    }

    private boolean isActiveRound() {
        for (String line : Utils.getSidebarLines()) {
            String strip = Utils.stripString(line);
            if (strip.contains("State: Active Round")) {
                return true;
            }
        }

        return false;
    }

    public int getBlockSlot() {
        return Utils.findHotbarSlot(stack -> stack != null
                && stack.getItem() instanceof BlockItem
                && BlockUtils.isNormalBlock(((BlockItem) stack.getItem()).getBlock()));
    }

    @ForgeEvents.SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (ModuleManager.bedAura == null || !ModuleManager.bedAura.shouldOverrideMouseOver()) {
            if (this.placeMop != null) {
                if (this.placingPitch > 90.0F) {
                    if (mc.interactionManager.useBlock(
                            mc.player,
                            mc.world,
                            mc.player.getDisplayItemInHand(),
                            this.placeMop.getPos(),
                            this.placeMop.face,
                            this.placeMop.facePos)) {
                        mc.player.swingArm();
                        mc.getItemInHandRenderer().onBlockUsed();
                        this.delay = (int) this.placeDelay.getInput();
                        this.fakeSwing = false;
                    }

                    this.placeMop = null;
                } else {
                    this.placingPitch += 300.0F;
                }

                e.setYaw(this.placingYaw);
                e.setPitch(this.placingPitch - 300.0F);
            } else {
                if (this.miningPos != null) {
                    float[] rotations = RotationUtils.getRotationsToBlock(
                            this.miningPos, Direction.UP, RotationUtils.prevRenderYaw, RotationUtils.prevRenderPitch);
                    if (rotations != null) {
                        e.setYaw(rotations[0]);
                        e.setPitch(rotations[1]);
                    }
                }

                if (this.delay > 0 && (!this.onlyMiddleClick.isToggled() || Mouse.isButtonDown(2))) {
                    List<BlockPos> posList = this.getPossiblePos(this.middlePos, true);
                    BlockPos closestPos = null;
                    if (!posList.isEmpty()) {
                        closestPos = this.getClosestPos(posList, true);
                    }

                    if (closestPos == null) {
                        posList = this.getPossiblePos(this.middlePos, false);
                        closestPos = this.getClosestPos(posList, false);
                    }

                    if (closestPos != null) {
                        float[] rotations = RotationUtils.getRotationsToBlock(
                                closestPos, Direction.UP, RotationUtils.prevRenderYaw, RotationUtils.prevRenderPitch);
                        if (rotations != null) {
                            e.setYaw(rotations[0]);
                            e.setPitch(rotations[1]);
                        }
                    }
                }
            }
        }
    }

    private boolean isWoolWars() {
        if (Utils.nullCheck() && Utils.isHypixel()) {
            Scoreboard scoreboard = mc.world.getScoreboard();
            if (scoreboard == null) {
                return false;
            }

            ScoreboardObjective objective = scoreboard.getDisplayObjective(1);
            return objective != null
                    && Utils.stripString(objective.getDisplayName()).contains("WOOL WARS");
        } else {
            return false;
        }
    }

    private void swapBack() {
        if (this.swapBack != -1) {
            mc.player.inventory.selectedSlot = this.swapBack;
            this.swapBack = -1;
        }
    }

    private void reset() {
        this.middlePos = this.miningPos = null;
        this.placeMop = null;
        this.curBlockDamageMP = this.delay = 0;
        this.swapBack = -1;
    }

    @ForgeEvents.SubscribeEvent
    public void onRender(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (this.middlePos != null) {
                for (BlockPos pos : this.getPossiblePos(this.middlePos, false)) {
                    RenderUtils.renderBlock(pos, this.MIDDLE_POSITION_COLOR, true, false);
                }
            }

            if (this.miningPos != null) {
                RenderUtils.renderBlock(this.miningPos, this.MINING_COLOR, false, true);
            } else if (this.placeMop != null) {
                RenderUtils.renderBlock(BlockUtils.offsetPos(this.placeMop), this.PLACE_COLOR, false, true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.button == 0) {
            if (e.buttonstate && (this.miningPos != null || this.placeMop != null)) {
                e.setCanceled(true);
            }
        } else if (e.button == 1 && (this.miningPos != null || this.placeMop != null)) {
            e.setCanceled(true);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.reset();
        }
    }

    public static void startBreak(BlockPos pos) {
        mc.player.networkHandler.sendPacket(
                new PlayerHandActionC2SPacket(Action.START_DESTROY_BLOCK, pos, Direction.UP));
    }

    public static void stopBreak(BlockPos pos) {
        mc.player.networkHandler.sendPacket(
                new PlayerHandActionC2SPacket(Action.STOP_DESTROY_BLOCK, pos, Direction.UP));
    }

    public static void abortBreak(BlockPos pos) {
        mc.player.networkHandler.sendPacket(
                new PlayerHandActionC2SPacket(Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
    }
}
