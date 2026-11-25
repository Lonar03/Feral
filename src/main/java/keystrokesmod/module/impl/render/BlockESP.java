package keystrokesmod.module.impl.render;

import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Set;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.BlockListSetting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.SharedBlockHighlightCache;
import keystrokesmod.utility.Utils;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class BlockESP extends Module {
    private final BlockListSetting blockList;
    private final ButtonSetting outline;
    private final ButtonSetting shade;
    private final SliderSetting range;
    private final SliderSetting maxRenders;
    private final SliderSetting scanSpeed;
    private int prevListHash;

    public BlockESP() {
        super("BlockESP", Module.category.render);
        this.registerSetting(this.blockList = new BlockListSetting("Blocks"));
        this.registerSetting(this.outline = new ButtonSetting("Outline", true));
        this.registerSetting(this.shade = new ButtonSetting("Shade", false));
        this.registerSetting(this.range = new SliderSetting("Range", 64.0, 8.0, 256.0, 8.0));
        this.registerSetting(this.maxRenders = new SliderSetting("Max renders", 2048.0, 128.0, 8192.0, 128.0));
        this.registerSetting(this.scanSpeed = new SliderSetting("Scan speed", 8.0, 1.0, 32.0, 1.0));
    }

    @Override
    public void onEnable() {
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        cache.attachBlockList(this.blockList);
        this.prevListHash = this.blockList.getBlocks().hashCode();
        if (!this.blockList.getBlocks().isEmpty()) {
            cache.enqueueLoadedChunks();
        }
    }

    @Override
    public void onDisable() {
        SharedBlockHighlightCache.get().detachBlockList();
    }

    @Override
    public void onUpdate() {
        if (Utils.nullCheck()) {
            int h = this.blockList.getBlocks().hashCode();
            if (h != this.prevListHash) {
                this.prevListHash = h;
                SharedBlockHighlightCache.get().onBlockListSettingsChanged();
            }
        }
    }

    public int getScanSpeedBudget() {
        return this.isEnabled() && !this.blockList.getBlocks().isEmpty() ? (int) this.scanSpeed.getInput() : 0;
    }

    @Override
    public String getInfo() {
        int total = SharedBlockHighlightCache.get().totalBlockList();
        return total > 0 ? String.valueOf(total) : "";
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent ev) {
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (cache.anyConsumerActive() && Utils.nullCheck()) {
            double rangeSq = this.range.getInput() * this.range.getInput();
            double px = mc.player.x;
            double py = mc.player.y;
            double pz = mc.player.z;
            boolean doOutline = this.outline.isToggled();
            boolean doShade = this.shade.isToggled();
            int max = (int) this.maxRenders.getInput();
            int rendered = 0;

            for (Entry<Long, Set<BlockPos>> entry : cache.entriesBlockList()) {
                if (rendered >= max) {
                    break;
                }

                for (BlockPos pos : entry.getValue()) {
                    if (rendered >= max) {
                        break;
                    }

                    double dx = pos.getX() + 0.5 - px;
                    double dy = pos.getY() + 0.5 - py;
                    double dz = pos.getZ() + 0.5 - pz;
                    if (!(dx * dx + dy * dy + dz * dz > rangeSq)) {
                        Box box = BlockUtils.getBlockSelectionBox(pos);
                        if (box == null || RenderUtils.isInViewFrustum(box)) {
                            EnumSet<Direction> visibleFaces = EnumSet.noneOf(Direction.class);

                            for (Direction f : Direction.ALL) {
                                if (!cache.containsBlockList(pos.offset(f))) {
                                    visibleFaces.add(f);
                                }
                            }

                            if (!visibleFaces.isEmpty()) {
                                BlockState state = mc.world.getBlockState(pos);
                                MapColor mapColor = state.getBlock().getMapColor(state);
                                int rgb = mapColor != null ? 0xFF000000 | mapColor.color : -65536;
                                RenderUtils.renderBlockShape(pos, state, rgb, doOutline, doShade, visibleFaces);
                                rendered++;
                            }
                        }
                    }
                }
            }
        }
    }
}
