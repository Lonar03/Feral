package keystrokesmod.clickgui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import keystrokesmod.module.Module;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;

/** Original GUI artwork, with filtering limited to these draws. */
public final class ClassicIcons {
    private static final Map<Module.category, ItemStack[]> ITEMS = new EnumMap<>(Module.category.class);

    private ClassicIcons() {}

    public static void drawCategory(Module.category category, float x, float y, boolean active) {
        ItemStack stack = ITEMS.computeIfAbsent(category, ClassicIcons::categoryStacks)[active ? 1 : 0];
        drawItem(stack, x, y, 0.55f);
    }

    public static void drawItem(ItemStack stack, float x, float y, float scale) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer renderer = mc.getItemRenderer();
        TextureManager textures = mc.getTextureManager();
        int textureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        float z = renderer.zOffset;
        GlStateManager.pushMatrix();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager.bindTexture(0);
        textures.bind(TextureAtlas.BLOCKS_LOCATION);
        Texture atlas = textures.get(TextureAtlas.BLOCKS_LOCATION);
        atlas.pushFilter(true, false);
        try {
            GlStateManager.translatef(x, y, 0f);
            GlStateManager.scalef(scale, scale, scale);
            Lighting.turnOnGui();
            GlStateManager.enableTexture();
            // Classic panels also use raw GL, so the cached texture-enable flag can be stale.
            if (!GL11.glIsEnabled(GL11.GL_TEXTURE_2D)) {
                GL11.glEnable(GL11.GL_TEXTURE_2D);
            }
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.enableRescaleNormal();
            GlStateManager.color4f(1f, 1f, 1f, 1f);
            renderer.zOffset = z + 50f;
            // Use the same vanilla model and foil, but not renderGuiItemModel: the GUI item
            // atlas in Argentum replaces that entry point and can batch these icons as quads.
            RenderUtils.drawGuiItemModel(
                    renderer, stack, renderer.getModelShaper().getModel(stack), 0, 0);
        } finally {
            renderer.zOffset = z;
            GlStateManager.disableAlphaTest();
            GlStateManager.disableRescaleNormal();
            Lighting.turnOff();
            GlStateManager.popMatrix();
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
            textures.bind(TextureAtlas.BLOCKS_LOCATION);
            atlas.popFilter();
            GlStateManager.bindTexture(boundTexture);
            GlStateManager.activeTexture(textureUnit);
        }
    }

    private static ItemStack[] categoryStacks(Module.category category) {
        ItemStack normal;
        switch (category) {
            case combat:
                normal = new ItemStack(Items.DIAMOND_SWORD);
                break;
            case movement:
                normal = new ItemStack(Items.DIAMOND_BOOTS);
                break;
            case player:
                normal = new ItemStack(Items.GOLDEN_APPLE);
                break;
            case world:
                normal = new ItemStack(Items.FILLED_MAP);
                break;
            case render:
                normal = new ItemStack(Items.ENDER_EYE);
                break;
            case minigames:
                normal = new ItemStack(Items.GOLD_INGOT);
                break;
            case network:
                normal = new ItemStack(Items.COMPARATOR);
                break;
            case client:
                normal = new ItemStack(Items.COMPASS);
                break;
            case profiles:
                normal = new ItemStack(Items.BOOK);
                break;
            case scripts:
                normal = new ItemStack(Items.REDSTONE);
                break;
            case zombies:
                normal = new ItemStack(Items.SKULL, 1, 2);
                break;
            default:
                normal = new ItemStack(Items.CLOCK);
                break;
        }
        ItemStack active = normal.copy();
        if (category == Module.category.player) {
            active.setDamage(1);
        } else {
            active.addEnchantment(Enchantment.UNBREAKING, 2);
        }
        return new ItemStack[] {normal, active};
    }

    public enum Mask {
        EYE("eye"),
        EYE_OFF("eye_off"),
        BACK("arrow_left"),
        CLOSE("close");

        private final String resource;
        private BufferedImage source;
        private boolean missing;
        private int texture;
        private int uploadedSize;

        Mask(String file) {
            resource = "/assets/keystrokesmod/textures/gui/" + file + ".png";
        }

        public void draw(float x, float y, float size, int color) {
            if (source == null && !missing) {
                try (InputStream input = ClassicIcons.class.getResourceAsStream(resource)) {
                    source = input == null ? null : ImageIO.read(input);
                } catch (IOException ignored) {
                    // These bundled assets may be missing in a modified distribution.
                }
                missing = source == null;
            }
            if (missing) {
                return;
            }
            int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
            try {
                int pixels = Math.max(1, (int) Math.round(
                        size * ClickGui.getActiveRenderScale() * new Window(Minecraft.getInstance()).getScale()));
                if (texture == 0) {
                    texture = TextureUtil.genTextures();
                    if (texture == 0) {
                        return;
                    }
                }
                if (pixels != uploadedSize) {
                    TextureUtil.prepare(texture, pixels, pixels);
                    TextureUtil.uploadTexture(texture, downsampleMask(source, pixels), pixels, pixels);
                    GlStateManager.bindTexture(texture);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                    GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                    uploadedSize = pixels;
                }
                GlStateManager.bindTexture(texture);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color, (byte) (color >>> 24));
                GL11.glBegin(GL11.GL_QUADS);
                vertex(x, y, 0f, 0f);
                vertex(x, y + size, 0f, 1f);
                vertex(x + size, y + size, 1f, 1f);
                vertex(x + size, y, 1f, 0f);
                GL11.glEnd();
            } finally {
                GlStateManager.bindTexture(previous);
                GL11.glPopAttrib();
            }
        }

        private static void vertex(float x, float y, float u, float v) {
            GL11.glTexCoord2f(u, v);
            GL11.glVertex2f(x, y);
        }
    }

    /** Box-filter the mask's coverage before uploading. Transparent texels stay white for linear sampling. */
    static int[] downsampleMask(BufferedImage source, int size) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] original = source.getRGB(0, 0, width, height, null, 0, width);
        int[] result = new int[size * size];
        for (int y = 0; y < size; y++) {
            int top = y * height;
            int bottom = top + height;
            for (int x = 0; x < size; x++) {
                int left = x * width;
                int right = left + width;
                long alpha = 0;
                for (int sy = top / size; sy * size < bottom; sy++) {
                    int h = Math.min(bottom, (sy + 1) * size) - Math.max(top, sy * size);
                    for (int sx = left / size; sx * size < right; sx++) {
                        int w = Math.min(right, (sx + 1) * size) - Math.max(left, sx * size);
                        alpha += (long) (original[sy * width + sx] >>> 24) * w * h;
                    }
                }
                int area = width * height;
                result[y * size + x] = 0xFFFFFF | (int) ((alpha + area / 2) / area) << 24;
            }
        }
        return result;
    }
}
