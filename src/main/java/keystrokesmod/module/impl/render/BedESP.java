package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.SharedBlockHighlightCache;
import keystrokesmod.utility.Theme;
import keystrokesmod.utility.Utils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BedBlock.Part;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class BedESP extends Module {
    private static final String[] COLOR_MODES = new String[] {"Static", "Gradient", "Rainbow", "Theme"};
    private static final int THEME_COLOR_MODE = 3;
    private static final int DEFAULT_THEME_INDEX = Theme.DEFAULT_THEME_INDEX;
    private static final int THEME_ALPHA = 64;
    private static final int BED_BREAK_ANIMATION_DURATION = 250;
    private static final float BED_BREAK_ANIMATION_ALPHA = 0.95F;
    private static final float DEFENSE_AUTO_SCALE_THRESHOLD = 8.0F;
    private static final double BEAM_HEIGHT = 75.0;
    private static final double BEAM_WIDTH = 0.1;
    private static final float BEAM_ALPHA = 0.3F;
    private SliderSetting colorMode;
    private SliderSetting theme;
    private ColorSetting color;
    private ColorSetting color2;
    private SliderSetting gradientSpeed;
    private SliderSetting range;
    private SliderSetting scanSpeed;
    private ButtonSetting disableInLobby;
    private ButtonSetting firstBed;
    private ButtonSetting renderFullBlock;
    private ButtonSetting showExposedOutline;
    private ButtonSetting disableFullyExposed;
    private SliderSetting renderExposedBlocks;
    private ColorSetting exposedColor;
    private ColorSetting exposedOutlineColor;
    private ButtonSetting showDefenseLayers;
    private ButtonSetting showDefenseTools;
    private ButtonSetting showDefenseCounts;
    private SliderSetting defenseHeight;
    private SliderSetting defenseScale;
    private ButtonSetting defenseAutoScale;
    private ButtonSetting drawBeam;
    private boolean lastDefenseToolMode;
    private boolean pausedForLobby;
    private final List<BlockPos[]> lastRenderedBedPairs = new ArrayList<>();
    private static final int DEFENSE_ICON_SIZE = 16;
    private static final int DEFENSE_ICON_SPACING = 18;
    private static final int DEFENSE_PADDING = 3;
    private static final float DEFENSE_BACKGROUND_RADIUS = 8.0F;
    private static final int DEFENSE_BACKGROUND_COLOR = 1929379840;
    private static final int DEFENSE_BACKGROUND_OUTLINE_COLOR = -1778384896;
    private static final int DEFENSE_MAX_LAYERS = 5;
    private static final float DEFENSE_AIR_RATIO_THRESHOLD = 0.2F;
    private static final float DEFENSE_BLOCK_RATIO_THRESHOLD = 0.2F;
    private static final EnumMap<Direction, BedESP.LayerOffsets[]> DEFENSE_OFFSETS = buildLayerOffsetsByFacing();
    private final List<BlockPos[]> activeBedPairs = new ArrayList<>();
    private final Map<Long, BedESP.BedBreakAnimation> bedBreakAnimations = new HashMap<>();
    private final Map<Long, BedESP.DefenseOverlaySnapshot> defenseSnapshots = new HashMap<>();
    private final Map<Long, BedESP.DefenseWatchRegion> defenseWatchRegions = new HashMap<>();
    private final Map<Long, Set<Long>> watchedBedsByDefensePos = new HashMap<>();
    private final Map<Long, Set<Long>> watchedBedsByChunk = new HashMap<>();
    private final Set<Long> dirtyDefenseBeds = new HashSet<>();
    private final SharedBlockHighlightCache.UpdateListener defenseUpdateListener =
            new SharedBlockHighlightCache.UpdateListener() {
                @Override
                public void onBlockChanged(BlockPos pos, BlockState newState) {
                    BedESP.this.markBedsDirtyForDefensePos(pos);
                }

                @Override
                public void onChunkQueued(int chunkX, int chunkZ) {
                    BedESP.this.markBedsDirtyForChunk(chunkX, chunkZ);
                }

                @Override
                public void onChunkRemoved(int chunkX, int chunkZ) {
                    BedESP.this.markBedsDirtyForChunk(chunkX, chunkZ);
                }

                @Override
                public void onCacheCleared() {
                    BedESP.this.clearDefenseState();
                }
            };

    public BedESP() {
        super("BedESP", Module.category.render);
        this.liteModule = true;
        this.registerSetting(this.colorMode = new SliderSetting("Color mode", 0, COLOR_MODES));
        this.registerSetting(this.theme = new SliderSetting("Theme", DEFAULT_THEME_INDEX, Theme.THEMES_SETTING));
        this.registerSetting(this.color = new ColorSetting("Color", 255, 85, 85, 64));
        this.registerSetting(this.color2 = new ColorSetting("Color 2", 85, 85, 255, 64));
        this.registerSetting(this.gradientSpeed = new SliderSetting("Gradient speed", 1.0, 0.1, 8.0, 0.1));
        this.registerSetting(this.range = new SliderSetting("Range", 10.0, 4.0, 200.0, 2.0));
        this.registerSetting(this.scanSpeed = new SliderSetting("Scan speed", 8.0, 1.0, 32.0, 1.0));
        this.registerSetting(this.disableInLobby = new ButtonSetting("Disable in lobby", true));
        this.registerSetting(this.drawBeam = new ButtonSetting("Draw beam", false));
        this.registerSetting(this.firstBed = new ButtonSetting("Only render first bed", false));
        this.registerSetting(this.renderFullBlock = new ButtonSetting("Render full block", false));
        this.registerSetting(
                this.renderExposedBlocks = new SliderSetting("Render exposed", " block", true, -1.0, 1.0, 8.0, 1.0));
        this.registerSetting(this.exposedColor = new ColorSetting("Exposed color", 255, 0, 0, 64));
        this.registerSetting(this.showExposedOutline = new ButtonSetting("Outline exposed", true));
        this.registerSetting(this.disableFullyExposed = new ButtonSetting("Disable fully exposed", false));
        this.registerSetting(this.exposedOutlineColor = new ColorSetting("Exposed outline color", 255, 220, 64, 255));
        this.registerSetting(this.showDefenseLayers = new ButtonSetting("Show defense layers", false));
        this.registerSetting(this.showDefenseTools = new ButtonSetting("Show break tools", false));
        this.registerSetting(this.showDefenseCounts = new ButtonSetting("Show defense counts", true));
        this.registerSetting(this.defenseHeight = new SliderSetting("Defense height", 0.6, 0.1, 3.0, 0.1));
        this.registerSetting(this.defenseScale = new SliderSetting("Defense scale", 1.0, 0.1, 2.0, 0.05));
        this.registerSetting(this.defenseAutoScale = new ButtonSetting("Auto Scale", false));
    }

    @Override
    public void guiUpdate() {
        int mode = this.colorMode == null ? 0 : (int) this.colorMode.getInput();
        boolean staticMode = mode == 0;
        boolean gradientMode = mode == 1;
        boolean rainbowMode = mode == 2;
        boolean themeMode = mode == 3;
        boolean renderExposedEnabled = (int) this.renderExposedBlocks.getInput() != -1;
        this.theme.setVisible(themeMode, this);
        this.color.setVisible(staticMode || gradientMode || rainbowMode, this);
        this.color2.setVisible(gradientMode, this);
        this.gradientSpeed.setVisible(gradientMode, this);
        this.exposedColor.setVisible(renderExposedEnabled, this);
        boolean exposedOutlineAvailable = this.showExposedOutline.isToggled();
        this.disableFullyExposed.setVisible(exposedOutlineAvailable, this);
        this.exposedOutlineColor.setVisible(exposedOutlineAvailable, this);
        boolean defenseVisible = this.showDefenseLayers.isToggled();
        this.showDefenseTools.setVisible(defenseVisible, this);
        this.showDefenseCounts.setVisible(defenseVisible && !this.showDefenseTools.isToggled(), this);
        this.defenseHeight.setVisible(defenseVisible, this);
        this.defenseScale.setVisible(defenseVisible, this);
        this.defenseAutoScale.setVisible(defenseVisible, this);
    }

    @Override
    public void onEnable() {
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        cache.addUpdateListener(this.defenseUpdateListener);
        this.pausedForLobby = this.shouldPauseForLobby();
        if (this.pausedForLobby) {
            cache.detachBed();
            this.clearRenderedState();
        } else {
            cache.attachBed();
            cache.enqueueLoadedChunks();
        }
    }

    @Override
    public void onDisable() {
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        cache.removeUpdateListener(this.defenseUpdateListener);
        cache.detachBed();
        this.pausedForLobby = false;
        this.clearRenderedState();
    }

    @ForgeEvents.SubscribeEvent
    public void onEntityJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity == mc.player) {
            this.clearRenderedState();
        }
    }

    public int getScanSpeedBudget() {
        return this.isEnabled() && !this.pausedForLobby ? (int) this.scanSpeed.getInput() : 0;
    }

    @Override
    public void onUpdate() {
        if (!Utils.nullCheck()) {
            this.activeBedPairs.clear();
            this.clearDefenseState();
        } else {
            boolean pauseForLobby = this.shouldPauseForLobby();
            if (pauseForLobby != this.pausedForLobby) {
                this.setPausedForLobby(pauseForLobby);
            }

            if (pauseForLobby) {
                this.clearRenderedState();
            } else {
                SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
                double rangeSq = this.range.getInput() * this.range.getInput();
                double px = mc.player.x;
                double py = mc.player.y;
                double pz = mc.player.z;
                List<BlockPos[]> candidatePairs = this.collectActiveBedPairs(cache, px, py, pz, rangeSq);

                for (BlockPos[] pair : this.activeBedPairs) {
                    if (!this.hasBedAt(pair[0])) {
                        this.startBedBreakAnimation(pair);
                    }
                }

                this.activeBedPairs.clear();

                for (BlockPos[] pair : candidatePairs) {
                    this.activeBedPairs.add(copyBedPair(pair));
                }

                boolean defenseToolMode = this.showDefenseTools.isToggled();
                if (this.lastDefenseToolMode != defenseToolMode) {
                    this.clearDefenseState();
                    this.lastDefenseToolMode = defenseToolMode;
                }

                if (!this.showDefenseLayers.isToggled()) {
                    this.clearDefenseState();
                } else {
                    Set<Long> activeFeet = new HashSet<>();

                    for (BlockPos[] pair : candidatePairs) {
                        BlockPos foot = pair[0];
                        BlockPos head = pair[1];
                        long footKey = foot.toLong();
                        activeFeet.add(footKey);
                        BedESP.DefenseWatchRegion region = this.defenseWatchRegions.get(footKey);
                        if (region == null || !region.matches(head)) {
                            this.unregisterDefenseWatch(footKey);
                            this.registerDefenseWatch(foot, head);
                            this.dirtyDefenseBeds.add(footKey);
                        }

                        BedESP.DefenseOverlaySnapshot snapshot = this.defenseSnapshots.get(footKey);
                        if (snapshot == null || !snapshot.matches(head) || this.dirtyDefenseBeds.remove(footKey)) {
                            this.defenseSnapshots.put(footKey, this.computeDefenseSnapshot(foot, head));
                        }
                    }

                    for (Long footKey : new ArrayList<>(this.defenseWatchRegions.keySet())) {
                        if (!activeFeet.contains(footKey)) {
                            this.unregisterDefenseWatch(footKey);
                        }
                    }

                    this.dirtyDefenseBeds.retainAll(activeFeet);
                }
            }
        }
    }

    @Override
    public String getInfo() {
        if (!this.pausedForLobby && !this.shouldPauseForLobby()) {
            int n = SharedBlockHighlightCache.get().totalBedFeet();
            return n > 0 ? String.valueOf(n) : "";
        } else {
            return "";
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (this.isEnabled() && !this.shouldPauseForLobby()) {
                float blockHeight = this.getBlockHeight();
                double rangeSq = this.range.getInput() * this.range.getInput();
                double px = mc.player.x;
                double py = mc.player.y;
                double pz = mc.player.z;
                List<BlockPos[]> pairsToRender = new ArrayList<>();
                Set<BlockPos> addedFeet = new HashSet<>();
                Set<Long> activeFeet = new HashSet<>();
                Map<Long, Float> animatedAlphas = new HashMap<>();

                for (BlockPos[] pair : this.activeBedPairs) {
                    BlockPos foot = pair[0];
                    long footKey = foot.toLong();
                    activeFeet.add(footKey);
                    if (!this.hasBedAt(foot)) {
                        this.startBedBreakAnimation(pair);
                    } else {
                        this.bedBreakAnimations.remove(footKey);
                        Box bb = bedWorldBounds(pair[0], pair[1], blockHeight);
                        if (this.isBedOrBeamVisible(pair, bb) && addedFeet.add(foot)) {
                            pairsToRender.add(copyBedPair(pair));
                        }
                    }
                }

                for (BlockPos[] prev : new ArrayList<>(this.lastRenderedBedPairs)) {
                    if (prev != null && prev.length >= 2) {
                        BlockPos foot = prev[0];
                        BlockPos head = prev[1];
                        long footKey = foot.toLong();
                        if (!activeFeet.contains(footKey)) {
                            if (!this.hasBedAt(foot)) {
                                this.startBedBreakAnimation(prev);
                            } else {
                                this.bedBreakAnimations.remove(footKey);
                                double dx = foot.getX() + 0.5 - px;
                                double dy = foot.getY() + 0.5 - py;
                                double dz = foot.getZ() + 0.5 - pz;
                                if (!(dx * dx + dy * dy + dz * dz > rangeSq)) {
                                    Box bb = bedWorldBounds(foot, head, blockHeight);
                                    if (this.isBedOrBeamVisible(prev, bb)) {
                                        pairsToRender.add(copyBedPair(prev));
                                        addedFeet.add(foot);
                                    }
                                }
                            }
                        }
                    }
                }

                long now = System.currentTimeMillis();
                Iterator<Entry<Long, BedESP.BedBreakAnimation>> animationIterator =
                        this.bedBreakAnimations.entrySet().iterator();

                while (animationIterator.hasNext()) {
                    Entry<Long, BedESP.BedBreakAnimation> entry = animationIterator.next();
                    BedESP.BedBreakAnimation animation = entry.getValue();
                    BlockPos[] pair = animation.pair;
                    BlockPos foot = pair[0];
                    if (this.hasBedAt(foot)) {
                        animationIterator.remove();
                    } else {
                        float progress = (float) (now - animation.startedAt) / 250.0F;
                        if (progress >= 1.0F) {
                            animationIterator.remove();
                        } else {
                            float alpha = 0.95F * (1.0F - progress);
                            double dx = foot.getX() + 0.5 - px;
                            double dy = foot.getY() + 0.5 - py;
                            double dz = foot.getZ() + 0.5 - pz;
                            if (!(dx * dx + dy * dy + dz * dz > rangeSq)) {
                                Box bb = bedWorldBounds(pair[0], pair[1], blockHeight);
                                if (this.isBedOrBeamVisible(pair, bb) && addedFeet.add(foot)) {
                                    pairsToRender.add(copyBedPair(pair));
                                    animatedAlphas.put(entry.getKey(), alpha);
                                }
                            }
                        }
                    }
                }

                for (BlockPos[] pair : pairsToRender) {
                    long footKey = pair[0].toLong();
                    Float animatedAlpha = animatedAlphas.get(footKey);
                    this.renderBed(pair, blockHeight, animatedAlpha);
                    if (this.showDefenseLayers.isToggled()) {
                        BedESP.BedBreakAnimation animation = this.bedBreakAnimations.get(footKey);
                        if (animation != null || this.isLiveBedPair(pair)) {
                            BedESP.DefenseOverlaySnapshot snapshot =
                                    animation == null ? this.defenseSnapshots.get(footKey) : animation.defenseSnapshot;
                            this.renderDefenseOverlay(pair, blockHeight, snapshot, animatedAlpha);
                        }
                    }
                }

                this.lastRenderedBedPairs.clear();

                for (BlockPos[] pair : pairsToRender) {
                    this.lastRenderedBedPairs.add(copyBedPair(pair));
                }
            }
        }
    }

    private boolean shouldPauseForLobby() {
        return this.disableInLobby != null && this.disableInLobby.isToggled() && Utils.nullCheck() && Utils.isLobby();
    }

    private void setPausedForLobby(boolean paused) {
        this.pausedForLobby = paused;
        SharedBlockHighlightCache cache = SharedBlockHighlightCache.get();
        if (paused) {
            cache.detachBed();
            this.clearRenderedState();
        } else {
            cache.attachBed();
            cache.enqueueLoadedChunks();
        }
    }

    private void clearRenderedState() {
        this.activeBedPairs.clear();
        this.lastRenderedBedPairs.clear();
        this.bedBreakAnimations.clear();
        this.lastDefenseToolMode = false;
        this.clearDefenseState();
    }

    private void startBedBreakAnimation(BlockPos[] pair) {
        long footKey = pair[0].toLong();
        if (!this.bedBreakAnimations.containsKey(footKey)) {
            this.bedBreakAnimations.put(
                    footKey,
                    new BedESP.BedBreakAnimation(
                            copyBedPair(pair), System.currentTimeMillis(), this.defenseSnapshots.get(footKey)));
        }
    }

    private static BlockPos[] copyBedPair(BlockPos[] pair) {
        return new BlockPos[] {new BlockPos(pair[0]), new BlockPos(pair[1])};
    }

    private List<BlockPos[]> collectActiveBedPairs(
            SharedBlockHighlightCache cache, double px, double py, double pz, double rangeSq) {
        List<BlockPos[]> candidatePairs = new ArrayList<>();

        for (Entry<Long, Set<BlockPos>> chunk : cache.entriesBedFeet()) {
            for (BlockPos foot : chunk.getValue()) {
                double dx = foot.getX() + 0.5 - px;
                double dy = foot.getY() + 0.5 - py;
                double dz = foot.getZ() + 0.5 - pz;
                if (!(dx * dx + dy * dy + dz * dz > rangeSq)) {
                    BlockPos[] pair = this.footAndHead(foot);
                    if (pair != null && this.stillHasRenderableBed(pair)) {
                        candidatePairs.add(copyBedPair(pair));
                    }
                }
            }
        }

        if (this.firstBed.isToggled() && candidatePairs.size() > 1) {
            BlockPos[] nearest = null;
            double nearestDistanceSq = Double.MAX_VALUE;

            for (BlockPos[] pair : candidatePairs) {
                double dx = pair[0].getX() + 0.5 - px;
                double dy = pair[0].getY() + 0.5 - py;
                double dz = pair[0].getZ() + 0.5 - pz;
                double distanceSq = dx * dx + dy * dy + dz * dz;
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = pair;
                }
            }

            candidatePairs.clear();
            if (nearest != null) {
                candidatePairs.add(nearest);
            }

            return candidatePairs;
        } else {
            return candidatePairs;
        }
    }

    private static boolean isBedFoot(BlockState st) {
        return st != null && st.getBlock() instanceof BedBlock && st.get(BedBlock.PART) == Part.FOOT;
    }

    private boolean stillHasRenderableBed(BlockPos[] pair) {
        if (pair != null && pair.length >= 2 && mc.world != null) {
            BlockState a = mc.world.getBlockState(pair[0]);
            BlockState b = mc.world.getBlockState(pair[1]);
            return a != null && a.getBlock() instanceof BedBlock || b != null && b.getBlock() instanceof BedBlock;
        } else {
            return false;
        }
    }

    private boolean hasBedAt(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).getBlock() instanceof BedBlock;
    }

    private boolean isLiveBedPair(BlockPos[] pair) {
        if (pair != null && pair.length >= 2 && mc.world != null) {
            BlockState footState = mc.world.getBlockState(pair[0]);
            BlockState headState = mc.world.getBlockState(pair[1]);
            return isBedFoot(footState) && headState != null && headState.getBlock() instanceof BedBlock;
        } else {
            return false;
        }
    }

    private static Box bedWorldBounds(BlockPos foot, BlockPos head, float height) {
        int fx = foot.getX();
        int fy = foot.getY();
        int fz = foot.getZ();
        double h = fy + height;
        if (foot.getX() != head.getX()) {
            return foot.getX() > head.getX()
                    ? new Box(fx - 1.0, fy, fz, fx + 1.0, h, fz + 1.0)
                    : new Box(fx, fy, fz, fx + 2.0, h, fz + 1.0);
        } else {
            return foot.getZ() > head.getZ()
                    ? new Box(fx, fy, fz - 1.0, fx + 1.0, h, fz + 1.0)
                    : new Box(fx, fy, fz, fx + 1.0, h, fz + 2.0);
        }
    }

    private BlockPos[] footAndHead(BlockPos foot) {
        BlockState st = mc.world.getBlockState(foot);
        if (!(st.getBlock() instanceof BedBlock)) {
            return null;
        }

        Direction facing = (Direction) st.get(BedBlock.FACING);
        return new BlockPos[] {foot, foot.offset(facing)};
    }

    private boolean isOwnBed(BlockPos[] blocks) {
        return ModuleManager.bedwars != null && ModuleManager.bedwars.isOwnBed(blocks);
    }

    private Box beamWorldBounds(BlockPos[] blocks) {
        double x = (blocks[0].getX() + blocks[1].getX()) * 0.5 + 0.5;
        double z = (blocks[0].getZ() + blocks[1].getZ()) * 0.5 + 0.5;
        double y = blocks[0].getY();
        return new Box(x - 0.1, y, z - 0.1, x + 0.1, y + 75.0, z + 0.1);
    }

    private boolean isBedOrBeamVisible(BlockPos[] blocks, Box bedBounds) {
        return RenderUtils.isInViewFrustum(bedBounds)
                || this.drawBeam.isToggled()
                        && !this.isOwnBed(blocks)
                        && RenderUtils.isInViewFrustum(this.beamWorldBounds(blocks));
    }

    private void renderBedBeam(BlockPos[] blocks, Float animatedAlpha) {
        if (this.drawBeam.isToggled()
                && !this.isOwnBed(blocks)
                && RenderUtils.isInViewFrustum(this.beamWorldBounds(blocks))) {
            double x = (blocks[0].getX() + blocks[1].getX()) * 0.5;
            double z = (blocks[0].getZ() + blocks[1].getZ()) * 0.5;
            float alpha = (animatedAlpha == null ? 1.0F : animatedAlpha) * 0.3F;
            RenderUtils.drawBeam(
                    x, blocks[0].getY(), z, 75.0, 0.1, Theme.getGradient(Theme.Frost.ordinal(), 0.0), alpha);
        }
    }

    private void renderBed(BlockPos[] blocks, float height, Float animatedAlpha) {
        this.renderBedBeam(blocks, animatedAlpha);
        if (RenderUtils.isInViewFrustum(bedWorldBounds(blocks[0], blocks[1], height))) {
            List<BlockPos> exposedBlocks = this.getExposedBlocks(blocks);
            List<Box> mergedExposedBounds = this.mergeExposedBounds(exposedBlocks);
            int exposedCount = exposedBlocks.size();
            int exposedLimit = (int) this.renderExposedBlocks.getInput();
            boolean exposedRenderingEnabled = exposedLimit != -1;
            boolean renderExposedOverlays = exposedRenderingEnabled && exposedCount > 0 && exposedCount <= exposedLimit;
            boolean fullyExposed = this.isFullyExposed(blocks);
            boolean renderExposedOutline = this.showExposedOutline.isToggled()
                    && exposedCount > 0
                    && (!this.disableFullyExposed.isToggled() || !fullyExposed)
                    && (!exposedRenderingEnabled || exposedCount > exposedLimit);
            double x = blocks[0].getX() - mc.getEntityRenderDispatcher().cameraX;
            double y = blocks[0].getY() - mc.getEntityRenderDispatcher().cameraY;
            double z = blocks[0].getZ() - mc.getEntityRenderDispatcher().cameraZ;
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            int col = this.getCurrentColor();
            float drawA = animatedAlpha == null ? (col >> 24 & 0xFF) / 255.0F : animatedAlpha;
            float r = (col >> 16 & 0xFF) / 255.0F;
            float g = (col >> 8 & 0xFF) / 255.0F;
            float b = (col & 0xFF) / 255.0F;
            GL11.glColor4d(r, g, b, drawA);
            Box axisAlignedBB;
            if (blocks[0].getX() != blocks[1].getX()) {
                if (blocks[0].getX() > blocks[1].getX()) {
                    axisAlignedBB = new Box(x - 1.0, y, z, x + 1.0, y + height, z + 1.0);
                } else {
                    axisAlignedBB = new Box(x, y, z, x + 2.0, y + height, z + 1.0);
                }
            } else if (blocks[0].getZ() > blocks[1].getZ()) {
                axisAlignedBB = new Box(x, y, z - 1.0, x + 1.0, y + height, z + 1.0);
            } else {
                axisAlignedBB = new Box(x, y, z, x + 1.0, y + height, z + 2.0);
            }

            RenderUtils.drawBoundingBox(axisAlignedBB, r, g, b, drawA);
            if (renderExposedOverlays) {
                this.renderExposedBlockOverlays(mergedExposedBounds);
            }

            if (renderExposedOutline) {
                int outlineColor = this.exposedOutlineColor.getColor();
                float outlineA = (outlineColor >> 24 & 0xFF) / 255.0F;
                float outlineR = (outlineColor >> 16 & 0xFF) / 255.0F;
                float outlineG = (outlineColor >> 8 & 0xFF) / 255.0F;
                float outlineB = (outlineColor & 0xFF) / 255.0F;
                GL11.glLineWidth(3.0F);
                GL11.glColor4f(outlineR, outlineG, outlineB, outlineA);
                WorldRenderer.renderOutlineShape(axisAlignedBB);
                GL11.glLineWidth(2.0F);
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
        }
    }

    private List<Box> mergeExposedBounds(List<BlockPos> exposedBlocks) {
        List<Box> bounds = new ArrayList<>();

        for (BlockPos pos : exposedBlocks) {
            bounds.add(
                    new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0));
        }

        boolean merged;
        label32:
        do {
            merged = false;

            for (int i = 0; i < bounds.size(); i++) {
                for (int j = i + 1; j < bounds.size(); j++) {
                    Box combined = this.mergeAdjacentBounds(bounds.get(i), bounds.get(j));
                    if (combined != null) {
                        bounds.set(i, combined);
                        bounds.remove(j);
                        merged = true;
                        continue label32;
                    }
                }
            }
        } while (merged);

        return bounds;
    }

    private Box mergeAdjacentBounds(Box first, Box second) {
        boolean sameX = first.minX == second.minX && first.maxX == second.maxX;
        boolean sameY = first.minY == second.minY && first.maxY == second.maxY;
        boolean sameZ = first.minZ == second.minZ && first.maxZ == second.maxZ;
        if (!sameY || !sameZ || first.maxX != second.minX && second.maxX != first.minX) {
            if (!sameX || !sameZ || first.maxY != second.minY && second.maxY != first.minY) {
                return !sameX || !sameY || first.maxZ != second.minZ && second.maxZ != first.minZ
                        ? null
                        : new Box(
                                first.minX,
                                first.minY,
                                Math.min(first.minZ, second.minZ),
                                first.maxX,
                                first.maxY,
                                Math.max(first.maxZ, second.maxZ));
            } else {
                return new Box(
                        first.minX,
                        Math.min(first.minY, second.minY),
                        first.minZ,
                        first.maxX,
                        Math.max(first.maxY, second.maxY),
                        first.maxZ);
            }
        } else {
            return new Box(
                    Math.min(first.minX, second.minX),
                    first.minY,
                    first.minZ,
                    Math.max(first.maxX, second.maxX),
                    first.maxY,
                    first.maxZ);
        }
    }

    private void renderExposedBlockOverlays(List<Box> exposedBounds) {
        if (!exposedBounds.isEmpty()) {
            int overlayColor = this.exposedColor.getColor();
            float overlayA = (overlayColor >> 24 & 0xFF) / 255.0F;
            float overlayR = (overlayColor >> 16 & 0xFF) / 255.0F;
            float overlayG = (overlayColor >> 8 & 0xFF) / 255.0F;
            float overlayB = (overlayColor & 0xFF) / 255.0F;
            EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
            GL11.glColor4f(overlayR, overlayG, overlayB, overlayA);

            for (Box bounds : exposedBounds) {
                Box renderedBounds =
                        bounds.moved(-renderManager.cameraX, -renderManager.cameraY, -renderManager.cameraZ);
                RenderUtils.drawBoundingBox(renderedBounds, overlayR, overlayG, overlayB, overlayA);
            }
        }
    }

    private List<BlockPos> getExposedBlocks(BlockPos[] pair) {
        if (pair != null && pair.length >= 2 && mc.world != null) {
            Set<BlockPos> exposedBlocks = new HashSet<>();

            for (BlockPos bedPart : pair) {
                BlockState bedState = mc.world.getBlockState(bedPart);
                if (bedState != null && bedState.getBlock() instanceof BedBlock) {
                    for (Direction side : Direction.values()) {
                        BlockPos neighbor = bedPart.offset(side);
                        if (!neighbor.equals(pair[0]) && !neighbor.equals(pair[1])) {
                            Block neighborBlock =
                                    mc.world.getBlockState(neighbor).getBlock();
                            if (neighborBlock == Blocks.AIR) {
                                exposedBlocks.add(neighbor);
                            }
                        }
                    }
                }
            }

            return new ArrayList<>(exposedBlocks);
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isFullyExposed(BlockPos[] pair) {
        if (pair != null && pair.length >= 2 && mc.world != null) {
            boolean hasBedPart = false;

            for (BlockPos bedPart : pair) {
                BlockState state = mc.world.getBlockState(bedPart);
                Block block = state.getBlock();
                if (block instanceof BedBlock) {
                    hasBedPart = true;
                } else if (block != Blocks.AIR) {
                    return false;
                }

                for (Direction side : Direction.values()) {
                    if (side != Direction.DOWN) {
                        BlockPos neighbor = bedPart.offset(side);
                        if (!neighbor.equals(pair[0])
                                && !neighbor.equals(pair[1])
                                && mc.world.getBlockState(neighbor).getBlock() != Blocks.AIR) {
                            return false;
                        }
                    }
                }
            }

            return hasBedPart;
        } else {
            return false;
        }
    }

    private void renderDefenseOverlay(
            BlockPos[] blocks, float blockHeight, BedESP.DefenseOverlaySnapshot snapshot, Float animatedAlpha) {
        if (snapshot != null && snapshot.matches(blocks[1]) && !snapshot.entries.isEmpty()) {
            EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
            TextRenderer fontRenderer = mc.textRenderer;
            if (renderManager != null && fontRenderer != null) {
                Box bedBounds = bedWorldBounds(blocks[0], blocks[1], blockHeight);
                double x = (bedBounds.minX + bedBounds.maxX) * 0.5 - renderManager.cameraX;
                double y = bedBounds.maxY + this.defenseHeight.getInput() - renderManager.cameraY;
                double z = (bedBounds.minZ + bedBounds.maxZ) * 0.5 - renderManager.cameraZ;
                float renderScale = this.computeDefenseBaseScaleValue();
                if (this.defenseAutoScale.isToggled()) {
                    float distance = (float) Math.sqrt(x * x + y * y + z * z);
                    renderScale = this.computeDefenseScaleValue(distance);
                }

                if (animatedAlpha != null) {
                    double t = animatedAlpha / 0.95F;
                    double scaleMultiplier = t <= 0.55
                            ? 1.3 * Math.exp(-5.0 * (1.0 - t))
                            : 1.3 * (1.0 + 0.3 * Math.sin((t - 0.55) * Math.PI / 0.3));
                    renderScale *= (float) scaleMultiplier;
                }

                List<BedESP.DefenseOverlayEntry> stacks = snapshot.entries;
                int contentWidth = stacks.size() * 18 - 2;
                int left = -contentWidth / 2;
                int iconY = -8;
                int backgroundLeft = left - 3;
                int backgroundTop = iconY - 3;
                int backgroundRight = left + contentWidth + 3;
                int backgroundBottom = iconY + 16 + 3;
                GlStateManager.pushMatrix();

                try {
                    GlStateManager.translatef((float) x, (float) y, (float) z);
                    GlStateManager.rotatef(-renderManager.cameraYaw, 0.0F, 1.0F, 0.0F);
                    GlStateManager.rotatef(renderManager.cameraPitch, 1.0F, 0.0F, 0.0F);
                    GlStateManager.scalef(-renderScale, -renderScale, renderScale);
                    GlStateManager.disableLighting();
                    GlStateManager.depthMask(false);
                    GlStateManager.disableDepthTest();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                    this.renderDefenseBackground(backgroundLeft, backgroundTop, backgroundRight, backgroundBottom);
                    this.applyDefenseOverlayTextState();

                    for (int i = 0; i < stacks.size(); i++) {
                        BedESP.DefenseOverlayEntry stackData = stacks.get(i);
                        int iconX = left + i * 18;
                        this.renderDefenseEntry(stackData, iconX, iconY);
                        this.applyDefenseOverlayTextState();
                        if (!this.showDefenseTools.isToggled()
                                && this.showDefenseCounts.isToggled()
                                && stackData.count > 1) {
                            String countText = String.valueOf(stackData.getCount());
                            fontRenderer.drawWithShadow(
                                    countText, iconX + 17 - fontRenderer.getWidth(countText), iconY + 9, 16777215);
                            this.applyDefenseOverlayTextState();
                        }
                    }
                } finally {
                    GlStateManager.enableDepthTest();
                    GlStateManager.depthMask(true);
                    GlStateManager.disableLighting();
                    GlStateManager.disableRescaleNormal();
                    GlStateManager.disableBlend();
                    GlStateManager.enableAlphaTest();
                    GlStateManager.enableTexture();
                    GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                    GL11.glTexEnvi(8960, 8704, 8448);
                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.popMatrix();
                }
            }
        }
    }

    private void renderDefenseEntry(BedESP.DefenseOverlayEntry entry, int iconX, int iconY) {
        if (entry != null) {
            if (entry.hasItemStack()) {
                RenderUtils.renderItemAndEffectIntoGui3D(entry.renderStack, iconX, iconY);
            } else {
                if (entry.hasBlockSprite()) {
                    this.renderDefenseBlockSprite(entry.blockSprite, iconX, iconY);
                }
            }
        }
    }

    private void renderDefenseBlockSprite(TextureAtlasSprite sprite, int iconX, int iconY) {
        if (sprite != null) {
            mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            GlStateManager.enableTexture();
            GlStateManager.enableBlend();
            GlStateManager.enableAlphaTest();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder worldRenderer = tessellator.getBuffer();
            worldRenderer.begin(7, DefaultVertexFormat.POSITION_TEX);
            worldRenderer
                    .vertex(iconX, iconY + 16, 0.0)
                    .texture(sprite.getUMin(), sprite.getVMax())
                    .nextVertex();
            worldRenderer
                    .vertex(iconX + 16, iconY + 16, 0.0)
                    .texture(sprite.getUMax(), sprite.getVMax())
                    .nextVertex();
            worldRenderer
                    .vertex(iconX + 16, iconY, 0.0)
                    .texture(sprite.getUMax(), sprite.getVMin())
                    .nextVertex();
            worldRenderer
                    .vertex(iconX, iconY, 0.0)
                    .texture(sprite.getUMin(), sprite.getVMin())
                    .nextVertex();
            tessellator.end();
        }
    }

    private void renderDefenseBackground(int left, int top, int right, int bottom) {
        RenderUtils.drawRoundedGradientOutlinedRectangle(
                left, top, right, bottom, 8.0F, 1929379840, -1778384896, -1778384896);
    }

    private void applyDefenseOverlayTextState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GL11.glTexEnvi(8960, 8704, 8448);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private BedESP.DefenseOverlaySnapshot computeDefenseSnapshot(BlockPos foot, BlockPos head) {
        BedESP.LayerOffsets[] layers = this.getLayerOffsets(foot, head);
        if (layers == null) {
            return new BedESP.DefenseOverlaySnapshot(new BlockPos(head), Collections.emptyList());
        }

        Map<BedESP.DefenseBlockKey, Integer> finalCounts = new HashMap<>();
        Map<Integer, BedESP.DefenseBlockKey> resolvedBlockKeys = new HashMap<>();
        int sparseLayerCount = 0;
        Mutable mutablePos = new Mutable();

        for (BedESP.LayerOffsets layerOffsets : layers) {
            Map<BedESP.DefenseBlockKey, Integer> layerCounts = new HashMap<>();
            int layerTotalBlocks = 0;
            int layerAirBlocks = 0;

            for (BedESP.RelativeOffset offset : layerOffsets.positions) {
                mutablePos.set(foot.getX() + offset.x, foot.getY() + offset.y, foot.getZ() + offset.z);
                if (this.accumulateDefenseBlock(mutablePos, layerCounts, resolvedBlockKeys)) {
                    layerAirBlocks++;
                }

                layerTotalBlocks++;
            }

            if (layerTotalBlocks != 0 && !((float) layerAirBlocks / layerTotalBlocks > 0.2F)) {
                sparseLayerCount = 0;

                for (Entry<BedESP.DefenseBlockKey, Integer> countedBlock : layerCounts.entrySet()) {
                    int count = countedBlock.getValue();
                    if ((float) count / layerTotalBlocks >= 0.2F) {
                        finalCounts.put(
                                countedBlock.getKey(), finalCounts.getOrDefault(countedBlock.getKey(), 0) + count);
                    }
                }
            } else if (++sparseLayerCount >= 2) {
                break;
            }
        }

        List<BedESP.DefenseOverlayEntry> entries;
        if (this.showDefenseTools.isToggled()) {
            Set<BedESP.ToolOverlayType> toolTypes = new HashSet<>();

            for (BedESP.DefenseBlockKey countedBlock : finalCounts.keySet()) {
                BedESP.ToolOverlayType toolType = BedESP.ToolOverlayType.fromState(countedBlock.state);
                if (toolType != null) {
                    toolTypes.add(toolType);
                }
            }

            if (toolTypes.contains(BedESP.ToolOverlayType.DIAMOND_PICKAXE)) {
                toolTypes.remove(BedESP.ToolOverlayType.IRON_PICKAXE);
            }

            entries = new ArrayList<>(toolTypes.size());

            for (BedESP.ToolOverlayType toolType : toolTypes) {
                entries.add(new BedESP.DefenseOverlayEntry(toolType.createRenderStack(), null, 1, toolType.sortName));
            }

            entries.sort((left, right) -> left.sortName.compareToIgnoreCase(right.sortName));
        } else {
            entries = new ArrayList<>();

            for (Entry<BedESP.DefenseBlockKey, Integer> countedBlock : finalCounts.entrySet()) {
                entries.add(new BedESP.DefenseOverlayEntry(
                        countedBlock.getKey().createRenderStack(),
                        countedBlock.getKey().blockSprite,
                        countedBlock.getValue(),
                        countedBlock.getKey().sortName));
            }

            entries.sort((left, right) -> {
                int countCompare = Integer.compare(right.count, left.count);
                return countCompare != 0 ? countCompare : left.sortName.compareToIgnoreCase(right.sortName);
            });
        }

        return new BedESP.DefenseOverlaySnapshot(new BlockPos(head), Collections.unmodifiableList(entries));
    }

    private boolean accumulateDefenseBlock(
            BlockPos pos,
            Map<BedESP.DefenseBlockKey, Integer> layerCounts,
            Map<Integer, BedESP.DefenseBlockKey> resolvedBlockKeys) {
        BlockState state = mc.world.getBlockState(pos);
        if (state != null && state.getBlock() != Blocks.AIR) {
            BlockState normalizedState = normalizeDefenseState(state);
            int stateId = Block.serialize(normalizedState);
            BedESP.DefenseBlockKey key = resolvedBlockKeys.get(stateId);
            if (key == null) {
                key = BedESP.DefenseBlockKey.from(normalizedState, pos, mc.world);
                resolvedBlockKeys.put(stateId, key);
            }

            layerCounts.put(key, layerCounts.getOrDefault(key, 0) + 1);
            return false;
        } else {
            return true;
        }
    }

    private BedESP.LayerOffsets[] getLayerOffsets(BlockPos foot, BlockPos head) {
        Direction facing = getBedFacing(foot, head);
        return facing == null ? null : DEFENSE_OFFSETS.get(facing);
    }

    private static EnumMap<Direction, BedESP.LayerOffsets[]> buildLayerOffsetsByFacing() {
        EnumMap<Direction, BedESP.LayerOffsets[]> offsets = new EnumMap<>(Direction.class);
        offsets.put(Direction.EAST, buildLayerOffsets(Direction.EAST));
        offsets.put(Direction.WEST, buildLayerOffsets(Direction.WEST));
        offsets.put(Direction.SOUTH, buildLayerOffsets(Direction.SOUTH));
        offsets.put(Direction.NORTH, buildLayerOffsets(Direction.NORTH));
        return offsets;
    }

    private static BedESP.LayerOffsets[] buildLayerOffsets(Direction canonicalFacing) {
        BlockPos foot = BlockPos.ORIGIN;
        BlockPos head = foot.offset(canonicalFacing);
        boolean facingZ = canonicalFacing.getAxis() == Axis.Z;
        BlockPos firstBedPart =
                facingZ ? (head.getZ() > foot.getZ() ? head : foot) : (head.getX() > foot.getX() ? head : foot);
        BlockPos secondBedPart = firstBedPart.equals(foot) ? head : foot;
        BlockPos[] bedParts = new BlockPos[] {firstBedPart, secondBedPart};
        BedESP.LayerOffsets[] layers = new BedESP.LayerOffsets[5];
        Set<Long> seenAcrossLayers = new HashSet<>();

        for (int layer = 1; layer <= 5; layer++) {
            List<BedESP.RelativeOffset> offsets = new ArrayList<>();

            for (int bedIndex = 0; bedIndex < bedParts.length; bedIndex++) {
                BlockPos bed = bedParts[bedIndex];
                int outwardOffset = bedIndex == 0 ? layer : -layer;
                int startX = facingZ ? bed.getX() : bed.getX() + outwardOffset;
                int startZ = facingZ ? bed.getZ() + outwardOffset : bed.getZ();

                for (int advance = 0; advance <= layer; advance++) {
                    int yOffset = 0;

                    for (int breadth = advance; breadth >= 0; breadth--) {
                        int firstY = bed.getY() + yOffset;
                        int secondY = firstY;
                        int firstX;
                        int firstZ;
                        int secondX;
                        int secondZ;
                        if (facingZ) {
                            int z = startZ - (bedIndex == 0 ? advance : -advance);
                            firstX = startX - breadth;
                            firstZ = z;
                            secondX = startX + breadth;
                            secondZ = z;
                        } else {
                            int x = startX - (bedIndex == 0 ? advance : -advance);
                            firstX = x;
                            firstZ = startZ - breadth;
                            secondX = x;
                            secondZ = startZ + breadth;
                        }

                        addOffset(offsets, seenAcrossLayers, firstX, firstY, firstZ);
                        addOffset(offsets, seenAcrossLayers, secondX, secondY, secondZ);
                        if (breadth > 0) {
                            yOffset++;
                        }
                    }
                }
            }

            layers[layer - 1] = new BedESP.LayerOffsets(Collections.unmodifiableList(offsets));
        }

        return layers;
    }

    private static void addOffset(List<BedESP.RelativeOffset> offsets, Set<Long> seen, int x, int y, int z) {
        long key = new BlockPos(x, y, z).toLong();
        if (seen.add(key)) {
            offsets.add(new BedESP.RelativeOffset(x, y, z));
        }
    }

    private static Direction getBedFacing(BlockPos foot, BlockPos head) {
        int dx = head.getX() - foot.getX();
        int dz = head.getZ() - foot.getZ();

        for (Direction facing : Direction.BY_ID_HORIZONTAL) {
            if (facing.getOffsetX() == dx && facing.getOffsetZ() == dz) {
                return facing;
            }
        }

        return null;
    }

    private void clearDefenseState() {
        this.defenseSnapshots.clear();
        this.defenseWatchRegions.clear();
        this.watchedBedsByDefensePos.clear();
        this.watchedBedsByChunk.clear();
        this.dirtyDefenseBeds.clear();
    }

    private void markBedsDirtyForDefensePos(BlockPos pos) {
        if (pos != null) {
            Set<Long> feet = this.watchedBedsByDefensePos.get(pos.toLong());
            if (feet != null) {
                this.dirtyDefenseBeds.addAll(feet);
            }
        }
    }

    private void markBedsDirtyForChunk(int chunkX, int chunkZ) {
        Set<Long> feet = this.watchedBedsByChunk.get(chunkKey(chunkX, chunkZ));
        if (feet != null) {
            this.dirtyDefenseBeds.addAll(feet);
        }
    }

    private void registerDefenseWatch(BlockPos foot, BlockPos head) {
        BedESP.LayerOffsets[] layers = this.getLayerOffsets(foot, head);
        long footKey = foot.toLong();
        if (layers == null) {
            this.defenseWatchRegions.put(
                    footKey,
                    new BedESP.DefenseWatchRegion(head.toLong(), Collections.emptySet(), Collections.emptySet()));
        } else {
            Set<Long> watchedPositions = new HashSet<>();
            Set<Long> watchedChunks = new HashSet<>();

            for (BedESP.LayerOffsets layer : layers) {
                for (BedESP.RelativeOffset offset : layer.positions) {
                    BlockPos watchedPos =
                            new BlockPos(foot.getX() + offset.x, foot.getY() + offset.y, foot.getZ() + offset.z);
                    long posKey = watchedPos.toLong();
                    if (watchedPositions.add(posKey)) {
                        this.watchedBedsByDefensePos
                                .computeIfAbsent(posKey, ignored -> new HashSet<>())
                                .add(footKey);
                        watchedChunks.add(chunkKey(watchedPos.getX() >> 4, watchedPos.getZ() >> 4));
                    }
                }
            }

            for (Long chunkKey : watchedChunks) {
                this.watchedBedsByChunk
                        .computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                        .add(footKey);
            }

            this.defenseWatchRegions.put(
                    footKey,
                    new BedESP.DefenseWatchRegion(
                            head.toLong(),
                            Collections.unmodifiableSet(watchedPositions),
                            Collections.unmodifiableSet(watchedChunks)));
        }
    }

    private void unregisterDefenseWatch(long footKey) {
        BedESP.DefenseWatchRegion region = this.defenseWatchRegions.remove(footKey);
        this.defenseSnapshots.remove(footKey);
        this.dirtyDefenseBeds.remove(footKey);
        if (region != null) {
            for (Long posKey : region.watchedPositions) {
                Set<Long> feet = this.watchedBedsByDefensePos.get(posKey);
                if (feet != null) {
                    feet.remove(footKey);
                    if (feet.isEmpty()) {
                        this.watchedBedsByDefensePos.remove(posKey);
                    }
                }
            }

            for (Long chunkKey : region.watchedChunks) {
                Set<Long> feet = this.watchedBedsByChunk.get(chunkKey);
                if (feet != null) {
                    feet.remove(footKey);
                    if (feet.isEmpty()) {
                        this.watchedBedsByChunk.remove(chunkKey);
                    }
                }
            }
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 4294967295L;
    }

    private float getBlockHeight() {
        return this.renderFullBlock.isToggled() ? 1.0F : 0.5625F;
    }

    private float computeDefenseBaseScaleValue() {
        return (float) this.defenseScale.getInput() * 0.02F;
    }

    private float computeDefenseScaleValue(float distance) {
        float baseScale = this.computeDefenseBaseScaleValue();
        float effectiveDistance = Math.max(1.0F, distance);
        float scaledValue = baseScale * (effectiveDistance / 8.0F);
        return Math.max(baseScale, scaledValue);
    }

    private static ItemStack resolveBlockItemStack(BlockState state, BlockPos pos, World world) {
        Block block = state.getBlock();

        try {
            Item item = block.getPickItem(world, pos);
            if (item == null) {
                return null;
            }

            int meta = item.hasCustomData() ? block.getPickItemMetadata(world, pos) : 0;
            return new ItemStack(item, 1, meta);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static TextureAtlasSprite resolveBlockSprite(BlockState state) {
        if (mc != null
                && mc.getBlockRenderDispatcher() != null
                && mc.getBlockRenderDispatcher().getModelShaper() != null) {
            TextureAtlasSprite sprite =
                    mc.getBlockRenderDispatcher().getModelShaper().getParticleIcon(state);
            return sprite == null ? null : sprite;
        } else {
            return null;
        }
    }

    private static BlockState normalizeDefenseState(BlockState state) {
        if (state == null) {
            return null;
        } else {
            Block block = state.getBlock();
            if (block == Blocks.WATER || block == Blocks.FLOWING_WATER) {
                return Blocks.WATER.defaultState();
            } else if (block == Blocks.LAVA || block == Blocks.FLOWING_LAVA) {
                return Blocks.LAVA.defaultState();
            } else {
                return block == Blocks.FIRE ? Blocks.FIRE.defaultState() : state;
            }
        }
    }

    private static String getFallbackStateName(BlockState state) {
        String localizedName = state.getBlock().getName();
        if (localizedName != null && !localizedName.isEmpty()) {
            return localizedName;
        } else {
            Object registryName = Block.REGISTRY.getKey(state.getBlock());
            if (registryName != null) {
                int meta = state.getBlock().getMetadataFromState(state);
                return meta != 0 ? registryName + ":" + meta : registryName.toString();
            } else {
                return "unknown";
            }
        }
    }

    private static ItemStack fallbackRenderStack(Block block) {
        if (block == Blocks.BED) {
            return new ItemStack(Items.BED);
        }

        try {
            Item item = Item.byBlock(block);
            if (item != null) {
                int meta = block.getMetadataFromState(block.defaultState());
                return new ItemStack(item, 1, meta);
            }
        } catch (Exception var3) {
        }

        return new ItemStack(Blocks.BARRIER);
    }

    public int getCurrentColor() {
        int mode = this.colorMode == null ? 0 : (int) this.colorMode.getInput();
        switch (mode) {
            case 0:
                return this.color.getColor();
            case 1:
                double pct = Math.sin(System.currentTimeMillis() * this.gradientSpeed.getInput() / 1000.0) * 0.5 + 0.5;
                Color firstColor = new Color(
                        this.color.getRed(), this.color.getGreen(), this.color.getBlue(), this.color.getAlpha());
                Color secondColor = new Color(
                        this.color2.getRed(), this.color2.getGreen(), this.color2.getBlue(), this.color2.getAlpha());
                Color blended = Theme.convert(firstColor, secondColor, pct);
                int alpha = (int) (this.color.getAlpha() * pct + this.color2.getAlpha() * (1.0 - pct));
                return Utils.mergeAlpha(blended.getRGB(), alpha);
            case 2:
                return Utils.mergeAlpha(Utils.getChroma(2L, 0L), this.color.getAlpha());
            case 3:
                return Utils.mergeAlpha(Theme.getGradient(this.getSelectedThemeIndex(), 0.0), 64);
            default:
                return this.color.getColor();
        }
    }

    private int getSelectedThemeIndex() {
        return this.theme != null && this.theme.getOptions().length != 0
                ? (int) Math.max(0.0, Math.min(this.theme.getOptions().length - 1, this.theme.getInput()))
                : DEFAULT_THEME_INDEX;
    }

    private static final class BedBreakAnimation {
        private final BlockPos[] pair;
        private final long startedAt;
        private final BedESP.DefenseOverlaySnapshot defenseSnapshot;

        private BedBreakAnimation(BlockPos[] pair, long startedAt, BedESP.DefenseOverlaySnapshot defenseSnapshot) {
            this.pair = pair;
            this.startedAt = startedAt;
            this.defenseSnapshot = defenseSnapshot;
        }
    }

    private static final class DefenseBlockKey {
        private final BlockState state;
        private final String identityKey;
        private final String sortName;
        private final int hashCode;
        private final ItemStack renderStack;
        private final TextureAtlasSprite blockSprite;

        private DefenseBlockKey(
                BlockState state,
                String identityKey,
                String sortName,
                ItemStack renderStack,
                TextureAtlasSprite blockSprite) {
            this.state = state;
            this.identityKey = identityKey;
            this.sortName = sortName;
            this.renderStack = renderStack;
            this.blockSprite = blockSprite;
            this.hashCode = identityKey.hashCode();
        }

        private static BedESP.DefenseBlockKey from(BlockState state, BlockPos pos, World world) {
            String fallbackName = BedESP.getFallbackStateName(state);
            ItemStack stack = BedESP.resolveBlockItemStack(state, pos, world);
            String identityKey = resolveIdentityKey(state, stack);
            TextureAtlasSprite sprite = null;
            if (stack == null || stack.getItem() == null) {
                sprite = BedESP.resolveBlockSprite(state);
                if (sprite == null) {
                    stack = BedESP.fallbackRenderStack(state.getBlock());
                } else {
                    stack = null;
                }
            }

            String sortName =
                    stack != null && stack.getItem() != null ? getSafeDisplayName(stack, fallbackName) : fallbackName;
            return new BedESP.DefenseBlockKey(state, identityKey, sortName, stack, sprite);
        }

        private ItemStack createRenderStack() {
            return this.renderStack == null ? null : this.renderStack.copy();
        }

        private static String resolveIdentityKey(BlockState state, ItemStack stack) {
            if (stack != null && stack.getItem() != null) {
                return Item.getId(stack.getItem()) + ":" + stack.getMetadata();
            }

            Object registryName = Block.REGISTRY.getKey(state.getBlock());
            return registryName != null ? registryName.toString() : Integer.toString(Block.getId(state.getBlock()));
        }

        private static String getSafeDisplayName(ItemStack stack, String fallback) {
            if (stack != null && stack.getItem() != null) {
                try {
                    String displayName = stack.getHoverName();
                    return displayName != null && !displayName.isEmpty() ? displayName : fallback;
                } catch (Exception ignored) {
                    return fallback;
                }
            } else {
                return fallback;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof BedESP.DefenseBlockKey)) {
                return false;
            }

            BedESP.DefenseBlockKey other = (BedESP.DefenseBlockKey) obj;
            return this.identityKey.equals(other.identityKey);
        }

        @Override
        public int hashCode() {
            return this.hashCode;
        }
    }

    private static final class DefenseOverlayEntry {
        private final ItemStack renderStack;
        private final TextureAtlasSprite blockSprite;
        private final int count;
        private final String sortName;

        private DefenseOverlayEntry(ItemStack renderStack, TextureAtlasSprite blockSprite, int count, String sortName) {
            this.renderStack = renderStack;
            this.blockSprite = blockSprite;
            this.count = count;
            this.sortName = sortName;
        }

        private boolean hasItemStack() {
            return this.renderStack != null && this.renderStack.getItem() != null;
        }

        private boolean hasBlockSprite() {
            return this.blockSprite != null;
        }

        private int getCount() {
            return this.count;
        }
    }

    private static final class DefenseOverlaySnapshot {
        private final long headKey;
        private final List<BedESP.DefenseOverlayEntry> entries;

        private DefenseOverlaySnapshot(BlockPos head, List<BedESP.DefenseOverlayEntry> entries) {
            this.headKey = head.toLong();
            this.entries = entries;
        }

        private boolean matches(BlockPos otherHead) {
            return otherHead != null && this.headKey == otherHead.toLong();
        }
    }

    private static final class DefenseWatchRegion {
        private final long headKey;
        private final Set<Long> watchedPositions;
        private final Set<Long> watchedChunks;

        private DefenseWatchRegion(long headKey, Set<Long> watchedPositions, Set<Long> watchedChunks) {
            this.headKey = headKey;
            this.watchedPositions = watchedPositions;
            this.watchedChunks = watchedChunks;
        }

        private boolean matches(BlockPos otherHead) {
            return otherHead != null && this.headKey == otherHead.toLong();
        }
    }

    private static final class LayerOffsets {
        private final List<BedESP.RelativeOffset> positions;

        private LayerOffsets(List<BedESP.RelativeOffset> positions) {
            this.positions = positions;
        }
    }

    private static final class RelativeOffset {
        private final int x;
        private final int y;
        private final int z;

        private RelativeOffset(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private enum ToolOverlayType {
        DIAMOND_PICKAXE(Items.DIAMOND_PICKAXE, "Diamond Pickaxe"),
        IRON_AXE(Items.IRON_AXE, "Iron Axe"),
        IRON_HOE(Items.IRON_HOE, "Iron Hoe"),
        IRON_PICKAXE(Items.IRON_PICKAXE, "Iron Pickaxe"),
        SHEARS(Items.SHEARS, "Shears"),
        IRON_SHOVEL(Items.IRON_SHOVEL, "Iron Shovel"),
        IRON_SWORD(Items.IRON_SWORD, "Iron Sword");

        private final Item item;
        private final String sortName;
        private final ItemStack renderStack;

        ToolOverlayType(Item item, String sortName) {
            this.item = item;
            this.sortName = sortName;
            this.renderStack = new ItemStack(item);
        }

        private ItemStack createRenderStack() {
            return this.renderStack.copy();
        }

        private static BedESP.ToolOverlayType fromState(BlockState state) {
            if (state == null) {
                return null;
            }

            Block block = state.getBlock();
            if (block == Blocks.OBSIDIAN) {
                return DIAMOND_PICKAXE;
            }

            BedESP.ToolOverlayType bestTool = IRON_PICKAXE;
            float bestEfficiency = 1.0F;

            for (BedESP.ToolOverlayType toolType : values()) {
                if (toolType != DIAMOND_PICKAXE) {
                    float efficiency = Utils.getEfficiency(toolType.renderStack, block);
                    if (efficiency > bestEfficiency) {
                        bestEfficiency = efficiency;
                        bestTool = toolType;
                    }
                }
            }

            return bestTool;
        }
    }
}
