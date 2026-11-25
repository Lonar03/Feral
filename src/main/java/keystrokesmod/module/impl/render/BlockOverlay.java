package keystrokesmod.module.impl.render;

import java.awt.Color;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.StairsUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DeadBushBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.opengl.GL11;

public class BlockOverlay extends Module {
    private static final String[] RENDER_MODES = new String[] {"Hidden", "Vanilla", "Side", "Full"};
    private static final String[] COLOR_MODES = new String[] {"Static", "Gradient", "Fade", "Chroma"};
    private static final double PADDING = 0.002;
    private final SliderSetting renderMode;
    private final GroupSetting overlayGroup;
    private final ButtonSetting overlayVisible;
    private final SliderSetting overlayColorMode;
    private final ColorSetting overlayColor;
    private final ColorSetting overlayColor2;
    private final SliderSetting overlayFadeSpeed;
    private final SliderSetting overlayChromaSpeed;
    private final GroupSetting outlineGroup;
    private final ButtonSetting outlineVisible;
    private final SliderSetting outlineColorMode;
    private final ColorSetting outlineColor;
    private final ColorSetting outlineColor2;
    private final SliderSetting outlineFadeSpeed;
    private final SliderSetting outlineChromaSpeed;
    private final GroupSetting optionsGroup;
    private final SliderSetting thickness;
    private final ButtonSetting persistence;
    private final ButtonSetting depthless;
    private final ButtonSetting barriers;
    private final ButtonSetting hidePlants;

    public BlockOverlay() {
        super("Block Overlay", Module.category.render);
        this.registerSetting(this.renderMode = new SliderSetting("Mode", 2, RENDER_MODES));
        this.registerSetting(this.overlayGroup = new GroupSetting("Overlay"));
        this.registerSetting(this.overlayVisible = new ButtonSetting(this.overlayGroup, "Visible", true));
        this.registerSetting(
                this.overlayColorMode = new SliderSetting(this.overlayGroup, "Color mode", 0, COLOR_MODES));
        this.registerSetting(this.overlayColor = new ColorSetting(this.overlayGroup, "Color", 0, 0, 0, 100));
        this.registerSetting(this.overlayColor2 = new ColorSetting(this.overlayGroup, "Color 2", 255, 255, 255, 100));
        this.registerSetting(
                this.overlayFadeSpeed = new SliderSetting(this.overlayGroup, "Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(
                this.overlayChromaSpeed = new SliderSetting(this.overlayGroup, "Chroma speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(this.outlineGroup = new GroupSetting("Outline"));
        this.registerSetting(this.outlineVisible = new ButtonSetting(this.outlineGroup, "Visible", true));
        this.registerSetting(
                this.outlineColorMode = new SliderSetting(this.outlineGroup, "Color mode", 0, COLOR_MODES));
        this.registerSetting(this.outlineColor = new ColorSetting(this.outlineGroup, "Color", 0, 0, 0, 255));
        this.registerSetting(this.outlineColor2 = new ColorSetting(this.outlineGroup, "Color 2", 255, 255, 255, 255));
        this.registerSetting(
                this.outlineFadeSpeed = new SliderSetting(this.outlineGroup, "Fade speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(
                this.outlineChromaSpeed = new SliderSetting(this.outlineGroup, "Chroma speed", 5.5, 1.0, 10.0, 0.5));
        this.registerSetting(this.optionsGroup = new GroupSetting("Options"));
        this.registerSetting(this.thickness = new SliderSetting(this.optionsGroup, "Thickness", 2.0, 1.0, 10.0, 0.5));
        this.registerSetting(this.persistence = new ButtonSetting(this.optionsGroup, "Persistence", false));
        this.registerSetting(this.depthless = new ButtonSetting(this.optionsGroup, "Depthless", false));
        this.registerSetting(this.barriers = new ButtonSetting(this.optionsGroup, "Barriers", false));
        this.registerSetting(this.hidePlants = new ButtonSetting(this.optionsGroup, "Hide plants", false));
    }

    @Override
    public void guiUpdate() {
        int oMode = (int) this.overlayColorMode.getInput();
        this.overlayColor2.setVisible(oMode == 1 || oMode == 2, this);
        this.overlayFadeSpeed.setVisible(oMode == 2, this);
        this.overlayChromaSpeed.setVisible(oMode == 3, this);
        int olMode = (int) this.outlineColorMode.getInput();
        this.outlineColor2.setVisible(olMode == 1 || olMode == 2, this);
        this.outlineFadeSpeed.setVisible(olMode == 2, this);
        this.outlineChromaSpeed.setVisible(olMode == 3, this);
    }

    @Override
    public String getInfo() {
        return RENDER_MODES[(int) this.renderMode.getInput()];
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onDrawBlockHighlight(ForgeEvents.DrawBlockHighlightEvent e) {
        int mode = (int) this.renderMode.getInput();
        if (mode == 0) {
            e.setCanceled(true);
        } else if (mode != 1) {
            e.setCanceled(true);
            if (Utils.nullCheck()) {
                if (this.persistence.isToggled() || !mc.player.isSpectator()) {
                    BlockPos pos = this.getFocusedBlock();
                    if (pos != null) {
                        boolean showOverlay = this.overlayVisible.isToggled();
                        boolean showOutline = this.outlineVisible.isToggled();
                        if (showOverlay || showOutline) {
                            Direction side = mode == 2 ? mc.crosshairTarget.face : null;
                            this.renderCustomBlockOverlay(pos, side, showOverlay, showOutline);
                        }
                    }
                }
            }
        }
    }

    private void renderCustomBlockOverlay(BlockPos pos, Direction side, boolean showOverlay, boolean showOutline) {
        Box box = BlockUtils.getBlockSelectionBox(pos);
        if (box != null) {
            box = box.grown(0.002, 0.002, 0.002);
            double vx = mc.getEntityRenderDispatcher().cameraX;
            double vy = mc.getEntityRenderDispatcher().cameraY;
            double vz = mc.getEntityRenderDispatcher().cameraZ;
            int overlayStart = 0;
            int overlayEnd = 0;
            int outlineStart = 0;
            int outlineEnd = 0;
            if (showOverlay) {
                overlayStart = computeStart(
                        (int) this.overlayColorMode.getInput(),
                        this.overlayColor,
                        this.overlayColor2,
                        this.overlayFadeSpeed.getInput(),
                        this.overlayChromaSpeed.getInput());
                overlayEnd = computeEnd(
                        (int) this.overlayColorMode.getInput(),
                        this.overlayColor,
                        this.overlayColor2,
                        this.overlayFadeSpeed.getInput(),
                        this.overlayChromaSpeed.getInput());
            }

            if (showOutline) {
                outlineStart = computeStart(
                        (int) this.outlineColorMode.getInput(),
                        this.outlineColor,
                        this.outlineColor2,
                        this.outlineFadeSpeed.getInput(),
                        this.outlineChromaSpeed.getInput());
                outlineEnd = computeEnd(
                        (int) this.outlineColorMode.getInput(),
                        this.outlineColor,
                        this.outlineColor2,
                        this.outlineFadeSpeed.getInput(),
                        this.outlineChromaSpeed.getInput());
            }

            GL11.glPushMatrix();
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture();
            GlStateManager.depthMask(false);
            boolean depthDisabled = this.depthless.isToggled();
            if (depthDisabled) {
                GlStateManager.disableDepthTest();
            }

            GL11.glEnable(2848);
            GL11.glHint(3154, 4354);
            if (showOutline) {
                GL11.glLineWidth((float) this.thickness.getInput());
            }

            GL11.glShadeModel(7425);

            try {
                drawOverlayGeometry(
                        mc,
                        pos,
                        box,
                        side,
                        vx,
                        vy,
                        vz,
                        overlayStart,
                        overlayEnd,
                        outlineStart,
                        outlineEnd,
                        showOverlay,
                        showOutline);
            } finally {
                GL11.glShadeModel(7424);
                GL11.glLineWidth(2.0F);
                GL11.glDisable(2848);
                if (depthDisabled) {
                    GlStateManager.enableDepthTest();
                }

                GlStateManager.depthMask(true);
                GlStateManager.enableTexture();
                GlStateManager.enableCull();
                GlStateManager.disableBlend();
                GL11.glPopMatrix();
            }
        }
    }

    public static void renderBlockOutline(
            BlockPos pos, int outlineArgbStart, int outlineArgbEnd, float lineWidth, boolean depthless) {
        Minecraft m = Minecraft.getInstance();
        if (m.world != null && pos != null) {
            Box box = BlockUtils.getBlockSelectionBox(pos);
            if (box != null) {
                box = box.grown(0.002, 0.002, 0.002);
                double vx = m.getEntityRenderDispatcher().cameraX;
                double vy = m.getEntityRenderDispatcher().cameraY;
                double vz = m.getEntityRenderDispatcher().cameraZ;
                GL11.glPushMatrix();
                GlStateManager.disableCull();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                GlStateManager.disableTexture();
                GlStateManager.depthMask(false);
                if (depthless) {
                    GlStateManager.disableDepthTest();
                }

                GL11.glEnable(2848);
                GL11.glHint(3154, 4354);
                GL11.glLineWidth(lineWidth);
                GL11.glShadeModel(7425);

                try {
                    drawOverlayGeometry(
                            m, pos, box, null, vx, vy, vz, 0, 0, outlineArgbStart, outlineArgbEnd, false, true);
                } finally {
                    GL11.glShadeModel(7424);
                    GL11.glLineWidth(2.0F);
                    GL11.glDisable(2848);
                    if (depthless) {
                        GlStateManager.enableDepthTest();
                    }

                    GlStateManager.depthMask(true);
                    GlStateManager.enableTexture();
                    GlStateManager.enableCull();
                    GlStateManager.disableBlend();
                    GL11.glPopMatrix();
                }
            }
        }
    }

    private static void drawOverlayGeometry(
            Minecraft mc,
            BlockPos pos,
            Box paddedWorldBox,
            Direction side,
            double vx,
            double vy,
            double vz,
            int overlayStart,
            int overlayEnd,
            int outlineStart,
            int outlineEnd,
            boolean showOverlay,
            boolean showOutline) {
        Box renderBox = paddedWorldBox.moved(-vx, -vy, -vz);
        BlockState state = mc.world.getBlockState(pos);
        if (state.getBlock() instanceof StairsBlock) {
            StairsUtils.drawStairs(
                    pos,
                    state,
                    paddedWorldBox,
                    side,
                    vx,
                    vy,
                    vz,
                    overlayStart,
                    overlayEnd,
                    outlineStart,
                    outlineEnd,
                    showOverlay,
                    showOutline,
                    BlockOverlay::drawFace);
        } else if (side != null) {
            drawFace(renderBox, side, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
        } else {
            for (Direction face : Direction.values()) {
                drawFace(renderBox, face, overlayStart, overlayEnd, outlineStart, outlineEnd, showOverlay, showOutline);
            }
        }
    }

    private BlockPos getFocusedBlock() {
        if (mc.crosshairTarget != null && mc.crosshairTarget.type == Type.BLOCK) {
            BlockPos pos = mc.crosshairTarget.getPos();
            if (pos == null) {
                return null;
            } else {
                Block block = mc.world.getBlockState(pos).getBlock();
                if (block == Blocks.AIR) {
                    return null;
                } else if (block == Blocks.BARRIER && !this.barriers.isToggled()) {
                    return null;
                } else {
                    return !this.hidePlants.isToggled()
                                    || !(block instanceof TallPlantBlock)
                                            && !(block instanceof FlowerBlock)
                                            && !(block instanceof DeadBushBlock)
                                            && !(block instanceof DoublePlantBlock)
                            ? pos
                            : null;
                }
            }
        } else {
            return null;
        }
    }

    private static int interpolate(int c1, int c2, double pct) {
        Color a = new Color(c1, true);
        Color b = new Color(c2, true);
        double inv = 1.0 - pct;
        return new Color(
                        (int) (a.getRed() * pct + b.getRed() * inv),
                        (int) (a.getGreen() * pct + b.getGreen() * inv),
                        (int) (a.getBlue() * pct + b.getBlue() * inv),
                        (int) (a.getAlpha() * pct + b.getAlpha() * inv))
                .getRGB();
    }

    private static int computeStart(
            int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1:
                return color1.getColor();
            case 2:
                double pct = Math.sin(System.currentTimeMillis() / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            case 3:
                int alpha = color1.getColor() >> 24 & 0xFF;
                return Utils.mergeAlpha(Utils.getChroma((long) chromaSpeed), alpha);
            default:
                return color1.getColor();
        }
    }

    private static int computeEnd(
            int colorMode, ColorSetting color1, ColorSetting color2, double fadeSpeed, double chromaSpeed) {
        switch (colorMode) {
            case 1:
                return color2.getColor();
            case 2:
                double pct = Math.sin((System.currentTimeMillis() + 500L) / (1100.0 - fadeSpeed * 100.0)) * 0.5 + 0.5;
                return interpolate(color1.getColor(), color2.getColor(), pct);
            default:
                return computeStart(colorMode, color1, color2, fadeSpeed, chromaSpeed);
        }
    }

    private static void drawFace(
            Box box, Direction face, int os, int oe, int ls, int le, boolean overlay, boolean outline) {
        Tesselator ts = Tesselator.getInstance();
        BufferBuilder wr = ts.getBuffer();
        if (overlay) {
            wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
            addFaceVertices(wr, face, box, os, oe);
            ts.end();
        }

        if (outline) {
            wr.begin(2, DefaultVertexFormat.POSITION_COLOR);
            addFaceVertices(wr, face, box, ls, le);
            ts.end();
        }
    }

    private static void v(BufferBuilder wr, double x, double y, double z, int color) {
        wr.vertex(x, y, z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .nextVertex();
    }

    private static void addFaceVertices(BufferBuilder wr, Direction face, Box box, int start, int end) {
        switch (face) {
            case UP:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                v(wr, box.maxX, box.maxY, box.minZ, start);
                v(wr, box.maxX, box.maxY, box.maxZ, end);
                v(wr, box.maxX, box.minY, box.maxZ, start);
                v(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case WEST:
                v(wr, box.minX, box.maxY, box.maxZ, start);
                v(wr, box.minX, box.maxY, box.minZ, end);
                v(wr, box.minX, box.minY, box.minZ, start);
                v(wr, box.minX, box.minY, box.maxZ, end);
        }
    }
}
