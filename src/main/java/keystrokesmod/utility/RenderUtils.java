package keystrokesmod.utility;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.platform.ClientPlatform;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.FrustumCuller;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.model.block.BakedQuad;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.client.resource.model.ModelTransformations.Type;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

public class RenderUtils implements IMinecraftInstance {
    public static int scaledWidth;
    public static int scaledWidthD;
    public static int scaledHeight;
    public static int scaledHeightD;
    public static int scaleFactor = 1;
    public static final double[] SIN_0_90 = new double[31];
    public static final double[] COS_0_90 = new double[31];
    public static final double[] SIN_90_180 = new double[31];
    public static final double[] COS_90_180 = new double[31];
    public static FrustumCuller frustum;
    private static final FloatBuffer MODELVIEW;
    private static final FloatBuffer PROJECTION;
    private static final IntBuffer VIEWPORT;
    private static final FloatBuffer SCREEN_COORDS;
    private static final int GUI_ITEM_LOGICAL_SIZE = 16;
    private static final RenderUtils.ProjectionContext GUI_ITEM_PROJECTION;
    private static final double[] GUI_ITEM_PROJECTED_POINT;
    private static final IntBuffer GUI_ITEM_SCISSOR;
    private static final int SCISSOR_PUSH_STACK_DEPTH = 4;
    private static final IntBuffer SCISSOR_PUSH_BUF;
    private static final int[][] scissorPushStack;
    private static int scissorPushDepth;
    private static final Map<String, Identifier> iconCache;

    public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade) {
        renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0, 1.0, 1.0, color, outline, shade);
    }

    public static void drawBeam(double x, double y, double z, double height, double width, int color, float alpha) {
        x -= mc.getEntityRenderDispatcher().cameraX;
        y -= mc.getEntityRenderDispatcher().cameraY;
        z -= mc.getEntityRenderDispatcher().cameraZ;
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder renderer = tessellator.getBuffer();
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        double minX = x + 0.5 - width;
        double maxX = x + 0.5 + width;
        double minZ = z + 0.5 - width;
        double maxZ = z + 0.5 + width;
        double top = y + height;
        renderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
        renderer.vertex(minX, top, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, y, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, y, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, top, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, top, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, y, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, y, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, top, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, top, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, y, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, y, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(minX, top, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, top, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, y, minZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, y, maxZ).color(red, green, blue, alpha).nextVertex();
        renderer.vertex(maxX, top, maxZ).color(red, green, blue, alpha).nextVertex();
        tessellator.end();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture();
    }

    public static void renderChest(BlockPos blockPos, int color, boolean outline, boolean shade) {
        renderBox(
                blockPos.getX() + 0.0625F,
                blockPos.getY(),
                blockPos.getZ() + 0.0625F,
                0.875,
                0.875,
                0.875,
                color,
                outline,
                shade);
    }

    public static void renderChestBatch(List<BlockPos> positions, int color, boolean outline, boolean shade) {
        renderChestBatch(positions, color, color, outline, shade);
    }

    public static void renderChestBatch(
            List<BlockPos> positions, int outlineColor, int shadeColor, boolean outline, boolean shade) {
        if (positions != null && !positions.isEmpty()) {
            double vx = mc.getEntityRenderDispatcher().cameraX;
            double vy = mc.getEntityRenderDispatcher().cameraY;
            double vz = mc.getEntityRenderDispatcher().cameraZ;
            GL11.glPushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            float outlineA = (outlineColor >> 24 & 0xFF) / 255.0F;
            float outlineR = (outlineColor >> 16 & 0xFF) / 255.0F;
            float outlineG = (outlineColor >> 8 & 0xFF) / 255.0F;
            float outlineB = (outlineColor & 0xFF) / 255.0F;
            float shadeA = (shadeColor >> 24 & 0xFF) / 255.0F;
            float shadeR = (shadeColor >> 16 & 0xFF) / 255.0F;
            float shadeG = (shadeColor >> 8 & 0xFF) / 255.0F;
            float shadeB = (shadeColor & 0xFF) / 255.0F;

            for (BlockPos blockPos : positions) {
                double xPos = blockPos.getX() + 0.0625 - vx;
                double yPos = blockPos.getY() - vy;
                double zPos = blockPos.getZ() + 0.0625 - vz;
                Box axisAlignedBB = new Box(xPos, yPos, zPos, xPos + 0.875, yPos + 0.875, zPos + 0.875);
                if (outline) {
                    GL11.glColor4f(outlineR, outlineG, outlineB, outlineA);
                    WorldRenderer.renderOutlineShape(axisAlignedBB);
                }

                if (shade) {
                    drawBoundingBox(axisAlignedBB, shadeR, shadeG, shadeB, shadeA);
                }
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }
    }

    public static void renderBlock(BlockPos blockPos, int color, double y2, boolean outline, boolean shade) {
        renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1.0, y2, 1.0, color, outline, shade);
    }

    public static void scissor(double x, double y, double width, double height) {
        double guiScale = ClickGui.getActiveRenderScale();
        x *= guiScale;
        y *= guiScale;
        width *= guiScale;
        height *= guiScale;
        Window sr = new Window(mc);
        int scale = sr.getScale();
        double screenH = sr.getHeight();
        int left = (int) Math.floor(x * scale);
        int right = (int) Math.ceil((x + width) * scale);
        int scaledWidth = Math.max(0, right - left);
        double bottomGui = y + height;
        int glBottom = (int) Math.floor((screenH - bottomGui) * scale);
        int glTop = (int) Math.ceil((screenH - y) * scale);
        int scaledHeight = Math.max(0, glTop - glBottom);
        if (scaledWidth >= 0 && scaledHeight >= 0) {
            GL11.glScissor(left, glBottom, scaledWidth, scaledHeight);
        }
    }

    public static void scissorPushGui(double x, double y, double width, double height) {
        double guiScale = ClickGui.getActiveRenderScale();
        x *= guiScale;
        y *= guiScale;
        width *= guiScale;
        height *= guiScale;
        Window sr = new Window(mc);
        int scale = sr.getScale();
        double screenH = sr.getHeight();
        int left = (int) Math.floor(x * scale);
        int right = (int) Math.ceil((x + width) * scale);
        int scaledWidth = Math.max(0, right - left);
        double bottomGui = y + height;
        int glBottom = (int) Math.floor((screenH - bottomGui) * scale);
        int glTop = (int) Math.ceil((screenH - y) * scale);
        int scaledHeight = Math.max(0, glTop - glBottom);
        boolean wasEnabled = GL11.glIsEnabled(3089);
        int[] saved = scissorPushStack[scissorPushDepth++];
        if (scissorPushDepth > 4) {
            throw new IllegalStateException("Scissor stack overflow");
        }

        if (wasEnabled) {
            ((Buffer) SCISSOR_PUSH_BUF).clear();
            GL11.glGetInteger(3088, SCISSOR_PUSH_BUF);
            saved[0] = 1;
            saved[1] = SCISSOR_PUSH_BUF.get(0);
            saved[2] = SCISSOR_PUSH_BUF.get(1);
            saved[3] = SCISSOR_PUSH_BUF.get(2);
            saved[4] = SCISSOR_PUSH_BUF.get(3);
            int ix = Math.max(saved[1], left);
            int iy = Math.max(saved[2], glBottom);
            int iw = Math.max(0, Math.min(saved[1] + saved[3], left + scaledWidth) - ix);
            int ih = Math.max(0, Math.min(saved[2] + saved[4], glBottom + scaledHeight) - iy);
            GL11.glScissor(ix, iy, iw, ih);
        } else {
            saved[0] = 0;
            GL11.glEnable(3089);
            GL11.glScissor(left, glBottom, scaledWidth, scaledHeight);
        }
    }

    public static void scissorPop() {
        int[] saved = scissorPushStack[--scissorPushDepth];
        if (saved[0] == 1) {
            GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
        } else {
            GL11.glDisable(3089);
        }
    }

    public static boolean isInViewFrustum(Entity entity) {
        return entity == null ? false : isInViewFrustum(entity.getShape()) || entity.ignoreCameraFrustum;
    }

    public static boolean isInViewFrustum(Box bb) {
        if (bb == null) {
            return false;
        }

        Entity view = mc.getCamera();
        if (view == null) {
            return true;
        }

        frustum.prepare(view.x, view.y, view.z);
        return frustum.isVisible(bb);
    }

    public static boolean isWithinDistanceSqToRenderView(Entity entity, double maxDistSq) {
        if (entity == null) {
            return false;
        }

        Entity view = mc.getCamera();
        return view == null ? false : entity.squaredDistanceTo(view) <= maxDistSq;
    }

    public static boolean isBlockPosWithinDistanceSqToView(BlockPos pos, double maxDistSq) {
        if (pos == null) {
            return false;
        }

        Entity view = mc.getCamera();
        if (view == null) {
            return false;
        }

        double dx = pos.getX() + 0.5 - view.x;
        double dy = pos.getY() + 0.5 - view.y;
        double dz = pos.getZ() + 0.5 - view.z;
        return dx * dx + dy * dy + dz * dz <= maxDistSq;
    }

    public static void drawRect(double left, double top, double right, double bottom, int color) {
        beginRect();
        rawRect(left, top, right, bottom, color);
        endRect();
    }

    public static void drawPlayerBoundingBox(Vec3d pos, int color) {
        GlStateManager.pushMatrix();
        double x = pos.x - mc.getEntityRenderDispatcher().cameraX;
        double y = pos.y - mc.getEntityRenderDispatcher().cameraY;
        double z = pos.z - mc.getEntityRenderDispatcher().cameraZ;
        Box bbox = mc.player.getShape().grown(0.1, 0.1, 0.1);
        Box axis = new Box(
                bbox.minX - mc.player.x + x,
                bbox.minY - mc.player.y + y,
                bbox.minZ - mc.player.z + z,
                bbox.maxX - mc.player.x + x,
                bbox.maxY - mc.player.y + y,
                bbox.maxZ - mc.player.z + z);
        float a = (color >> 24 & 0xFF) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(r, g, b, a);
        drawBoundingBox(axis, r, g, b, a);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GlStateManager.popMatrix();
    }

    public static void drawOutline(float x, float y, float x2, float y2, float lineWidth, int color) {
        float f5 = (color >> 24 & 0xFF) / 255.0F;
        float f6 = (color >> 16 & 0xFF) / 255.0F;
        float f7 = (color >> 8 & 0xFF) / 255.0F;
        float f8 = (color & 0xFF) / 255.0F;
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glPushMatrix();
        GL11.glColor4f(f6, f7, f8, f5);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(1);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x, y2);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x2, y);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x2, y);
        GL11.glVertex2d(x, y2);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopMatrix();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
    }

    public static void renderBox(
            double x, double y, double z, double x2, double y2, double z2, int color, boolean outline, boolean shade) {
        double xPos = x - mc.getEntityRenderDispatcher().cameraX;
        double yPos = y - mc.getEntityRenderDispatcher().cameraY;
        double zPos = z - mc.getEntityRenderDispatcher().cameraZ;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0F);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        float n8 = (color >> 24 & 0xFF) / 255.0F;
        float n9 = (color >> 16 & 0xFF) / 255.0F;
        float n10 = (color >> 8 & 0xFF) / 255.0F;
        float n11 = (color & 0xFF) / 255.0F;
        GL11.glColor4f(n9, n10, n11, n8);
        Box axisAlignedBB = new Box(xPos, yPos, zPos, xPos + x2, yPos + y2, zPos + z2);
        if (outline) {
            WorldRenderer.renderOutlineShape(axisAlignedBB);
        }

        if (shade) {
            drawBoundingBox(axisAlignedBB, n9, n10, n11);
        }

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    public static void renderBlockFaces(
            BlockPos blockPos, int color, boolean outline, boolean shade, Set<Direction> faces) {
        if (faces != null && !faces.isEmpty()) {
            double xPos = blockPos.getX() - mc.getEntityRenderDispatcher().cameraX;
            double yPos = blockPos.getY() - mc.getEntityRenderDispatcher().cameraY;
            double zPos = blockPos.getZ() - mc.getEntityRenderDispatcher().cameraZ;
            double maxX = xPos + 1.0;
            double maxY = yPos + 1.0;
            double maxZ = zPos + 1.0;
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            float outlineA = (color >> 24 & 0xFF) / 255.0F;
            float shadeA = 0.25F;
            GL11.glPushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            Tesselator ts = Tesselator.getInstance();
            BufferBuilder vb = ts.getBuffer();
            if (shade) {
                vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
                if (faces.contains(Direction.DOWN)) {
                    vb.vertex(xPos, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                }

                if (faces.contains(Direction.UP)) {
                    vb.vertex(xPos, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                }

                if (faces.contains(Direction.NORTH)) {
                    vb.vertex(xPos, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                }

                if (faces.contains(Direction.SOUTH)) {
                    vb.vertex(maxX, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                }

                if (faces.contains(Direction.WEST)) {
                    vb.vertex(xPos, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                }

                if (faces.contains(Direction.EAST)) {
                    vb.vertex(maxX, yPos, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, maxY, zPos).color(r, g, b, shadeA).nextVertex();
                    vb.vertex(maxX, yPos, zPos).color(r, g, b, shadeA).nextVertex();
                }

                ts.end();
            }

            if (outline) {
                GL11.glColor4f(r, g, b, outlineA);
                vb.begin(1, DefaultVertexFormat.POSITION);
                if (faces.contains(Direction.DOWN)) {
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                }

                if (faces.contains(Direction.UP)) {
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                }

                if (faces.contains(Direction.NORTH)) {
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                }

                if (faces.contains(Direction.SOUTH)) {
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                }

                if (faces.contains(Direction.WEST)) {
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                    vb.vertex(xPos, maxY, zPos).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, maxY, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, maxZ).nextVertex();
                    vb.vertex(xPos, yPos, zPos).nextVertex();
                }

                if (faces.contains(Direction.EAST)) {
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, zPos).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, maxY, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, maxZ).nextVertex();
                    vb.vertex(maxX, yPos, zPos).nextVertex();
                }

                ts.end();
            }

            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }
    }

    private static void drawBoxFaceVertex(BufferBuilder wr, double x, double y, double z, int color) {
        wr.vertex(x, y, z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >> 24 & 0xFF)
                .nextVertex();
    }

    private static void drawBoxFaceVertices(BufferBuilder wr, Direction face, Box box, int start, int end) {
        switch (face) {
            case UP:
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case DOWN:
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case NORTH:
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
                break;
            case SOUTH:
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
                break;
            case EAST:
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.minZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.maxY, box.maxZ, end);
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.maxX, box.minY, box.minZ, end);
                break;
            case WEST:
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.maxZ, start);
                drawBoxFaceVertex(wr, box.minX, box.maxY, box.minZ, end);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.minZ, start);
                drawBoxFaceVertex(wr, box.minX, box.minY, box.maxZ, end);
        }
    }

    public static void drawBoxFace(
            Box box, Direction face, int overlayColor, int outlineColor, boolean overlay, boolean outline) {
        Tesselator ts = Tesselator.getInstance();
        BufferBuilder wr = ts.getBuffer();
        if (overlay) {
            wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
            drawBoxFaceVertices(wr, face, box, overlayColor, overlayColor);
            ts.end();
        }

        if (outline) {
            wr.begin(2, DefaultVertexFormat.POSITION_COLOR);
            drawBoxFaceVertices(wr, face, box, outlineColor, outlineColor);
            ts.end();
        }
    }

    public static void renderBlockShape(
            BlockPos pos, BlockState state, int color, boolean outline, boolean shade, Set<Direction> visibleFaces) {
        Box box = BlockUtils.getBlockSelectionBox(pos);
        if (box != null) {
            double vx = mc.getEntityRenderDispatcher().cameraX;
            double vy = mc.getEntityRenderDispatcher().cameraY;
            double vz = mc.getEntityRenderDispatcher().cameraZ;
            int overlayColor = color & 16777215 | 1056964608;
            int outlineColor = color | 0xFF000000;
            GL11.glPushMatrix();
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            if (state.getBlock() instanceof StairsBlock) {
                StairsUtils.drawStairs(
                        pos,
                        state,
                        box,
                        null,
                        vx,
                        vy,
                        vz,
                        overlayColor,
                        outlineColor,
                        outlineColor,
                        outlineColor,
                        shade,
                        outline,
                        (b, facex, os, oe, ls, le, ov, ol) ->
                                drawBoxFace(b, facex, overlayColor, outlineColor, ov, ol));
            } else {
                Box renderBox = box.moved(-vx, -vy, -vz);

                for (Direction face : visibleFaces) {
                    drawBoxFace(renderBox, face, overlayColor, outlineColor, shade, outline);
                }
            }

            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
            GL11.glPopMatrix();
        }
    }

    public static void renderBPS(boolean b, boolean b2) {
        Window scaledResolution = new Window(mc);
        String s = "";
        int n = -1;
        if (b) {
            double t = Utils.gbps((Entity) (Freecam.freeEntity == null ? mc.player : Freecam.freeEntity), 2);
            if (t < 10.0) {
                n = Color.green.getRGB();
            } else if (t < 30.0) {
                n = Color.yellow.getRGB();
            } else if (t < 60.0) {
                n = Color.orange.getRGB();
            } else if (t < 160.0) {
                n = Color.red.getRGB();
            } else {
                n = Color.black.getRGB();
            }

            s = s + t + "bps";
        }

        if (b2) {
            double h = Utils.getHorizontalSpeed();
            if (!s.isEmpty()) {
                s = s + " ";
            }

            s = s + Utils.isWholeNumber(Utils.round(h, 3));
        }

        mc.textRenderer.draw(
                s,
                scaledResolution.getWidth() / 2 - mc.textRenderer.getWidth(s) / 2,
                scaledResolution.getHeight() / 2 + 15,
                n,
                false);
    }

    public static void renderEntity(Entity e, int type, double expand, double shift, int color, boolean damage) {
        renderEntity(e, type, expand, shift, color, damage, 0.25F);
    }

    public static void renderEntity(
            Entity e, int type, double expand, double shift, int color, boolean damage, float fillAlpha) {
        renderEntity(e, type, expand, shift, color, damage, fillAlpha, 2.0F);
    }

    /** @param lineWidth box outline width (MobESP's Box thickness, ported from MeowerClient). */
    public static void renderEntity(
            Entity e,
            int type,
            double expand,
            double shift,
            int color,
            boolean damage,
            float fillAlpha,
            float lineWidth) {
        if (e instanceof LivingEntity) {
            float partialTicks = ((IAccessorMinecraft) mc).getTimer().partialTick;
            double x = e.prevX + (e.x - e.prevX) * partialTicks - mc.getEntityRenderDispatcher().cameraX;
            double y = e.prevY + (e.y - e.prevY) * partialTicks - mc.getEntityRenderDispatcher().cameraY;
            double z = e.prevZ + (e.z - e.prevZ) * partialTicks - mc.getEntityRenderDispatcher().cameraZ;
            float d = (float) expand / 40.0F;
            if (e instanceof PlayerEntity && damage && ((PlayerEntity) e).damagedTimer != 0) {
                color = Color.RED.getRGB();
            }

            GlStateManager.pushMatrix();
            if (type == 3) {
                GL11.glTranslated(x, y - 0.2, z);
                GL11.glRotated(-mc.getEntityRenderDispatcher().cameraYaw, 0.0, 1.0, 0.0);
                GlStateManager.disableDepthTest();
                GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
                int outline = Color.black.getRGB();
                GuiElement.fill(-20, -1, -26, 75, outline);
                GuiElement.fill(20, -1, 26, 75, outline);
                GuiElement.fill(-20, -1, 21, 5, outline);
                GuiElement.fill(-20, 70, 21, 75, outline);
                if (color != 0) {
                    GuiElement.fill(-21, 0, -25, 74, color);
                    GuiElement.fill(21, 0, 25, 74, color);
                    GuiElement.fill(-21, 0, 24, 4, color);
                    GuiElement.fill(-21, 71, 25, 74, color);
                } else {
                    int st = Utils.getChroma(2L, 0L);
                    int en = Utils.getChroma(2L, 1000L);
                    dGR(-21, 0, -25, 74, st, en);
                    dGR(21, 0, 25, 74, st, en);
                    GuiElement.fill(-21, 0, 21, 4, en);
                    GuiElement.fill(-21, 71, 21, 74, st);
                }

                GlStateManager.enableDepthTest();
            } else if (type == 4) {
                LivingEntity en = (LivingEntity) e;
                double health = en.getHealth() / en.getMaxHealth();
                int barHeight = (int) (74.0 * health);
                int healthColor = health < 0.3
                        ? Color.red.getRGB()
                        : (health < 0.5
                                ? Color.orange.getRGB()
                                : (health < 0.7 ? Color.yellow.getRGB() : Color.green.getRGB()));
                GL11.glTranslated(x, y - 0.2, z);
                GL11.glRotated(-mc.getEntityRenderDispatcher().cameraYaw, 0.0, 1.0, 0.0);
                GlStateManager.disableDepthTest();
                GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
                int i = (int) (21.0 + shift * 2.0);
                GuiElement.fill(i, -1, i + 4, 75, Color.black.getRGB());
                GuiElement.fill(i + 1, barHeight, i + 3, 74, Color.darkGray.getRGB());
                GuiElement.fill(i + 1, 0, i + 3, barHeight, healthColor);
                GlStateManager.enableDepthTest();
            } else if (type == 6) {
                drawCircle(x, y, z, 0.7F, 45, 1.5F, color, color == 0);
            } else {
                if (color == 0) {
                    color = Utils.getChroma(2L, 0L);
                }

                float a = (color >> 24 & 0xFF) / 255.0F;
                float r = (color >> 16 & 0xFF) / 255.0F;
                float g = (color >> 8 & 0xFF) / 255.0F;
                float b = (color & 0xFF) / 255.0F;
                Box bbox = e.getShape().grown(0.1 + expand, 0.1 + expand, 0.1 + expand);
                Box axis = new Box(
                        bbox.minX - e.x + x,
                        bbox.minY - e.y + y,
                        bbox.minZ - e.z + z,
                        bbox.maxX - e.x + x,
                        bbox.maxY - e.y + y,
                        bbox.maxZ - e.z + z);
                GL11.glBlendFunc(770, 771);
                GL11.glEnable(3042);
                GL11.glDisable(3553);
                GL11.glDisable(2929);
                GL11.glDepthMask(false);
                GL11.glLineWidth(lineWidth);
                GL11.glColor4f(r, g, b, a);
                if (type == 1) {
                    WorldRenderer.renderOutlineShape(axis);
                } else if (type == 2) {
                    drawBoundingBox(axis, r, g, b, fillAlpha);
                }

                GL11.glEnable(3553);
                GL11.glEnable(2929);
                GL11.glDepthMask(true);
                GL11.glDisable(3042);
            }

            GlStateManager.popMatrix();
        }
    }

    public static void drawPolygon(double n, double n2, double n3, int n4, int n5) {
        if (n4 >= 3) {
            float n6 = (n5 >> 24 & 0xFF) / 255.0F;
            float n7 = (n5 >> 16 & 0xFF) / 255.0F;
            float n8 = (n5 >> 8 & 0xFF) / 255.0F;
            float n9 = (n5 & 0xFF) / 255.0F;
            Tesselator getInstance = Tesselator.getInstance();
            BufferBuilder getWorldRenderer = getInstance.getBuffer();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GL11.glColor4f(n7, n8, n9, n6);
            getWorldRenderer.begin(6, DefaultVertexFormat.POSITION);

            for (int i = 0; i < n4; i++) {
                double n10 = (Math.PI * 2) * i / n4 + Math.toRadians(180.0);
                getWorldRenderer
                        .vertex(n + Math.sin(n10) * n3, n2 + Math.cos(n10) * n3, 0.0)
                        .nextVertex();
            }

            getInstance.end();
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
        }
    }

    public static void drawOutlinedBox(Box worldBox, double viewerX, double viewerY, double viewerZ) {
        Box renderBox = worldBox.moved(-viewerX, -viewerY, -viewerZ);
        WorldRenderer.renderOutlineShape(renderBox);
    }

    public static void drawBoundingBox(Box abb, float r, float g, float b) {
        drawBoundingBox(abb, r, g, b, 0.25F);
    }

    public static void drawBoundingBox(Box abb, float r, float g, float b, float a) {
        Tesselator ts = Tesselator.getInstance();
        BufferBuilder vb = ts.getBuffer();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        ts.end();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        ts.end();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        ts.end();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        ts.end();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        ts.end();
        vb.begin(7, DefaultVertexFormat.POSITION_COLOR);
        vb.vertex(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).nextVertex();
        vb.vertex(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).nextVertex();
        ts.end();
    }

    public static void renderBlockModel(BlockState blockState, BlockPos blockPos, int color) {
        renderBlockModel(blockState, blockPos.getX(), blockPos.getY(), blockPos.getZ(), color);
    }

    public static void renderBlockModel(BlockState blockState, double x, double y, double z, int color) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderDispatcher();
        BakedModel model = dispatcher.getModel(blockState, mc.world, new BlockPos(x, y, z));
        double xPos = x - mc.getEntityRenderDispatcher().cameraX;
        double yPos = y - mc.getEntityRenderDispatcher().cameraY;
        double zPos = z - mc.getEntityRenderDispatcher().cameraZ;
        float a = (color >> 24 & 0xFF) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translated(xPos, yPos, zPos);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.disableTexture();
        GlStateManager.disableCull();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.color4f(r, g, b, a);
        renderModelColoredQuads(model, r, g, b, a);
        GlStateManager.depthMask(true);
        GlStateManager.enableDepthTest();
        GlStateManager.enableTexture();
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static void renderModelColoredQuads(BakedModel model, float r, float g, float b, float a) {
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();

        for (Direction face : Direction.values()) {
            for (BakedQuad quad : model.getQuads(face)) {
                drawColoredQuad(wr, quad, r, g, b, a, tessellator);
            }
        }

        for (BakedQuad quad : model.getQuads()) {
            drawColoredQuad(wr, quad, r, g, b, a, tessellator);
        }
    }

    private static void drawColoredQuad(
            BufferBuilder wr, BakedQuad quad, float r, float g, float b, float a, Tesselator tessellator) {
        int[] vertexData = quad.getVertices();
        int vertexCount = 4;
        int intsPerVertex = vertexData.length / 4;
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < 4; i++) {
            int baseIndex = i * intsPerVertex;
            float vx = Float.intBitsToFloat(vertexData[baseIndex]);
            float vy = Float.intBitsToFloat(vertexData[baseIndex + 1]);
            float vz = Float.intBitsToFloat(vertexData[baseIndex + 2]);
            wr.vertex(vx, vy, vz).color(r, g, b, a).nextVertex();
        }

        tessellator.end();
    }

    public static void drawTracerLine(Entity e, int color, float lineWidth, float partialTicks) {
        if (e != null && mc.getEntityRenderDispatcher() != null) {
            Entity viewEntity = mc.getCamera();
            if (viewEntity == null) {
                viewEntity = mc.player;
            }

            if (viewEntity != null) {
                double targetX = e.prevX + (e.x - e.prevX) * partialTicks - mc.getEntityRenderDispatcher().cameraX;
                double targetY = e.prevY
                        + (e.y - e.prevY) * partialTicks
                        - mc.getEntityRenderDispatcher().cameraY
                        + e.getEyeHeight()
                        + (e.isSneaking() ? -0.125 : 0.0);
                double targetZ = e.prevZ + (e.z - e.prevZ) * partialTicks - mc.getEntityRenderDispatcher().cameraZ;
                double startX = 0.0;
                double startY = viewEntity.getEyeHeight();
                double startZ = 0.0;
                if (viewEntity == mc.player && mc.options.perspective == 0) {
                    float yaw = viewEntity.yaw;
                    float pitch = viewEntity.pitch;
                    double dirX = -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                    double dirY = -Math.sin(Math.toRadians(pitch));
                    double dirZ = Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch));
                    startX = dirX;
                    startY += dirY;
                    startZ = dirZ;
                }

                float a = (color >> 24 & 0xFF) / 255.0F;
                float r = (color >> 16 & 0xFF) / 255.0F;
                float g = (color >> 8 & 0xFF) / 255.0F;
                float b = (color & 0xFF) / 255.0F;
                GL11.glPushMatrix();
                GL11.glEnable(3042);
                GL11.glBlendFunc(770, 771);
                GL11.glEnable(2848);
                GL11.glDisable(3553);
                GL11.glDisable(2929);
                GL11.glDepthMask(false);
                GL11.glLineWidth(lineWidth);
                GL11.glColor4f(r, g, b, a);
                GL11.glBegin(1);
                GL11.glVertex3d(startX, startY, startZ);
                GL11.glVertex3d(targetX, targetY, targetZ);
                GL11.glEnd();
                GL11.glLineWidth(1.0F);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glDepthMask(true);
                GL11.glEnable(2929);
                GL11.glEnable(3553);
                GL11.glDisable(2848);
                GL11.glDisable(3042);
                GL11.glPopMatrix();
            }
        }
    }

    public static void dGR(int left, int top, int right, int bottom, int startColor, int endColor) {
        gradientRect(left, top, right, bottom, startColor, endColor);
    }

    public static void db(int w, int h, int r) {
        int c = r == -1 ? -1089466352 : r;
        GuiElement.fill(0, 0, w, h, c);
    }

    public static void drawColoredString(
            String text, char lineSplit, int x, int y, long s, long shift, boolean rect, TextRenderer fontRenderer) {
        int bX = x;
        int l = 0;
        long r = 0L;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == lineSplit) {
                l++;
                x = bX;
                y += fontRenderer.fontHeight + 5;
                r = shift * l;
            } else {
                fontRenderer.draw(String.valueOf(c), x, y, Utils.getChroma(s, r), rect);
                x += fontRenderer.getWidth(c);
                if (c != ' ') {
                    r -= 90L;
                }
            }
        }
    }

    public static void drawCircle(
            double x, double y, double z, double radius, int sides, float lineWidth, int color, boolean chroma) {
        float a = (color >> 24 & 0xFF) / 255.0F;
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        mc.gameRenderer.disableLightMap();
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(2929);
        GL11.glEnable(2848);
        GL11.glDepthMask(false);
        GL11.glLineWidth(lineWidth);
        if (!chroma) {
            GL11.glColor4f(r, g, b, a);
        }

        GL11.glBegin(1);
        long d = 0L;
        long ed = 15000L / sides;
        long hed = ed / 2L;

        for (int i = 0; i < sides * 2; i++) {
            if (chroma) {
                if (i % 2 != 0) {
                    if (i == 47) {
                        d = hed;
                    }

                    d += ed;
                }

                int c = Utils.getChroma(2L, d);
                float r2 = (c >> 16 & 0xFF) / 255.0F;
                float g2 = (c >> 8 & 0xFF) / 255.0F;
                float b2 = (c & 0xFF) / 255.0F;
                GL11.glColor3f(r2, g2, b2);
            }

            double angle = (Math.PI * 2) * i / sides + Math.toRadians(180.0);
            GL11.glVertex3d(x + Math.cos(angle) * radius, y, z + Math.sin(angle) * radius);
        }

        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glDisable(2848);
        GL11.glEnable(2929);
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        mc.gameRenderer.enableLightMap();
    }

    public static void drawCaret(float x, float y, int color, double width, double length) {
        GL11.glPushMatrix();
        GL11.glEnable(2848);
        GL11.glDisable(3553);
        glColor(color);
        GL11.glLineWidth((float) width);
        float halfWidth = (float) (width / 2.0);
        float xOffset = halfWidth / 2.0F;
        float yOffset = halfWidth / 2.0F;
        GL11.glBegin(1);
        GL11.glVertex2d(x - xOffset, y + yOffset);
        GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
        GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
        GL11.glVertex2d(x + 2.0 * length - xOffset, y + yOffset);
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glPopMatrix();
    }

    public static void drawFilledCaret(double size, int color) {
        GL11.glPushAttrib(24837);

        try {
            GL11.glEnable(3042);
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            GL11.glDisable(2884);
            GL11.glEnable(2848);
            GL11.glLineWidth(1.0F);
            GL11.glColor4f(
                    (color >> 16 & 0xFF) / 255.0F,
                    (color >> 8 & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    (color >>> 24 & 0xFF) / 255.0F);
            GL11.glBegin(6);
            GL11.glVertex2d(0.0, -size);
            GL11.glVertex2d(-size, size);
            GL11.glVertex2d(0.0, size * 0.5);
            GL11.glVertex2d(size, size);
            GL11.glEnd();
            GL11.glColor4f(0.0F, 0.0F, 0.0F, 1.0F);
            GL11.glBegin(2);
            GL11.glVertex2d(0.0, -size);
            GL11.glVertex2d(-size, size);
            GL11.glVertex2d(0.0, size * 0.5);
            GL11.glVertex2d(size, size);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
        }
    }

    public static void drawTriangle(double x, double y, double size, double widthDiv, double heightDiv, int color) {
        boolean blend = GL11.glIsEnabled(3042);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glPushMatrix();
        glColor(color);
        GL11.glBegin(7);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x - size / widthDiv, y + size);
        GL11.glVertex2d(x, y + size / heightDiv);
        GL11.glVertex2d(x + size / widthDiv, y + size);
        GL11.glVertex2d(x, y);
        GL11.glEnd();
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.8F);
        GL11.glBegin(2);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x - size / widthDiv, y + size);
        GL11.glVertex2d(x, y + size / heightDiv);
        GL11.glVertex2d(x + size / widthDiv, y + size);
        GL11.glVertex2d(x, y);
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glEnable(3553);
        if (!blend) {
            GL11.glDisable(3042);
        }

        GL11.glDisable(2848);
    }

    public static void glColor(int n) {
        setColor(n);
    }

    public static void drawRoundedGradientOutlinedRectangle(
            float x, float y, float x2, float y2, float radius, int n6, int n7, int n8) {
        roundedRectBorder(x, y, x2, y2, radius, n6, n7, n8);
    }

    public static void draw2DPolygon(double x, double y, double radius, int sides, int color) {
        if (sides >= 3) {
            float a = (color >> 24 & 0xFF) / 255.0F;
            float r = (color >> 16 & 0xFF) / 255.0F;
            float g = (color >> 8 & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder worldrenderer = tessellator.getBuffer();
            GlStateManager.enableBlend();
            GlStateManager.disableTexture();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            GL11.glEnable(2848);
            GL11.glColor4f(r, g, b, a);
            double rad180 = Math.toRadians(180.0);
            worldrenderer.begin(6, DefaultVertexFormat.POSITION);

            for (int i = 0; i < sides; i++) {
                double angle = (Math.PI * 2) * i / sides + rad180;
                worldrenderer
                        .vertex(x + Math.sin(angle) * radius, y + Math.cos(angle) * radius, 0.0)
                        .nextVertex();
            }

            tessellator.end();
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
        }
    }

    public static RenderTarget createFrameBuffer(RenderTarget framebuffer) {
        return createFrameBuffer(framebuffer, false);
    }

    public static RenderTarget createFrameBuffer(RenderTarget framebuffer, boolean depth) {
        if (needsNewFramebuffer(framebuffer)) {
            if (framebuffer != null) {
                framebuffer.destroyBuffers();
            }

            return new RenderTarget(mc.width, mc.height, depth);
        } else {
            return framebuffer;
        }
    }

    public static boolean needsNewFramebuffer(RenderTarget framebuffer) {
        return framebuffer == null || framebuffer.viewWidth != mc.width || framebuffer.viewHeight != mc.height;
    }

    public static void drawFramebufferFullscreen(RenderTarget framebuffer) {
        if (framebuffer != null) {
            Window sr = new Window(mc);
            GlStateManager.bindTexture(framebuffer.colorTextureId);
            quad(0.0, 0.0, sr.getWidth(), sr.getHeight());
        }
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(3553, texture);
    }

    public static void setAlphaLimit(float limit) {
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, (float) (limit * 0.01));
    }

    public static Color interpolateColorC(Color color1, Color color2, float amount) {
        amount = Math.min(1.0F, Math.max(0.0F, amount));
        return new Color(
                interpolateInt(color1.getRed(), color2.getRed(), amount),
                interpolateInt(color1.getGreen(), color2.getGreen(), amount),
                interpolateInt(color1.getBlue(), color2.getBlue(), amount),
                interpolateInt(color1.getAlpha(), color2.getAlpha(), amount));
    }

    public static int interpolateInt(int oldValue, int newValue, double interpolationValue) {
        return interpolate(oldValue, newValue, (float) interpolationValue).intValue();
    }

    public static Double interpolate(double oldValue, double newValue, double interpolationValue) {
        return oldValue + (newValue - oldValue) * interpolationValue;
    }

    public static void resetColor() {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static Vec3d convertTo2D(int scaleFactor, double x, double y, double z) {
        GL11.glGetFloat(2982, MODELVIEW);
        GL11.glGetFloat(2983, PROJECTION);
        GL11.glGetInteger(2978, VIEWPORT);
        boolean result =
                GLU.gluProject((float) x, (float) y, (float) z, MODELVIEW, PROJECTION, VIEWPORT, SCREEN_COORDS);
        return result
                ? new Vec3d(
                        SCREEN_COORDS.get(0) / scaleFactor,
                        (Display.getHeight() - SCREEN_COORDS.get(1)) / scaleFactor,
                        SCREEN_COORDS.get(2))
                : null;
    }

    public static RenderUtils.ProjectionContext captureProjectionContext(
            RenderUtils.ProjectionContext context, int scaleFactor) {
        if (context == null) {
            context = new RenderUtils.ProjectionContext();
        }

        context.scaleFactor = scaleFactor;
        ((Buffer) context.modelView).clear();
        ((Buffer) context.projection).clear();
        ((Buffer) context.viewport).clear();
        GL11.glGetFloat(2982, context.modelView);
        GL11.glGetFloat(2983, context.projection);
        GL11.glGetInteger(2978, context.viewport);
        ((Buffer) context.modelView).rewind();
        ((Buffer) context.projection).rewind();
        ((Buffer) context.viewport).rewind();
        return context;
    }

    public static boolean projectTo2D(
            RenderUtils.ProjectionContext context, double x, double y, double z, double[] output) {
        return context == null ? false : projectTo2D(context, x, y, z, context.scaleFactor, output);
    }

    public static boolean projectTo2D(
            RenderUtils.ProjectionContext context, double x, double y, double z, int scaleFactor, double[] output) {
        if (context != null && output != null && output.length >= 3) {
            ((Buffer) context.screenCoords).clear();
            boolean result = GLU.gluProject(
                    (float) x,
                    (float) y,
                    (float) z,
                    context.modelView,
                    context.projection,
                    context.viewport,
                    context.screenCoords);
            if (!result) {
                return false;
            }

            output[0] = context.screenCoords.get(0) / scaleFactor;
            output[1] = (Display.getHeight() - context.screenCoords.get(1)) / scaleFactor;
            output[2] = context.screenCoords.get(2);
            return true;
        } else {
            return false;
        }
    }

    public static void drawRoundedRectangle(float x, float y, float x2, float y2, float radius, int color) {
        roundedRect(x, y, x2, y2, radius, color);
    }

    public static void drawRectangleGL(float x, float y, float x2, float y2, int color) {
        rect(x, y, x2, y2, color);
    }

    public static void drawRoundedGradientRect(
            float x, float y, float x2, float y2, float radius, int n6, int n7, int n8, int n9) {
        roundedRectGradient(x, y, x2, y2, radius, n6, n7, n8, n9);
    }

    public static int setAlpha(int rgb, double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            alpha = 0.5;
        }

        return withAlpha(rgb, (int) (alpha * 255.0));
    }

    public static void draw2DCircle(
            float centerX,
            float centerY,
            float radius,
            int segments,
            float lineWidth,
            float r,
            float g,
            float b,
            float a) {
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glEnable(2884);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glColor4f(r, g, b, a);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(2);

        for (int i = 0; i <= segments; i++) {
            double theta = (Math.PI * 2) * i / segments;
            float x = (float) (radius * Math.cos(theta)) + centerX;
            float y = (float) (radius * Math.sin(theta)) + centerY;
            GL11.glVertex2f(x, y);
        }

        GL11.glEnd();
        GL11.glDisable(3042);
        GL11.glDisable(2884);
        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
        GL11.glPopMatrix();
    }

    public static void draw2DCircleArc(
            float centerX, float centerY, float radius, float startAngle, float endAngle, float lineWidth, int color) {
        float r = (color >> 16 & 0xFF) / 255.0F;
        float g = (color >> 8 & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;
        float a = (color >> 24 & 0xFF) / 255.0F;
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glEnable(2884);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glColor4f(r, g, b, a);
        GL11.glLineWidth(lineWidth);
        GL11.glBegin(3);

        for (float angle = startAngle; angle <= endAngle; angle++) {
            double theta = Math.toRadians(angle + 180.0F);
            float x = (float) (radius * Math.cos(theta)) + centerX;
            float y = (float) (radius * Math.sin(theta)) + centerY;
            GL11.glVertex2f(x, y);
        }

        GL11.glEnd();
        GL11.glDisable(3042);
        GL11.glDisable(2884);
        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glLineWidth(1.0F);
        GL11.glPopMatrix();
    }

    public static void drawHorizontalGradientRect(
            float left, float top, float right, float bottom, int leftColor, int rightColor) {
        float la = (leftColor >> 24 & 0xFF) / 255.0F;
        float lr = (leftColor >> 16 & 0xFF) / 255.0F;
        float lg = (leftColor >> 8 & 0xFF) / 255.0F;
        float lb = (leftColor & 0xFF) / 255.0F;
        float ra = (rightColor >> 24 & 0xFF) / 255.0F;
        float rr = (rightColor >> 16 & 0xFF) / 255.0F;
        float rg = (rightColor >> 8 & 0xFF) / 255.0F;
        float rb = (rightColor & 0xFF) / 255.0F;
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
        wr.vertex(left, bottom, 0.0).color(lr, lg, lb, la).nextVertex();
        wr.vertex(right, bottom, 0.0).color(rr, rg, rb, ra).nextVertex();
        wr.vertex(right, top, 0.0).color(rr, rg, rb, ra).nextVertex();
        wr.vertex(left, top, 0.0).color(lr, lg, lb, la).nextVertex();
        tessellator.end();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
    }

    public static void drawVerticalGradientRect(
            float left, float top, float right, float bottom, int topColor, int bottomColor) {
        gradientRect(left, top, right, bottom, bottomColor, topColor);
    }

    public static void renderItemAndEffectIntoGui3D(ItemStack stack, int xPos, int yPos) {
        if (stack != null) {
            if (!renderGuiItemInScreenSpace(stack, xPos, yPos)) {
                renderGuiItemDirectFallback(stack, xPos, yPos);
            }
        }
    }

    private static void renderGuiItemDirectFallback(ItemStack stack, int xPos, int yPos) {
        float previousBrightnessX = ClientPlatform.lastBrightnessX();
        float previousBrightnessY = ClientPlatform.lastBrightnessY();
        GlStateManager.pushMatrix();

        try {
            prepareGuiItemRenderState();
            GlStateManager.depthMask(true);
            GlStateManager.clear(256);
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, 240.0F);
            GlStateManager.pushMatrix();
            boolean normalizeEnabled = GL11.glIsEnabled(2977);

            try {
                GlStateManager.scalef(1.0F, 1.0F, -0.01F);
                GlStateManager.enableNormalize();
                Lighting.turnOnGui();
                mc.getItemRenderer().zOffset = -150.0F;
                mc.getItemRenderer().renderGuiItem(stack, xPos, yPos);
            } finally {
                mc.getItemRenderer().zOffset = 0.0F;
                if (!normalizeEnabled) {
                    GlStateManager.disableNormalize();
                }

                GlStateManager.popMatrix();
            }
        } finally {
            Lighting.turnOff();
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, previousBrightnessX, previousBrightnessY);
            prepareGuiTextureRenderState();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
        }
    }

    private static boolean projectGuiItemPoint(double x, double y) {
        ((Buffer) GUI_ITEM_PROJECTION.modelView).rewind();
        ((Buffer) GUI_ITEM_PROJECTION.projection).rewind();
        ((Buffer) GUI_ITEM_PROJECTION.viewport).rewind();
        ((Buffer) GUI_ITEM_PROJECTION.screenCoords).clear();
        if (!GLU.gluProject(
                (float) x,
                (float) y,
                0.0F,
                GUI_ITEM_PROJECTION.modelView,
                GUI_ITEM_PROJECTION.projection,
                GUI_ITEM_PROJECTION.viewport,
                GUI_ITEM_PROJECTION.screenCoords)) {
            return false;
        }

        GUI_ITEM_PROJECTED_POINT[0] = GUI_ITEM_PROJECTION.screenCoords.get(0) - GUI_ITEM_PROJECTION.viewport.get(0);
        GUI_ITEM_PROJECTED_POINT[1] = GUI_ITEM_PROJECTION.viewport.get(3)
                - (GUI_ITEM_PROJECTION.screenCoords.get(1) - GUI_ITEM_PROJECTION.viewport.get(1));
        GUI_ITEM_PROJECTED_POINT[2] = GUI_ITEM_PROJECTION.screenCoords.get(2);
        return GUI_ITEM_PROJECTED_POINT[2] >= 0.0 && GUI_ITEM_PROJECTED_POINT[2] <= 1.0;
    }

    private static boolean renderGuiItemInScreenSpace(ItemStack stack, int xPos, int yPos) {
        captureProjectionContext(GUI_ITEM_PROJECTION, 1);
        int viewportWidth = GUI_ITEM_PROJECTION.viewport.get(2);
        int viewportHeight = GUI_ITEM_PROJECTION.viewport.get(3);
        if (viewportWidth > 0 && viewportHeight > 0) {
            if (!projectGuiItemPoint(xPos + 8.0, yPos + 8.0)) {
                return false;
            }

            double centerX = GUI_ITEM_PROJECTED_POINT[0];
            double centerY = GUI_ITEM_PROJECTED_POINT[1];
            if (!projectGuiItemPoint(xPos + 16, yPos + 8.0)) {
                return false;
            }

            double halfWidth = Math.hypot(GUI_ITEM_PROJECTED_POINT[0] - centerX, GUI_ITEM_PROJECTED_POINT[1] - centerY);
            if (!projectGuiItemPoint(xPos + 8.0, yPos + 16)) {
                return false;
            }

            double halfHeight =
                    Math.hypot(GUI_ITEM_PROJECTED_POINT[0] - centerX, GUI_ITEM_PROJECTED_POINT[1] - centerY);
            double renderScale = (halfWidth + halfHeight) / 16.0;
            if (Double.isFinite(renderScale) && !(renderScale <= 0.0)) {
                double renderPixelSize = Math.max(1.0, Math.round(16.0 * renderScale));
                renderScale = renderPixelSize / 16.0;
                double screenX = Math.round(centerX - renderPixelSize * 0.5);
                double screenY = Math.round(centerY - renderPixelSize * 0.5);
                int previousMatrixMode = GL11.glGetInteger(2976);
                int previousProgram = GLX.usePostProcess ? GL11.glGetInteger(35725) : 0;
                float previousBrightnessX = ClientPlatform.lastBrightnessX();
                float previousBrightnessY = ClientPlatform.lastBrightnessY();
                float previousZLevel = mc.getItemRenderer().zOffset;
                boolean fogEnabled = GL11.glIsEnabled(2912);
                boolean scissorEnabled = GL11.glIsEnabled(3089);
                boolean projectionPushed = false;
                boolean modelViewPushed = false;
                if (scissorEnabled) {
                    ((Buffer) GUI_ITEM_SCISSOR).clear();
                    GL11.glGetInteger(3088, GUI_ITEM_SCISSOR);
                }

                try {
                    if (GLX.usePostProcess) {
                        GLX.useProgram(0);
                    }

                    GL11.glDisable(3089);
                    GlStateManager.disableFog();
                    prepareGuiItemRenderState();
                    GlStateManager.depthMask(true);
                    GlStateManager.clear(256);
                    GlStateManager.matrixMode(5889);
                    GlStateManager.pushMatrix();
                    projectionPushed = true;
                    GlStateManager.loadIdentity();
                    GlStateManager.ortho(
                            0.0, viewportWidth / renderScale, viewportHeight / renderScale, 0.0, 1000.0, 3000.0);
                    GlStateManager.matrixMode(5888);
                    GlStateManager.pushMatrix();
                    modelViewPushed = true;
                    GlStateManager.loadIdentity();
                    GlStateManager.translatef(0.0F, 0.0F, -2000.0F);
                    Lighting.turnOnGui();
                    GLX.multiTexCoord2f(GLX.GL_TEXTURE1, 240.0F, 240.0F);
                    GlStateManager.translatef((float) (screenX / renderScale), (float) (screenY / renderScale), 0.0F);
                    mc.getItemRenderer().zOffset = 0.0F;
                    mc.getItemRenderer().renderGuiItem(stack, 0, 0);
                    return true;
                } finally {
                    mc.getItemRenderer().zOffset = previousZLevel;
                    Lighting.turnOff();
                    GLX.multiTexCoord2f(GLX.GL_TEXTURE1, previousBrightnessX, previousBrightnessY);
                    if (modelViewPushed) {
                        GlStateManager.matrixMode(5888);
                        GlStateManager.popMatrix();
                    }

                    if (projectionPushed) {
                        GlStateManager.matrixMode(5889);
                        GlStateManager.popMatrix();
                    }

                    GlStateManager.matrixMode(previousMatrixMode);
                    if (GLX.usePostProcess) {
                        GLX.useProgram(previousProgram);
                    }

                    if (fogEnabled) {
                        GlStateManager.enableFog();
                    } else {
                        GlStateManager.disableFog();
                    }

                    if (scissorEnabled) {
                        GL11.glEnable(3089);
                        GL11.glScissor(
                                GUI_ITEM_SCISSOR.get(0),
                                GUI_ITEM_SCISSOR.get(1),
                                GUI_ITEM_SCISSOR.get(2),
                                GUI_ITEM_SCISSOR.get(3));
                    } else {
                        GL11.glDisable(3089);
                    }

                    prepareGuiTextureRenderState();
                    GlStateManager.disableBlend();
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public static int getDurabilityColor(float ratio) {
        if (ratio > 0.6F) {
            return 65280;
        } else {
            return ratio > 0.3F ? 16776960 : 16711680;
        }
    }

    public static void drawDurabilityBar(int xPos, int yPos, float durabilityRatio) {
        int barWidth = (int) (durabilityRatio * 13.0F);
        int barColor = getDurabilityColor(durabilityRatio);
        GlStateManager.disableTexture();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder wr = tess.getBuffer();
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
        wr.vertex(xPos + 2, yPos + 15, 0.0).color(0.0F, 0.0F, 0.0F, 1.0F).nextVertex();
        wr.vertex(xPos + 2, yPos + 16, 0.0).color(0.0F, 0.0F, 0.0F, 1.0F).nextVertex();
        wr.vertex(xPos + 15, yPos + 16, 0.0).color(0.0F, 0.0F, 0.0F, 1.0F).nextVertex();
        wr.vertex(xPos + 15, yPos + 15, 0.0).color(0.0F, 0.0F, 0.0F, 1.0F).nextVertex();
        tess.end();
        float r = (barColor >> 16 & 0xFF) / 255.0F;
        float g = (barColor >> 8 & 0xFF) / 255.0F;
        float b = (barColor & 0xFF) / 255.0F;
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
        wr.vertex(xPos + 2, yPos + 15, 0.0).color(r, g, b, 1.0F).nextVertex();
        wr.vertex(xPos + 2, yPos + 16, 0.0).color(r, g, b, 1.0F).nextVertex();
        wr.vertex(xPos + 2 + barWidth, yPos + 16, 0.0).color(r, g, b, 1.0F).nextVertex();
        wr.vertex(xPos + 2 + barWidth, yPos + 15, 0.0).color(r, g, b, 1.0F).nextVertex();
        tess.end();
        GlStateManager.enableTexture();
    }

    public static int getEnchantColor(int level) {
        switch (level) {
            case 1:
                return 16777215;
            case 2:
                return 5636095;
            case 3:
                return 43690;
            case 4:
                return 11141290;
            case 5:
                return 16755200;
            case 6:
            case 7:
            case 8:
            case 9:
            default:
                return level > 5 ? 16733695 : 16777215;
            case 10:
                return 16733695;
        }
    }

    public static int drawEnchantWithColor(TextRenderer fr, String letter, int level, int x, int y) {
        int letterWidth = fr.drawWithShadow(letter, x, y, 16777215);
        fr.drawWithShadow(String.valueOf(level), letterWidth, y, getEnchantColor(level));
        return letterWidth;
    }

    public static void prepareGuiTextureRenderState() {
        GlStateManager.disableLighting();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(false);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void prepareGuiItemRenderState() {
        GlStateManager.disableLighting();
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static String getEnchantmentAbbreviated(int id) {
        switch (id) {
            case 0:
                return "pt";
            case 1:
                return "frp";
            case 2:
                return "ff";
            case 3:
                return "blp";
            case 4:
                return "prp";
            case 5:
                return "thr";
            case 6:
                return "res";
            case 7:
                return "aa";
            case 8:
            case 9:
            case 10:
            case 11:
            case 12:
            case 13:
            case 14:
            case 15:
            case 22:
            case 23:
            case 24:
            case 25:
            case 26:
            case 27:
            case 28:
            case 29:
            case 30:
            case 31:
            case 36:
            case 37:
            case 38:
            case 39:
            case 40:
            case 41:
            case 42:
            case 43:
            case 44:
            case 45:
            case 46:
            case 47:
            default:
                return null;
            case 16:
                return "sh";
            case 17:
                return "smt";
            case 18:
                return "ban";
            case 19:
                return "kb";
            case 20:
                return "fa";
            case 21:
                return "lot";
            case 32:
                return "eff";
            case 33:
                return "sil";
            case 34:
                return "ub";
            case 35:
                return "for";
            case 48:
                return "pow";
            case 49:
                return "pun";
            case 50:
                return "flm";
            case 51:
                return "inf";
        }
    }

    public static Identifier buildWhiteMaskedTexture(String resourcePath, String registryName, Identifier fallback) {
        try {
            InputStream stream = Feral.class.getResourceAsStream(resourcePath);

            Identifier var14;
            label67:
            {
                Identifier var15;
                try {
                    if (stream == null) {
                        var14 = fallback;
                        break label67;
                    }

                    BufferedImage src = ImageIO.read(stream);
                    int w = src.getWidth();
                    int h = src.getHeight();
                    BufferedImage dst = new BufferedImage(w, h, 2);

                    for (int py = 0; py < h; py++) {
                        for (int px = 0; px < w; px++) {
                            int alpha = src.getRGB(px, py) >>> 24 & 0xFF;
                            if (alpha > 0) {
                                dst.setRGB(px, py, alpha << 24 | 16777215);
                            }
                        }
                    }

                    var15 = mc.getTextureManager().register(registryName, new DynamicTexture(dst));
                } catch (Throwable var12) {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (Throwable var11) {
                            var12.addSuppressed(var11);
                        }
                    }

                    throw var12;
                }

                if (stream != null) {
                    stream.close();
                }

                return var15;
            }

            if (stream != null) {
                stream.close();
            }

            return var14;
        } catch (Exception e) {
            e.printStackTrace();
            return fallback;
        }
    }

    public static Identifier getIcon(String resourcePath) {
        Identifier cached = iconCache.get(resourcePath);
        if (cached != null) {
            return cached;
        }

        String registryName = "feral_icon_" + resourcePath.hashCode();
        Identifier icon = buildWhiteMaskedTexture(resourcePath, registryName, null);
        if (icon != null) {
            iconCache.put(resourcePath, icon);
        }

        return icon;
    }

    public static void drawIcon(Identifier texture, float x, float y, int size, int argbColor) {
        if (texture != null) {
            boolean depthEnabled = GL11.glIsEnabled(2929);
            boolean blendEnabled = GL11.glIsEnabled(3042);
            boolean depthMask = GL11.glGetBoolean(2930);
            prepareGuiTextureRenderState();
            mc.getTextureManager().bind(texture);
            float a = (argbColor >>> 24 & 0xFF) / 255.0F;
            float r = (argbColor >> 16 & 0xFF) / 255.0F;
            float g = (argbColor >> 8 & 0xFF) / 255.0F;
            float b = (argbColor & 0xFF) / 255.0F;
            GlStateManager.color4f(r, g, b, a);
            GL11.glPushMatrix();
            GL11.glTranslatef(x, y, 0.0F);
            GuiElement.drawTexture(0, 0, 0.0F, 0.0F, size, size, size, size);
            GL11.glPopMatrix();
            restoreGuiRenderState(depthEnabled, blendEnabled, depthMask);
        }
    }

    public static void restoreGuiRenderState(boolean depthEnabled, boolean blendEnabled, boolean depthMask) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (blendEnabled) {
            GlStateManager.enableBlend();
        } else {
            GlStateManager.disableBlend();
        }

        if (depthEnabled) {
            GlStateManager.enableDepthTest();
        } else {
            GlStateManager.disableDepthTest();
        }

        GlStateManager.depthMask(depthMask);
    }

    public static void updateResolution() {
        Window sr = new Window(mc);
        scaledWidth = sr.getWidth();
        scaledWidthD = (int) sr.getScaledWidth();
        scaledHeight = sr.getHeight();
        scaledHeightD = (int) sr.getScaledHeight();
        scaleFactor = sr.getScale();
    }

    public static int rainbow(long speed, long... offset) {
        long l2 = System.currentTimeMillis() + (offset.length > 0 ? offset[0] : 0L);
        return Color.getHSBColor((float) (l2 % (15000L / speed)) / (15000.0F / (float) speed), 0.75F, 1.0F)
                .getRGB();
    }

    public static int blend(Color color, Color color2, double d) {
        int n = (int) (d * 255.0);
        int n2 = 255 - n;
        int r = color.getRed() * n + color2.getRed() * n2 >> 8;
        int g = color.getGreen() * n + color2.getGreen() * n2 >> 8;
        int b = color.getBlue() * n + color2.getBlue() * n2 >> 8;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static int withAlpha(int rgb, int alpha) {
        return rgb & 16777215 | alpha << 24;
    }

    public static int clampAlpha(int n) {
        if (n > 255) {
            return 255;
        } else {
            return n < 4 ? 4 : n;
        }
    }

    public static String dots(long period) {
        StringBuilder sb = new StringBuilder();
        int n = (int) (System.currentTimeMillis() / period % 4L);

        for (int i = 0; i < n; i++) {
            sb.append('.');
        }

        return sb.toString();
    }

    public static void setColor(int n) {
        float a = (n >> 24 & 0xFF) / 255.0F;
        float r = (n >> 16 & 0xFF) / 255.0F;
        float g = (n >> 8 & 0xFF) / 255.0F;
        float b = (n & 0xFF) / 255.0F;
        GlStateManager.color4f(r, g, b, a);
    }

    public static void roundedRect(float x, float y, float x2, float y2, float r, int color) {
        float var11;
        float var12;
        float var13;
        float var14;
        r = Math.min(
                r,
                Math.min(
                        ((var13 = x2 * 2.0F) - (var11 = x * 2.0F)) / 2.0F,
                        ((var14 = y2 * 2.0F) - (var12 = y * 2.0F)) / 2.0F));
        float f6 = var11 + r;
        float f7 = var12 + r;
        float f8 = var13 - r;
        float f9 = var14 - r;
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glBegin(9);
        setColor(color);

        for (int n = 0; n <= 30; n++) {
            GL11.glVertex2d(f6 + SIN_0_90[n] * -r, f7 + COS_0_90[n] * -r);
        }

        for (int var16 = 0; var16 <= 30; var16++) {
            GL11.glVertex2d(f6 + SIN_90_180[var16] * -r, f9 + COS_90_180[var16] * -r);
        }

        for (int var17 = 0; var17 <= 30; var17++) {
            GL11.glVertex2d(f8 + SIN_0_90[var17] * r, f9 + COS_0_90[var17] * r);
        }

        for (int var18 = 0; var18 <= 30; var18++) {
            GL11.glVertex2d(f8 + SIN_90_180[var18] * r, f7 + COS_90_180[var18] * r);
        }

        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        resetColor();
    }

    public static void rect(float x, float y, float x2, float y2, int color) {
        beginRect();
        rawRect(x, y, x2, y2, color);
        endRect();
    }

    public static void beginRect() {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
    }

    public static void rawRect(float f, float f2, float f3, float f4, int n) {
        rawRect((double) f, (double) f2, (double) f3, (double) f4, n);
    }

    public static void rawRect(double f, double f2, double f3, double f4, int n) {
        if (f < f3) {
            double t = f;
            f = f3;
            f3 = t;
        }

        if (f2 < f4) {
            double t = f2;
            f2 = f4;
            f4 = t;
        }

        float a = (n >>> 24) / 255.0F;
        float r = (n >> 16 & 0xFF) / 255.0F;
        float g = (n >> 8 & 0xFF) / 255.0F;
        float b = (n & 0xFF) / 255.0F;
        GlStateManager.color4f(r, g, b, a);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormat.POSITION);
        wr.vertex(f, f4, 0.0).nextVertex();
        wr.vertex(f3, f4, 0.0).nextVertex();
        wr.vertex(f3, f2, 0.0).nextVertex();
        wr.vertex(f, f2, 0.0).nextVertex();
        tessellator.end();
    }

    public static void endRect() {
        resetColor();
        GlStateManager.enableTexture();
        GlStateManager.disableBlend();
    }

    public static void quad(double x, double y, double w, double h) {
        GL11.glBegin(7);
        GL11.glTexCoord2f(0.0F, 0.0F);
        GL11.glVertex2d(x, y + h);
        GL11.glTexCoord2f(1.0F, 0.0F);
        GL11.glVertex2d(x + w, y + h);
        GL11.glTexCoord2f(1.0F, 1.0F);
        GL11.glVertex2d(x + w, y);
        GL11.glTexCoord2f(0.0F, 1.0F);
        GL11.glVertex2d(x, y);
        GL11.glEnd();
    }

    public static void screenQuad() {
        quad(0.0, 0.0, scaledWidth, scaledHeight);
    }

    public static void drawItem(ItemStack stack, float x, float y) {
        if (stack != null && stack.getItem() != null) {
            Lighting.turnOnGui();
            ItemRenderer renderItem = mc.getItemRenderer();
            renderItem.zOffset = -100.0F;
            BakedModel model = renderItem.getModelShaper().getModel(stack);
            GlStateManager.pushMatrix();
            mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            mc.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            resetColor();
            GlStateManager.translatef(x, y, 100.0F + renderItem.zOffset);
            GlStateManager.translatef(8.0F, 8.0F, 0.0F);
            GlStateManager.scalef(1.0F, 1.0F, -1.0F);
            GlStateManager.scalef(0.5F, 0.5F, 0.5F);
            if (model.isGui3d()) {
                GlStateManager.scalef(40.0F, 40.0F, 40.0F);
                GlStateManager.rotatef(210.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(-135.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.enableLighting();
            } else {
                GlStateManager.scalef(64.0F, 64.0F, 64.0F);
                GlStateManager.rotatef(180.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.disableLighting();
            }

            model = ClientPlatform.ForgeHooksClient.handleCameraTransforms(model, Type.GUI);
            renderItem.renderItem(stack, model);
            GlStateManager.disableBlend();
            GlStateManager.disableRescaleNormal();
            GlStateManager.disableLighting();
            GlStateManager.popMatrix();
            mc.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
            mc.getTextureManager().get(TextureAtlas.BLOCKS_LOCATION).popFilter();
            renderItem.zOffset = 0.0F;
            Lighting.turnOff();
        }
    }

    public static int blendInt(int n, int n2, double d) {
        int n3 = (int) (d * 255.0);
        int n4 = 255 - n3;
        int a = (n >> 24 & 0xFF) * n3 + (n2 >> 24 & 0xFF) * n4 >> 8;
        int r = (n >> 16 & 0xFF) * n3 + (n2 >> 16 & 0xFF) * n4 >> 8;
        int g = (n >> 8 & 0xFF) * n3 + (n2 >> 8 & 0xFF) * n4 >> 8;
        int b = (n & 0xFF) * n3 + (n2 & 0xFF) * n4 >> 8;
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static void gradientRect(float x, float y, float x2, float y2, int bottomColor, int topColor) {
        if (x < x2) {
            float t = x;
            x = x2;
            x2 = t;
        }

        if (y < y2) {
            float t = y;
            y = y2;
            y2 = t;
        }

        float a = (bottomColor >> 24 & 0xFF) / 255.0F;
        float r = (bottomColor >> 16 & 0xFF) / 255.0F;
        float g = (bottomColor >> 8 & 0xFF) / 255.0F;
        float b = (bottomColor & 0xFF) / 255.0F;
        float a2 = (topColor >> 24 & 0xFF) / 255.0F;
        float r2 = (topColor >> 16 & 0xFF) / 255.0F;
        float g2 = (topColor >> 8 & 0xFF) / 255.0F;
        float b2 = (topColor & 0xFF) / 255.0F;
        GlStateManager.disableTexture();
        GlStateManager.enableBlend();
        GlStateManager.disableAlphaTest();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder wr = tessellator.getBuffer();
        wr.begin(7, DefaultVertexFormat.POSITION_COLOR);
        wr.vertex(x2, y, 0.0).color(r, g, b, a).nextVertex();
        wr.vertex(x, y, 0.0).color(r, g, b, a).nextVertex();
        wr.vertex(x, y2, 0.0).color(r2, g2, b2, a2).nextVertex();
        wr.vertex(x2, y2, 0.0).color(r2, g2, b2, a2).nextVertex();
        tessellator.end();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlphaTest();
        GlStateManager.enableTexture();
    }

    public static void roundedRectBorder(float f, float f2, float f3, float f4, float f5, int n, int n2, int n3) {
        f *= 2.0F;
        f2 *= 2.0F;
        f3 *= 2.0F;
        f4 *= 2.0F;
        GL11.glPushAttrib(1);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glBegin(9);
        setColor(n);
        float f6 = f + f5;
        float f7 = f2 + f5;
        float f8 = f3 - f5;
        float f9 = f4 - f5;

        for (int i = 0; i <= 30; i++) {
            GL11.glVertex2d(f6 + SIN_0_90[i] * -f5, f7 + COS_0_90[i] * -f5);
        }

        for (int var17 = 0; var17 <= 30; var17++) {
            GL11.glVertex2d(f6 + SIN_90_180[var17] * -f5, f9 + COS_90_180[var17] * -f5);
        }

        for (int var18 = 0; var18 <= 30; var18++) {
            GL11.glVertex2d(f8 + SIN_0_90[var18] * f5, f9 + COS_0_90[var18] * f5);
        }

        for (int var19 = 0; var19 <= 30; var19++) {
            GL11.glVertex2d(f8 + SIN_90_180[var19] * f5, f7 + COS_90_180[var19] * f5);
        }

        GL11.glEnd();
        GL11.glPushMatrix();
        GL11.glShadeModel(7425);
        GL11.glLineWidth(2.0F);
        GL11.glBegin(2);
        if (n2 != 0) {
            setColor(n2);
        }

        for (int var20 = 0; var20 <= 30; var20++) {
            GL11.glVertex2d(f6 + SIN_0_90[var20] * -f5, f7 + COS_0_90[var20] * -f5);
        }

        for (int var21 = 0; var21 <= 30; var21++) {
            GL11.glVertex2d(f6 + SIN_90_180[var21] * -f5, f9 + COS_90_180[var21] * -f5);
        }

        if (n3 != 0) {
            setColor(n3);
        }

        for (int var22 = 0; var22 <= 30; var22++) {
            GL11.glVertex2d(f8 + SIN_0_90[var22] * f5, f9 + COS_0_90[var22] * f5);
        }

        for (int var23 = 0; var23 <= 30; var23++) {
            GL11.glVertex2d(f8 + SIN_90_180[var23] * f5, f7 + COS_90_180[var23] * f5);
        }

        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glLineWidth(1.0F);
        GL11.glShadeModel(7424);
        resetColor();
    }

    public static void roundedRectGradient(
            float f, float f2, float f3, float f4, float f5, int n, int n2, int n3, int n4) {
        float var14;
        float var15;
        float var16;
        float var17;
        f5 = Math.min(
                f5,
                Math.min(
                        ((var16 = f3 * 2.0F) - (var14 = f * 2.0F)) / 2.0F,
                        ((var17 = f4 * 2.0F) - (var15 = f2 * 2.0F)) / 2.0F));
        float f6 = var14 + f5;
        float f7 = var15 + f5;
        float f8 = var16 - f5;
        float f9 = var17 - f5;
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glShadeModel(7425);
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glShadeModel(7425);
        GL11.glBegin(9);
        setColor(n);

        for (int i = 0; i <= 30; i++) {
            GL11.glVertex2d(f6 + SIN_0_90[i] * -f5, f7 + COS_0_90[i] * -f5);
        }

        setColor(n2);

        for (int var19 = 0; var19 <= 30; var19++) {
            GL11.glVertex2d(f6 + SIN_90_180[var19] * -f5, f9 + COS_90_180[var19] * -f5);
        }

        setColor(n3);

        for (int var20 = 0; var20 <= 30; var20++) {
            GL11.glVertex2d(f8 + SIN_0_90[var20] * f5, f9 + COS_0_90[var20] * f5);
        }

        setColor(n4);

        for (int var21 = 0; var21 <= 30; var21++) {
            GL11.glVertex2d(f8 + SIN_90_180[var21] * f5, f7 + COS_90_180[var21] * f5);
        }

        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glShadeModel(7424);
        resetColor();
    }

    public static void shadowText(String s, float x, float y, int color) {
        GlStateManager.enableBlend();
        mc.textRenderer.draw(s, x, y, color, true);
        GlStateManager.disableBlend();
        resetColor();
    }

    static {
        for (int n = 0; n <= 90; n += 3) {
            double d = n * (float) (Math.PI / 180.0);
            SIN_0_90[n / 3] = Math.sin(d);
            COS_0_90[n / 3] = Math.cos(d);
        }

        for (int n = 90; n <= 180; n += 3) {
            double d = n * (float) (Math.PI / 180.0);
            SIN_90_180[(n - 90) / 3] = Math.sin(d);
            COS_90_180[(n - 90) / 3] = Math.cos(d);
        }

        frustum = new FrustumCuller();
        MODELVIEW = BufferUtils.createFloatBuffer(16);
        PROJECTION = BufferUtils.createFloatBuffer(16);
        VIEWPORT = BufferUtils.createIntBuffer(16);
        SCREEN_COORDS = BufferUtils.createFloatBuffer(3);
        GUI_ITEM_PROJECTION = new RenderUtils.ProjectionContext();
        GUI_ITEM_PROJECTED_POINT = new double[3];
        GUI_ITEM_SCISSOR = BufferUtils.createIntBuffer(16);
        SCISSOR_PUSH_BUF = BufferUtils.createIntBuffer(16);
        scissorPushStack = new int[4][5];
        scissorPushDepth = 0;
        iconCache = new HashMap<>();
    }

    public static final class ProjectionContext {
        private int scaleFactor;
        private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);
    }
}
