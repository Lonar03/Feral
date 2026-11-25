package keystrokesmod.module.impl.minigames;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import keystrokesmod.Feral;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CauldronBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.block.RailBlock;
import net.minecraft.block.SkullBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.StairsBlock.Half;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class SpeedBuilders extends Module {
    private SliderSetting placeDelay;
    private ButtonSetting antiMiss;
    private ButtonSetting autoPlace;
    private ButtonSetting autoSwap;
    private ButtonSetting hoverPlace;
    private ButtonSetting infoHud;
    private ButtonSetting renderBlocks;
    private ButtonSetting renderOnlyPlaceable;
    private ConcurrentHashMap<BlockPos, SpeedBuilders.BuildBlockInfo> buildInfo = new ConcurrentHashMap<>();
    private BlockPos platformCenter;
    private boolean listenForPacket;
    public List<BlockPos> PLATFORM_POSITIONS = Arrays.asList(
            new BlockPos(45, 71, -18),
            new BlockPos(-16, 71, 45),
            new BlockPos(18, 71, 45),
            new BlockPos(45, 71, 16),
            new BlockPos(-18, 71, -45),
            new BlockPos(-45, 71, -16),
            new BlockPos(-45, 71, 18),
            new BlockPos(16, 71, -45));
    private int HIGHLIGHT_COLOR = new Color(31, 255, 22, 44).getRGB();
    private int UNPLACEABLE_COLOR = new Color(184, 255, 183, 30).getRGB();
    private boolean doneCollecting;
    private double blockCount;
    private int lastPlaceTick = 0;
    private boolean eliminated;

    public SpeedBuilders() {
        super("Speed Builders", Module.category.minigames);
        this.registerSetting(new DescriptionSetting("Middle click to toggle auto."));
        this.registerSetting(this.placeDelay = new SliderSetting("Place delay", " tick", 0.5, 0.0, 10.0, 0.5));
        this.registerSetting(this.antiMiss = new ButtonSetting("Anti miss", false));
        this.registerSetting(this.autoPlace = new ButtonSetting("Auto place", false));
        this.registerSetting(this.autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(this.hoverPlace = new ButtonSetting("Hover place", true));
        this.registerSetting(this.infoHud = new ButtonSetting("Info HUD", true));
        this.registerSetting(this.renderBlocks = new ButtonSetting("Render blocks", true));
        this.registerSetting(this.renderOnlyPlaceable = new ButtonSetting("Render only placeable", false));
    }

    @Override
    public void onDisable() {
        this.lastPlaceTick = 0;
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        int gameStatus = this.getGameStatus();
        if (gameStatus != -1 && this.platformCenter != null) {
            if (gameStatus == 4) {
                this.doneCollecting = true;
            }

            if (gameStatus == 1 && !this.doneCollecting) {
                this.buildInfo = this.getBuildInfo(this.platformCenter);
                if (!this.buildInfo.isEmpty()) {
                    this.blockCount = this.buildInfo.size();
                }
            }

            if (gameStatus == 2) {
                this.doneCollecting = false;

                for (Entry<BlockPos, SpeedBuilders.BuildBlockInfo> entry : this.buildInfo.entrySet()) {
                    BlockState currentState = mc.world.getBlockState(entry.getKey());
                    BlockState requiredState = entry.getValue().requiredState;
                    if (currentState == null || requiredState == null) {
                        entry.getValue().isPlaced = false;
                    } else if (!currentState.equals(requiredState)
                            && (!(requiredState.getBlock() instanceof AbstractLeavesBlock)
                                    || !currentState.getBlock().equals(requiredState.getBlock()))) {
                        entry.getValue().isPlaced = false;
                    } else {
                        entry.getValue().isPlaced = true;
                    }
                }

                if (this.getLookInfo() != null) {
                    HitResult mop = this.getLookInfo();
                    if (mop.face != null) {
                        BlockPos targetPos = mop.getPos();
                        BlockPos facePos = targetPos.offset(mop.face);
                        SpeedBuilders.BuildBlockInfo info = this.buildInfo.get(facePos);
                        if (info != null && !info.isPlaced) {
                            if (this.autoSwap.isToggled()) {
                                int requiredMeta =
                                        info.requiredState.getBlock().getMetadataFromState(info.requiredState);
                                int slot = this.getSlot(info.requiredState.getBlock(), requiredMeta);
                                if (slot != -1 && slot != mc.player.inventory.selectedSlot) {
                                    mc.player.inventory.selectedSlot = slot;
                                }
                            }

                            if (this.hoverPlace.isToggled()
                                    && this.holdingSameBlock(info.requiredState)
                                    && this.correctPlaceState(
                                            info.requiredState,
                                            targetPos,
                                            mop.face,
                                            mop.facePos,
                                            mc.player.getDisplayItemInHand())) {
                                if (this.lastPlaceTick++ < this.placeDelay.getInput()) {
                                    return;
                                }

                                ((IAccessorMinecraft) mc).callRightClickMouse();
                                this.lastPlaceTick = 0;
                            }
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.buttonstate && Utils.nullCheck() && mc.screen == null) {
            if (e.button == 1 && this.antiMiss.isToggled() && this.getLookInfo() != null && this.getGameStatus() == 2) {
                HitResult mop = this.getLookInfo();
                if (mop.face != null) {
                    BlockPos targetPos = mop.getPos();
                    BlockPos facePos = targetPos.offset(mop.face);
                    SpeedBuilders.BuildBlockInfo info = this.buildInfo.get(facePos);
                    if (info == null
                            || !this.holdingSameBlock(info.requiredState)
                            || !this.correctPlaceState(
                                    info.requiredState,
                                    targetPos,
                                    mop.face,
                                    mop.facePos,
                                    mc.player.getDisplayItemInHand())) {
                        e.setCanceled(true);
                    }
                }
            } else if (e.button == 2) {
                if (this.autoSwap.isToggled()) {
                    this.autoSwap.disable();
                    this.hoverPlace.disable();
                } else {
                    this.autoSwap.enable();
                    this.hoverPlace.enable();
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent ev) {
        if (Utils.nullCheck() && this.getGameStatus() == 2 && this.renderBlocks.isToggled()) {
            for (Entry<BlockPos, SpeedBuilders.BuildBlockInfo> buildData : this.buildInfo.entrySet()) {
                SpeedBuilders.BuildBlockInfo info = buildData.getValue();
                if (!info.isPlaced && this.holdingSameBlock(info.requiredState)) {
                    BlockPos pos = buildData.getKey();
                    boolean useWhite = true;

                    for (Direction dir : Direction.values()) {
                        BlockPos neighborPos = pos.offset(dir);
                        if (BlockUtils.getBlock(neighborPos) != Blocks.AIR) {
                            useWhite = false;
                        }
                    }

                    if (!this.renderOnlyPlaceable.isToggled() || !useWhite) {
                        RenderUtils.renderBlockModel(
                                buildData.getValue().requiredState,
                                pos.getX(),
                                pos.getY(),
                                pos.getZ(),
                                useWhite ? this.UNPLACEABLE_COLOR : this.HIGHLIGHT_COLOR);
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck() && mc.screen == null) {
            int gameStatus = this.getGameStatus();
            if (Feral.DEBUG) {
                this.renderDebugMenu(gameStatus);
            }

            if (this.infoHud.isToggled()) {
                List<String> lines = new ArrayList<>();
                lines.add("§6Speed Builders");
                lines.add("§7Status: §b" + this.getGameStatusName(gameStatus));
                if (gameStatus == 2 && !this.eliminated) {
                    double placedCount = 0.0;

                    for (SpeedBuilders.BuildBlockInfo info : this.buildInfo.values()) {
                        if (info.isPlaced) {
                            placedCount++;
                        }
                    }

                    double percentage = 0.0;
                    if (this.buildInfo.isEmpty()) {
                        percentage = 100.0;
                        placedCount = this.blockCount;
                    } else if (this.blockCount > 0.0) {
                        percentage = placedCount / this.blockCount * 100.0;
                    }

                    lines.add("§7Progress: §b" + (int) placedCount + "§7/§b" + (int) this.blockCount + " "
                            + Math.round(percentage) + "%");
                }

                lines.add("§7Auto: " + (this.autoEnabled() ? "§aENABLED" : "§cDISABLED"));
                int padding = 4;
                int maxWidth = 0;

                for (String line : lines) {
                    int lineWidth = mc.textRenderer.getWidth(line);
                    if (lineWidth > maxWidth) {
                        maxWidth = lineWidth;
                    }
                }

                int lineHeight = mc.textRenderer.fontHeight;
                int lineSpacing = 3;
                int totalHeight = lines.size() * lineHeight + (lines.size() - 1) * lineSpacing + padding * 2;
                int totalWidth = maxWidth + padding * 2;
                float x = -5.0F;
                float y = 110.0F;
                RenderUtils.drawRoundedRectangle(
                        x,
                        y,
                        x + totalWidth + 7.0F,
                        y + totalHeight - 2.0F,
                        7.0F,
                        Utils.mergeAlpha(Color.black.getRGB(), 120));
                float textX = x + padding;
                float textY = y + padding;

                for (int i = 0; i < lines.size(); i++) {
                    mc.textRenderer.draw(
                            lines.get(i), (int) (textX + 5.0F), (int) (textY + i * (lineHeight + lineSpacing)), -1);
                }
            }
        }
    }

    private void renderDebugMenu(int gameStatus) {
        List<String> lines = Arrays.asList(
                "§6Debug",
                "§7Status: §b" + this.getGameStatusName(gameStatus) + " §8(" + gameStatus + ")",
                "§7Eliminated: " + (this.eliminated ? "§cYES" : "§aNO"));
        int padding = 5;
        int lineSpacing = 2;
        int maxWidth = 0;

        for (String line : lines) {
            maxWidth = Math.max(maxWidth, mc.textRenderer.getWidth(line));
        }

        int lineHeight = mc.textRenderer.fontHeight;
        int width = maxWidth + padding * 2;
        int height = lines.size() * lineHeight + (lines.size() - 1) * lineSpacing + padding * 2;
        int x = -5;
        int y = 14;
        RenderUtils.drawRoundedRectangle(
                x, y, x + width, y + height, 7.0F, Utils.mergeAlpha(Color.black.getRGB(), 120));

        for (int i = 0; i < lines.size(); i++) {
            mc.textRenderer.draw(lines.get(i), x + padding + 2, y + padding + i * (lineHeight + lineSpacing), -1, true);
        }
    }

    private String getGameStatusName(int gameStatus) {
        switch (gameStatus) {
            case -1:
                return "Disabled";
            case 0:
            default:
                return "Unknown";
            case 1:
            case 4:
                return "Showing";
            case 2:
                return "Building";
            case 3:
                return "Judging";
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (Utils.nullCheck() && e.entity != null) {
            if (e.entity == mc.player) {
                this.buildInfo.clear();
                this.platformCenter = null;
                this.listenForPacket = false;
                this.doneCollecting = false;
                this.eliminated = false;
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onChat(ForgeEvents.ClientChatReceivedEvent e) {
        if (e.type != 2 && Utils.nullCheck() && this.getGameStatus() != -1 && !this.listenForPacket) {
            String stripped = Utils.stripColor(e.message.getString());
            if (!stripped.isEmpty()) {
                if (stripped.contains("Perfectly recreate the build you are shown each")
                        || stripped.contains("The game starts in 1 second!")) {
                    this.listenForPacket = true;
                }

                if (stripped.startsWith(Utils.getServerName())
                        && stripped.contains(" got a perfect build in ")
                        && stripped.endsWith("s!")) {
                    this.buildInfo.clear();
                    this.doneCollecting = false;
                }

                if (stripped.startsWith("Player eliminated: " + Utils.getServerName()) && stripped.endsWith("%)")) {
                    this.eliminated = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (this.listenForPacket && Utils.nullCheck() && e.getPacket() instanceof PlayerMoveS2CPacket) {
            Vec3d setPos = new Vec3d(
                    ((PlayerMoveS2CPacket) e.getPacket()).getX(),
                    ((PlayerMoveS2CPacket) e.getPacket()).getY(),
                    ((PlayerMoveS2CPacket) e.getPacket()).getZ());
            if (this.platformCenter == null) {
                this.platformCenter = this.findCenter(setPos);
            }

            this.listenForPacket = false;
        }
    }

    public int getGameStatus() {
        List<String> sidebar = Utils.getSidebarLines();
        if (sidebar.isEmpty()) {
            return -1;
        }

        if (!Utils.stripColor(sidebar.get(0)).startsWith("BUILD BATTLE")) {
            return -1;
        }

        for (int i = 0; i < sidebar.size() - 1; i++) {
            String currentLine = Utils.stripColor(sidebar.get(i));
            String nextLine = Utils.stripColor(sidebar.get(i + 1));
            if (currentLine.startsWith("Round:")) {
                if (nextLine.startsWith("Starts In: 00:03")
                        && Utils.stripColor(sidebar.get(i + 3)).startsWith("Theme:")) {
                    return 4;
                }

                if (nextLine.startsWith("Starts In:")) {
                    return 1;
                }

                if (nextLine.startsWith("Time Left:")) {
                    return 2;
                }

                if (nextLine.startsWith("Judging:")) {
                    return 3;
                }
            }
        }

        return 0;
    }

    public BlockPos findCenter(Vec3d position) {
        BlockPos closestPos = null;
        double closestDistSq = Double.MAX_VALUE;
        double maxDistance = 30.0;
        double maxDistSq = maxDistance * maxDistance;

        for (BlockPos pos : this.PLATFORM_POSITIONS) {
            double dx = pos.getX() - position.x;
            double dy = pos.getY() - position.y;
            double dz = pos.getZ() - position.z;
            double distSq = Math.abs(dx * dx + dy * dy + dz * dz);
            if (distSq <= maxDistSq && distSq < closestDistSq) {
                closestDistSq = distSq;
                closestPos = pos;
            }
        }

        return closestPos;
    }

    public ConcurrentHashMap<BlockPos, SpeedBuilders.BuildBlockInfo> getBuildInfo(BlockPos centerPos) {
        ConcurrentHashMap<BlockPos, SpeedBuilders.BuildBlockInfo> blockInfo = new ConcurrentHashMap<>();
        int startX = centerPos.getX() - 3;
        int endX = centerPos.getX() + 3;
        int startZ = centerPos.getZ() - 3;
        int endZ = centerPos.getZ() + 3;
        int startY = centerPos.getY() + 1;
        int endY = startY + 30;

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                for (int y = startY; y <= endY; y++) {
                    BlockPos currentPos = new BlockPos(x, y, z);
                    BlockState state = mc.world.getBlockState(currentPos);
                    if (state.getBlock() != Blocks.AIR) {
                        blockInfo.put(currentPos, new SpeedBuilders.BuildBlockInfo(state));
                    }
                }
            }
        }

        return blockInfo;
    }

    public boolean autoEnabled() {
        return this.autoSwap.isToggled() && this.hoverPlace.isToggled();
    }

    public boolean holdingSameBlock(BlockState requiredState) {
        if (mc.player != null && requiredState != null) {
            ItemStack heldItem = mc.player.getDisplayItemInHand();
            if (heldItem == null) {
                return false;
            }

            Item item = heldItem.getItem();
            Block requiredBlock = requiredState.getBlock();
            if ((requiredBlock == Blocks.WATER || requiredBlock == Blocks.FLOWING_WATER)
                    && item == Items.WATER_BUCKET) {
                return true;
            }

            if (!(item instanceof BlockItem)) {
                return false;
            }

            Block heldBlock = ((BlockItem) item).getBlock();
            int heldMeta = heldItem.getDamage();
            int requiredMeta = requiredBlock.getMetadataFromState(requiredState);
            if (requiredBlock == Blocks.LEAVES || requiredBlock == Blocks.LEAVES2) {
                requiredMeta &= 3;
                heldMeta &= 3;
            }

            if (this.removeMeta(heldBlock)) {
                heldMeta = 0;
                requiredMeta = 0;
            }

            return heldBlock == requiredBlock && heldMeta == requiredMeta;
        } else {
            return false;
        }
    }

    public HitResult getLookInfo() {
        HitResult movingObjectPosition = mc.crosshairTarget;
        return movingObjectPosition != null
                        && movingObjectPosition.type == Type.BLOCK
                        && movingObjectPosition.getPos() != null
                ? mc.crosshairTarget
                : null;
    }

    private int getSlot(Block block, int meta) {
        if (this.removeMeta(block)) {
            meta = 0;
        }

        if (block == Blocks.LEAVES || block == Blocks.LEAVES2) {
            meta &= 3;
        }

        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = mc.player.inventory.items[i];
            if (itemStack != null && itemStack.getItem() instanceof BlockItem && itemStack.size > 0) {
                Block invBlock = ((BlockItem) itemStack.getItem()).getBlock();
                int invMeta = itemStack.getDamage();
                if (this.removeMeta(block)) {
                    invMeta = 0;
                }

                if (invBlock == Blocks.LEAVES || invBlock == Blocks.LEAVES2) {
                    invMeta &= 3;
                }

                if (invBlock == block && invMeta == meta) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean removeMeta(Block block) {
        return block instanceof StairsBlock
                || block instanceof DoublePlantBlock
                || block instanceof FlowerBlock
                || block instanceof SkullBlock
                || block instanceof LadderBlock
                || block instanceof PumpkinBlock
                || block instanceof CauldronBlock
                || block instanceof RailBlock
                || block instanceof AbstractRailBlock
                || block instanceof TripwireHookBlock
                || block instanceof TripwireBlock
                || block instanceof DispenserBlock
                || block instanceof DropperBlock
                || block instanceof HopperBlock
                || block instanceof TorchBlock
                || block instanceof ButtonBlock
                || block instanceof LeverBlock
                || block instanceof TrapdoorBlock
                || block instanceof SlabBlock;
    }

    private boolean correctPlaceState(
            BlockState requiredState, BlockPos blockPos, Direction enumFacing, Vec3d hitVec, ItemStack heldItem) {
        if (requiredState == null
                || blockPos == null
                || enumFacing == null
                || hitVec == null
                || heldItem == null
                || !(heldItem.getItem() instanceof BlockItem)) {
            return false;
        }

        if (!(requiredState.getBlock() instanceof AbstractLeavesBlock)
                && !(requiredState.getBlock() instanceof ButtonBlock)) {
            BlockItem itemBlock = (BlockItem) heldItem.getItem();
            Block block = itemBlock.getBlock();
            int meta = heldItem.getDamage();
            Vec3d relativeHitVec = hitVec.subtract(new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            BlockState simulatedState = block.getPlacementState(
                    mc.world,
                    blockPos,
                    enumFacing,
                    (float) relativeHitVec.x,
                    (float) relativeHitVec.y,
                    (float) relativeHitVec.z,
                    meta,
                    mc.player);
            if (simulatedState == null) {
                return false;
            }

            if (simulatedState.getBlock() != requiredState.getBlock()) {
                return false;
            }

            int simulatedMeta = simulatedState.getBlock().getMetadataFromState(simulatedState);
            int requiredMeta = requiredState.getBlock().getMetadataFromState(requiredState);
            if (simulatedMeta != requiredMeta) {
                return false;
            }

            if (simulatedState.values().containsKey(HorizontalFacingBlock.FACING)
                    && requiredState.values().containsKey(HorizontalFacingBlock.FACING)) {
                Direction simulatedFacing = (Direction) simulatedState.get(HorizontalFacingBlock.FACING);
                Direction requiredFacing = (Direction) requiredState.get(HorizontalFacingBlock.FACING);
                if (simulatedFacing != requiredFacing) {
                    return false;
                }
            }

            if (simulatedState.getBlock() instanceof StairsBlock && requiredState.getBlock() instanceof StairsBlock) {
                Direction simulatedFacing = (Direction) simulatedState.get(StairsBlock.FACING);
                Direction requiredFacing = (Direction) requiredState.get(StairsBlock.FACING);
                Half simulatedHalf = (Half) simulatedState.get(StairsBlock.HALF);
                Half requiredHalf = (Half) requiredState.get(StairsBlock.HALF);
                if (simulatedFacing != requiredFacing || simulatedHalf != requiredHalf) {
                    return false;
                }
            }

            return true;
        } else {
            return true;
        }
    }

    class BuildBlockInfo {
        public BlockState requiredState;
        public boolean isPlaced;

        public BuildBlockInfo(BlockState state) {
            this.requiredState = state;
            this.isPlaced = false;
        }
    }
}
