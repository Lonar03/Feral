package keystrokesmod.module.impl.render;

import com.google.gson.JsonParseException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import javax.imageio.ImageIO;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.Setting;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.ItemListSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.OverlayEntityScans;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.WorldEntityScan.Kind;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.ItemModelShaper;
import net.minecraft.client.render.model.block.BakedModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.Resource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.resource.Identifier;
import net.minecraft.text.StringUtils;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

/** Native port of Tnop's dropped-item overlay. */
public class ItemESP extends Module {
    private static final String[] DEFAULT_BLACKLIST = {
        "minecraft:stone_sword",
        "minecraft:ladder",
        "minecraft:arrow",
        "minecraft:wool:*",
        "minecraft:end_stone",
        "minecraft:planks:*",
        "minecraft:log2:*"
    };
    private static final int WHITE = 0xFFFFFFFF;
    private static final int TNT_RED = 0xFFFF5555;
    private static final int NO_EFFECT = 4;
    private static final int[] FACES = {0, 2, 3, 1, 4, 5, 7, 6, 0, 1, 5, 4, 2, 3, 7, 6, 0, 2, 6, 4, 1, 3, 7, 5};
    private static final int[] EDGES = {0, 2, 1, 3, 4, 6, 5, 7, 0, 1, 2, 3, 4, 5, 6, 7, 0, 4, 1, 5, 2, 6, 3, 7};
    private static final int[] RESOURCE_COLORS = {0xFF55FF55, 0xFF55FFFF, 0xFFFFD700, WHITE};
    private static final Comparator<DropType> ITEM_ORDER = Comparator.comparingInt((DropType type) -> type.oreOrder)
            .thenComparing(type -> type.key)
            .thenComparingInt(type -> type.dye);
    // Baking creates new sprites on each resource reload.
    private static final Map<TextureAtlasSprite, Integer> TEXTURE_COLORS = new WeakHashMap<>();

    private final SliderSetting boxOpacity;
    private final SliderSetting scale;
    private final SliderSetting labelMode;
    private final SliderSetting effect;
    private final SliderSetting backgroundRounding;
    private final ColorSetting edgeColor;
    private final ColorSetting backgroundColor;
    private final ButtonSetting outline;
    private final ButtonSetting background;
    private final ButtonSetting itemCount;
    private final ButtonSetting autoScale;
    private final ButtonSetting oresOnly;
    private final ItemListSetting blacklist;

    private final Map<Long, Group> byBlock = new HashMap<>();
    private final List<Group> groups = new ArrayList<>();
    private final List<Group> labels = new ArrayList<>();
    private final Map<Item, Map<Long, DropType>> types = new IdentityHashMap<>();
    private final Map<String, Integer> widths = new HashMap<>();
    private final BlockPos.Mutable block = new BlockPos.Mutable();
    private final double[] projected = new double[3];
    private final float[] projectedCorners = new float[16];
    private final FloatBuffer savedColor = BufferUtils.createFloatBuffer(16);
    private RenderUtils.ProjectionContext projection;

    public ItemESP() {
        super("ItemESP", Module.category.render);
        this.liteModule = true;
        GroupSetting display = new GroupSetting("Display");
        this.registerSetting(display);
        this.registerSetting(this.boxOpacity = new SliderSetting(display, "Box Opacity", "%", "Off", 25, 0, 100, 1));
        this.registerSetting(this.outline = new ButtonSetting(display, "Outline", false));
        this.registerSetting(this.autoScale = new ButtonSetting(display, "Auto Scale", true));
        this.registerSetting(this.itemCount = new ButtonSetting(display, "Item Count", true));
        this.registerSetting(this.scale = new SliderSetting(display, "Scale", 1, 0.1, 2, 0.1));
        this.registerSetting(this.labelMode = new SliderSetting(display, "ESP", 0, new String[] {"Number", "Text"}));
        this.registerSetting(
                this.effect = new SliderSetting(display, "Text Effect", 0, new String[] {
                    "All Around", "Drop Shadow", "Depressed", "Raised", "None"
                }));
        this.registerSetting(this.edgeColor = new ColorSetting(display, "Character Edge Color", 0, 0, 0, 255));
        this.registerSetting(this.background = new ButtonSetting(display, "Label Background", true));
        this.registerSetting(this.backgroundColor = new ColorSetting(display, "Label Background Color", 0, 0, 0, 140));
        this.registerSetting(
                this.backgroundRounding = new SliderSetting(display, "Label Background Rounding", 2, 0, 5, 0.5));
        GroupSetting filters = new GroupSetting("Item Filters");
        this.registerSetting(filters);
        this.registerSetting(this.oresOnly = new ButtonSetting(filters, "Ores Only", false));
        this.registerSetting(this.blacklist = new ItemListSetting(filters, "Blacklisted Items"));
        for (String id : DEFAULT_BLACKLIST) {
            this.blacklist.addItem(id);
        }
    }

    @Override
    public void guiUpdate() {
        boolean labels = this.itemCount.isToggled();
        boolean edge = labels && this.effect.getInput() != NO_EFFECT;
        boolean bg = labels && this.background.isToggled();
        if (this.labelMode.visible != labels || this.edgeColor.visible != edge || this.backgroundColor.visible != bg) {
            this.labelMode.visible = this.scale.visible = this.effect.visible = this.background.visible = labels;
            this.edgeColor.visible = edge;
            this.backgroundColor.visible = this.backgroundRounding.visible = bg;
            Setting.reloadModuleRows(this);
        }
    }

    public boolean hasVisibleOverlay() {
        return this.boxOpacity.getInput() > 0 || this.outline.isToggled() || this.itemCount.isToggled();
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent event) {
        if (mc.world == null || mc.player == null || !this.hasVisibleOverlay()) {
            return;
        }
        float alpha = (float) this.boxOpacity.getInput() / 100.0F;
        boolean edges = this.outline.isToggled();
        boolean showLabels = this.itemCount.isToggled();
        boolean names = this.labelMode.getInput() == 1;
        boolean autosize = this.autoScale.isToggled();
        float maxScale = (float) this.scale.getInput();
        Entity camera = mc.getCamera();
        Culler culler = RenderUtils.frustum;
        if (camera != null) {
            culler.prepare(camera.x, camera.y, camera.z);
        }
        try {
            this.collect(event, camera == null ? null : culler, showLabels, names);
            if (this.groups.isEmpty()) {
                return;
            }
            this.groups.sort(Comparator.comparingDouble(group -> group.y));
            Window window = new Window(mc);
            this.projection = RenderUtils.captureProjectionContext(this.projection, window.getScale());
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            boolean drawBoxes = false;
            TextRenderer font = mc.textRenderer;
            for (Group group : this.groups) {
                if (this.project(group, dispatcher, autosize, alpha > 0 || edges)) {
                    drawBoxes |= group.corners != null;
                    if (showLabels) {
                        this.addLabel(group, font, names, maxScale, autosize);
                    }
                }
            }
            if (drawBoxes || !this.labels.isEmpty()) {
                this.draw(window, font, drawBoxes, edges, alpha, names);
            }
        } finally {
            this.byBlock.clear();
            this.groups.clear();
            this.labels.clear();
            this.types.clear();
            this.widths.clear();
        }
    }

    private void collect(ForgeEvents.RenderWorldLastEvent event, Culler culler, boolean showLabels, boolean names) {
        float partial = event.partialTicks;
        boolean ores = this.oresOnly.isToggled();
        for (Entity entity : OverlayEntityScans.render(event, mc, Kind.ITEMS)) {
            if (!(entity instanceof ItemEntity) || entity.ticks < 3) {
                continue;
            }
            ItemStack stack = ((ItemEntity) entity).getItem();
            Item item = stack == null ? null : stack.getItem();
            if (item == null || stack.size <= 0) {
                continue;
            }
            int dye = item instanceof ArmorItem && ((ArmorItem) item).getTier() == ArmorItem.Tier.CLOTH
                    ? item.getColorTint(stack, 0) & 0xFFFFFF
                    : 0;
            long variant = ((long) stack.getMetadata() << 24) | dye;
            DropType type = this.types
                    .computeIfAbsent(item, ignored -> new HashMap<>())
                    .computeIfAbsent(variant, ignored -> new DropType(stack, dye, this.blacklist, ores));
            if (type.filtered) {
                continue;
            }
            Box bounds = entity.getShape();
            if (!entity.ignoreCameraFrustum && (bounds == null || culler != null && !culler.isVisible(bounds))) {
                continue;
            }
            if (type.color == 0) {
                type.color = type.itemColor(stack);
            }
            if (showLabels && names && (type.name == null || stack.hasCustomHoverName())) {
                type.name = StringUtils.stripFormatting(stack.getHoverName());
            }
            double x = entity.prevX + (entity.x - entity.prevX) * partial;
            double y = entity.prevY + (entity.y - entity.prevY) * partial;
            double z = entity.prevZ + (entity.z - entity.prevZ) * partial;
            long key = this.block
                    .set(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z))
                    .toLong();
            Group group = this.byBlock.get(key);
            if (group == null) {
                group = new Group(x, y, z, showLabels);
                this.byBlock.put(key, group);
                this.groups.add(group);
            }
            if (group.first == null || ITEM_ORDER.compare(type, group.first) < 0) {
                group.first = type;
            }
            if (showLabels) {
                group.counts.merge(type, stack.size, Integer::sum);
            }
        }
    }

    private boolean project(Group group, EntityRenderDispatcher camera, boolean autosize, boolean boxes) {
        double x = group.x - camera.cameraX;
        double y = group.y - camera.cameraY;
        double z = group.z - camera.cameraZ;
        double size = autosize ? 0.5 + 0.375 * (Math.max(6, Math.sqrt(x * x + y * y + z * z)) - 6) / 28 : 0.5;
        double half = size / 2;
        int visible = 0;
        for (int i = 0; i < 8; i++) {
            if (!RenderUtils.projectTo2D(
                            this.projection,
                            x + ((i & 2) == 0 ? -half : half),
                            y + ((i & 4) == 0 ? 0 : size),
                            z + ((i & 1) == 0 ? -half : half),
                            this.projected)
                    || !Double.isFinite(this.projected[0])
                    || !Double.isFinite(this.projected[1])
                    || !(this.projected[2] >= 0 && this.projected[2] < 1.0003684)) {
                continue;
            }
            float px = (float) this.projected[0];
            float py = (float) this.projected[1];
            group.left = Math.min(group.left, px);
            group.right = Math.max(group.right, px);
            group.top = Math.min(group.top, py);
            group.bottom = Math.max(group.bottom, py);
            if (boxes) {
                this.projectedCorners[i * 2] = px;
                this.projectedCorners[i * 2 + 1] = py;
            }
            visible++;
        }
        if (visible < 2 || group.left >= group.right || group.top >= group.bottom) {
            return false;
        }
        if (visible == 8 && boxes) {
            group.corners = this.projectedCorners.clone();
        }
        return true;
    }

    private void addLabel(Group group, TextRenderer font, boolean names, float maxScale, boolean autosize) {
        this.measure(group, font, names, maxScale, autosize);
        if (!names) {
            for (int i = 0; i < this.labels.size(); ) {
                Group other = this.labels.get(i);
                if (!group.near(other) || !group.overlaps(other)) {
                    i++;
                    continue;
                }
                other.counts.forEach((type, count) -> group.counts.merge(type, count, Integer::sum));
                group.x = (group.x + other.x) / 2;
                group.y = (group.y + other.y) / 2;
                group.z = (group.z + other.z) / 2;
                group.left = Math.min(group.left, other.left);
                group.right = Math.max(group.right, other.right);
                group.top = Math.min(group.top, other.top);
                group.bottom = Math.max(group.bottom, other.bottom);
                this.labels.remove(i);
                this.measure(group, font, false, maxScale, autosize);
                i = 0;
            }
        }
        this.labels.add(group);
    }

    private void measure(Group group, TextRenderer font, boolean names, float maxScale, boolean autosize) {
        group.parts.clear();
        int width = 0;
        for (Map.Entry<DropType, Integer> entry : group.counts.entrySet()) {
            DropType type = entry.getKey();
            if (!names && !group.parts.isEmpty()) {
                width += this.addPart(group, font, "-", WHITE);
            }
            String count = Integer.toString(entry.getValue());
            String text = names ? count + "x " + type.displayName() : count;
            int partWidth = this.addPart(group, font, text, type.color);
            width = names ? Math.max(width, partWidth) : width + partWidth;
        }
        float ceiling = Math.max(0.2F, maxScale);
        group.textScale = autosize
                ? Math.max(0.2F, Math.min(ceiling, ceiling * (group.bottom - group.top) / (font.fontHeight * 1.5F)))
                : maxScale;
        group.width = width * group.textScale;
        group.height = font.fontHeight * group.textScale * (names ? group.parts.size() : 1);
        group.labelX = (group.left + group.right - group.width) / 2;
        group.labelY = names ? group.top - group.height - 2 : (group.top + group.bottom - group.height) / 2;
    }

    private int addPart(Group group, TextRenderer font, String text, int color) {
        int width = this.widths.computeIfAbsent(text, font::getWidth);
        group.parts.add(new Part(text, color, width));
        return width;
    }

    private void draw(Window window, TextRenderer font, boolean boxes, boolean edges, float alpha, boolean names) {
        this.layoutLabels();
        int matrix = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, window.getScaledWidth(), window.getScaledHeight(), 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_TEXTURE_BIT);
        try {
            // Keep raw GL changes scoped; the vanilla font's cached changes are restored in drawLabels.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GLX.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GLX.activeTexture(GLX.GL_TEXTURE1);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GLX.activeTexture(GLX.GL_TEXTURE0);
            this.drawGeometry(boxes, edges, alpha);
            if (!this.labels.isEmpty()) {
                this.drawLabels(font, names);
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.activeTexture(active);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(matrix);
        }
    }

    private void drawGeometry(boolean boxes, boolean edges, float alpha) {
        int bg = this.background.isToggled() && !this.labels.isEmpty() ? this.backgroundColor.getColor() : 0;
        if (!boxes && bg >>> 24 == 0) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            if (boxes && alpha > 0) {
                this.drawBoxes(GL11.GL_QUADS, FACES, alpha);
            }
            if (boxes && edges) {
                GL11.glLineWidth(1.5F);
                this.drawBoxes(GL11.GL_LINES, EDGES, 1);
            }
            if (bg >>> 24 != 0) {
                this.drawBackgrounds(bg);
            }
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawBackgrounds(int bg) {
        float rounding = (float) this.backgroundRounding.getInput();
        color(bg, (bg >>> 24) / 255.0F);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for (Group label : this.labels) {
            float pad = Math.min(1, label.textScale);
            float left = label.labelX - pad, right = label.labelX + label.width + pad;
            float top = label.labelY - pad, bottom = label.labelY + label.height + pad;
            float radius = Math.min(rounding * label.textScale, Math.min(right - left, bottom - top) / 2);
            int steps = radius > 0 ? 5 : 0;
            // Duplicate the ends so neighboring panels share a batch, not a visible triangle.
            GL11.glVertex2f(left, top + radius);
            for (int i = 0; i <= 2 * steps + 1; i++) {
                boolean rightSide = i > steps;
                int angle = 6 * (rightSide ? 2 * steps + 1 - i : i);
                float dx = radius * (1 - (float) RenderUtils.COS_0_90[angle]);
                float dy = radius * (1 - (float) RenderUtils.SIN_0_90[angle]);
                float x = rightSide ? right - dx : left + dx;
                GL11.glVertex2f(x, top + dy);
                GL11.glVertex2f(x, bottom - dy);
            }
            GL11.glVertex2f(right, bottom - radius);
        }
        GL11.glEnd();
    }

    private void drawBoxes(int primitive, int[] vertices, float alpha) {
        GL11.glBegin(primitive);
        for (Group group : this.groups) {
            if (group.corners != null) {
                color(group.first.color, alpha);
                for (int corner : vertices) {
                    GL11.glVertex2f(group.corners[corner * 2], group.corners[corner * 2 + 1]);
                }
            }
        }
        GL11.glEnd();
    }

    private void layoutLabels() {
        for (int i = 0; i < this.labels.size(); i++) {
            Group label = this.labels.get(i);
            for (int j = 0; j < i; ) {
                Group prior = this.labels.get(j++);
                if (label.near(prior) && label.overlaps(prior)) {
                    label.labelY = prior.labelY - label.height - 2;
                    j = 0;
                }
            }
        }
    }

    private void drawLabels(TextRenderer font, boolean names) {
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean textureOn = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        this.savedColor.clear();
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, this.savedColor);
        GlStateManager.enableTexture();
        int style = (int) this.effect.getInput();
        int edge = style == NO_EFFECT ? 0 : this.edgeColor.getColor();
        int opacity = edge >>> 24;
        boolean translucent = opacity > 0 && opacity < 255;
        int alphaFunc = translucent ? GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC) : 0;
        float alphaRef = translucent ? GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF) : 0;
        if (translucent) {
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0);
            // Vanilla treats alpha 0–3 as an unspecified (fully opaque) text color.
            edge = RenderUtils.withAlpha(edge, Math.max(4, opacity));
        }
        try {
            for (Group label : this.labels) {
                this.drawText(font, label, names, style, edge);
            }
        } finally {
            if (translucent) {
                GlStateManager.alphaFunc(alphaFunc, alphaRef);
            }
            if (alphaTest) {
                GlStateManager.enableAlphaTest();
            } else {
                GlStateManager.disableAlphaTest();
            }
            if (textureOn) {
                GlStateManager.enableTexture();
            } else {
                GlStateManager.disableTexture();
            }
            GlStateManager.bindTexture(texture);
            GlStateManager.color4f(
                    this.savedColor.get(0), this.savedColor.get(1), this.savedColor.get(2), this.savedColor.get(3));
        }
    }

    private void drawText(TextRenderer font, Group label, boolean names, int style, int edge) {
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(label.labelX, label.labelY, 0);
            GL11.glScalef(label.textScale, label.textScale, 1);
            // Draw every edge before its row's foreground. TNT's embedded colors only affect the foreground.
            for (int pass = edge >>> 24 == 0 ? 1 : 0; pass < 2; pass++) {
                float x = 0;
                float y = 0;
                for (Part part : label.parts) {
                    if (names) {
                        x = (label.width / label.textScale - part.width) / 2;
                    }
                    if (pass == 0) {
                        drawEdge(font, part.text, x, y, style, edge);
                    } else {
                        float offset = style == 2 ? -0.5F : style == 3 ? 0.5F : 0;
                        font.draw(part.text, x + offset, y + offset, part.color, false);
                    }
                    if (names) {
                        y += font.fontHeight;
                    } else {
                        x += part.width;
                    }
                }
            }
        } finally {
            GL11.glPopMatrix();
        }
    }

    private static void drawEdge(TextRenderer font, String text, float x, float y, int style, int edge) {
        if (text.indexOf('\u00a7') >= 0) {
            text = StringUtils.stripFormatting(text);
        }
        float offset = 0.5F;
        if (style == 0) {
            font.draw(text, x - offset, y, edge, false);
            font.draw(text, x + offset, y, edge, false);
            font.draw(text, x, y - offset, edge, false);
            font.draw(text, x, y + offset, edge, false);
        } else {
            float diagonal = style == 3 ? -offset : offset;
            font.draw(text, x + diagonal, y + diagonal, edge, false);
        }
    }

    private static void color(int rgb, float alpha) {
        GL11.glColor4f((rgb >> 16 & 255) / 255.0F, (rgb >> 8 & 255) / 255.0F, (rgb & 255) / 255.0F, alpha);
    }

    private static final class Group {
        private double x;
        private double y;
        private double z;
        private final Map<DropType, Integer> counts;
        private final List<Part> parts;
        private DropType first;
        private float left = Float.POSITIVE_INFINITY;
        private float top = Float.POSITIVE_INFINITY;
        private float right = Float.NEGATIVE_INFINITY;
        private float bottom = Float.NEGATIVE_INFINITY;
        private float[] corners;
        private float textScale;
        private float labelX;
        private float labelY;
        private float width;
        private float height;

        private Group(double x, double y, double z, boolean labels) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.counts = labels ? new TreeMap<>(ITEM_ORDER) : null;
            this.parts = labels ? new ArrayList<>() : null;
        }

        private boolean near(Group other) {
            double dx = this.x - other.x;
            double dy = this.y - other.y;
            double dz = this.z - other.z;
            return dx * dx + dy * dy + dz * dz <= 2.25;
        }

        private boolean overlaps(Group other) {
            return this.labelX < other.labelX + other.width
                    && this.labelX + this.width > other.labelX
                    && this.labelY < other.labelY + other.height
                    && this.labelY + this.height > other.labelY;
        }
    }

    private static final class Part {
        private final String text;
        private final int color;
        private final int width;

        private Part(String text, int color, int width) {
            this.text = text;
            this.color = color;
            this.width = width;
        }
    }

    private static final class DropType {
        private final String id;
        private final String key;
        private final int oreOrder;
        private final int dye;
        private final boolean filtered;
        private int color;
        private String name;

        private DropType(ItemStack stack, int dye, ItemListSetting blacklist, boolean oresOnly) {
            Identifier registry = (Identifier) Item.REGISTRY.getKey(stack.getItem());
            this.id = registry == null ? "" : registry.getPath();
            int meta = stack.getMetadata();
            String base =
                    registry == null || "minecraft".equals(registry.getNamespace()) ? this.id : registry.toString();
            this.key = meta == 0 ? base : base + ':' + meta;
            this.oreOrder = oreOrder(this.id);
            this.dye = dye;
            this.filtered =
                    registry == null || oresOnly && this.oreOrder >= RESOURCE_COLORS.length || blacklist.matches(stack);
        }

        private String displayName() {
            if ("tnt".equals(this.id)) {
                return "T\u00a7fN\u00a7cT";
            }
            return this.name == null || this.name.isEmpty() ? this.id.replace('_', ' ') : this.name;
        }

        private static int oreOrder(String id) {
            switch (id) {
                case "emerald":
                case "emerald_block":
                case "emerald_ore":
                    return 0;
                case "diamond":
                case "diamond_block":
                case "diamond_ore":
                    return 1;
                case "gold_ingot":
                case "gold_block":
                case "gold_ore":
                    return 2;
                case "iron_ingot":
                case "iron_block":
                case "iron_ore":
                    return 3;
                default:
                    return 4;
            }
        }

        private int itemColor(ItemStack stack) {
            if (this.oreOrder < RESOURCE_COLORS.length) {
                return RESOURCE_COLORS[this.oreOrder];
            }
            Item item = stack.getItem();
            if (item instanceof PotionItem || item instanceof SpawnEggItem) {
                return 0xFF000000 | item.getColorTint(stack, 0);
            }
            if (item instanceof ArmorItem && ((ArmorItem) item).getTier() == ArmorItem.Tier.CLOTH) {
                return 0xFF000000 | this.dye;
            }
            switch (this.id) {
                case "tnt":
                case "tnt_minecart":
                    return TNT_RED;
                // These use a special renderer instead of an ordinary item sprite.
                case "chest":
                case "trapped_chest":
                    return 0xFFAA793E;
                case "ender_chest":
                    return 0xFF397772;
                default:
                    break;
            }
            ItemModelShaper shaper = mc.getItemRenderer().getModelShaper();
            BakedModel model = shaper.getModel(stack);
            TextureAtlasSprite sprite = model == shaper.getManager().getMissingModel() ? null : model.getParticleIcon();
            if (sprite != null && sprite != shaper.getManager().getBlocksAtlas().getMissingSprite()) {
                int color = TEXTURE_COLORS.computeIfAbsent(sprite, DropType::spriteColor);
                if (color != 0) {
                    return color;
                }
            }
            int hue = (this.key.hashCode() & Integer.MAX_VALUE) % 360;
            return Color.HSBtoRGB(hue / 360.0F, 0.62F, 1);
        }

        private static int spriteColor(TextureAtlasSprite sprite) {
            for (int i = 0; i < sprite.getFrameCount(); i++) {
                int[][] frame = sprite.getFrame(i);
                if (frame != null && frame.length > 0 && frame[0] != null) {
                    return textureColor(frame[0]);
                }
            }
            // Vanilla clears static frames after baking. Read the active pack's original image once.
            Identifier name = new Identifier(sprite.getName());
            Identifier path = new Identifier(name.getNamespace(), "textures/" + name.getPath() + ".png");
            try {
                Resource resource = mc.getResourceManager().getResource(path);
                try (InputStream stream = resource.asStream()) {
                    resource.getMetadata("animation"); // Consumes/closes the optional sidecar stream.
                    BufferedImage image = ImageIO.read(stream);
                    if (image != null) {
                        int width = image.getWidth(), height = image.getHeight();
                        return textureColor(image.getRGB(0, 0, width, height, null, 0, width));
                    }
                }
            } catch (IOException | JsonParseException | IllegalStateException ignored) {
                // A missing or unreadable sprite still has a stable fallback.
            }
            return 0;
        }

        private static int textureColor(int[] pixels) {
            long red = 0, green = 0, blue = 0, weight = 0;
            for (int pixel : pixels) {
                int alpha = pixel >>> 24;
                red += (pixel >> 16 & 255) * alpha;
                green += (pixel >> 8 & 255) * alpha;
                blue += (pixel & 255) * alpha;
                weight += alpha;
            }
            return weight == 0
                    ? 0
                    : 0xFF000000 | (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
        }
    }
}
