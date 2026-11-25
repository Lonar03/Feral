package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.data.SyncedData.Entry;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityDataS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public class DamageTags extends Module {
    private static final String[] DEPTH_MODES = new String[] {"Vanilla", "Visible", "Through walls"};
    private static final int HEALTH_WATCHER_ID = 6;
    private static final int ABSORPTION_WATCHER_ID = 17;
    private static final double MAX_RENDER_DISTANCE_SQ = 4096.0;
    private static final double RISE_DISTANCE = 1.0;
    private static final double FRUSTUM_AABB_EXPAND = 0.35;
    private static final double STACK_RADIUS_SQ = 0.36;
    private static final float HIDDEN_TEXT_ALPHA = 0.1254902F;
    private static final float FADE_START = 0.72F;
    private static final int MIN_FONT_ALPHA = 4;
    private final SliderSetting duration;
    private final SliderSetting scale;
    private final SliderSetting font;
    private final SliderSetting depthMode;
    private final ButtonSetting textShadow;
    private final ButtonSetting background;
    private final SliderSetting backgroundOpacity;
    private final List<DamageTags.DamageTag> activeTags = new ArrayList<>();
    private final Map<Integer, DamageTags.HealthSnapshot> trackedStates = new HashMap<>();
    private final ConcurrentLinkedQueue<DamageTags.QueuedMetadataUpdate> pendingUpdates = new ConcurrentLinkedQueue<>();
    private World lastWorld;

    public DamageTags() {
        super("Damage Tags", Module.category.render, 0);
        this.registerSetting(this.duration = new SliderSetting("Duration", " ms", 1200.0, 0.0, 2000.0, 100.0));
        this.registerSetting(this.scale = new SliderSetting("Scale", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(this.font = FontManager.createFontSetting("Font"));
        this.registerSetting(this.depthMode = new SliderSetting("Depth", 0, DEPTH_MODES));
        this.registerSetting(this.textShadow = new ButtonSetting("Text Shadow", false));
        this.registerSetting(this.background = new ButtonSetting("Background", true));
        this.registerSetting(this.backgroundOpacity = new SliderSetting("Background Opacity", 0.25, 0.0, 1.0, 0.05));
    }

    @Override
    public void guiUpdate() {
        this.backgroundOpacity.setVisible(this.background.isToggled(), this);
    }

    @Override
    public void onEnable() {
        this.resetState();
        this.lastWorld = mc.world;
    }

    @Override
    public void onDisable() {
        this.resetState();
    }

    @ForgeEvents.SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (e.getPacket() instanceof EntityDataS2CPacket && mc.world != null) {
            EntityDataS2CPacket packet = (EntityDataS2CPacket) e.getPacket();
            List<Entry> watchedObjects = packet.getDataEntries();
            if (watchedObjects != null && !watchedObjects.isEmpty()) {
                boolean hasHealth = false;
                float health = 0.0F;
                boolean hasAbsorption = false;
                float absorption = 0.0F;

                for (Entry watchedObject : watchedObjects) {
                    if (watchedObject != null && watchedObject.getValue() != null) {
                        int dataValueId = watchedObject.getId();
                        if (dataValueId == 6 && watchedObject.getValue() instanceof Float) {
                            hasHealth = true;
                            health = (Float) watchedObject.getValue();
                        } else if (dataValueId == 17 && watchedObject.getValue() instanceof Float) {
                            hasAbsorption = true;
                            absorption = (Float) watchedObject.getValue();
                        }
                    }
                }

                if (hasHealth || hasAbsorption) {
                    this.pendingUpdates.offer(new DamageTags.QueuedMetadataUpdate(
                            packet.getId(), hasHealth, health, hasAbsorption, absorption));
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (mc.world != this.lastWorld) {
                this.resetState();
                this.lastWorld = mc.world;
            }

            long now = System.currentTimeMillis();
            this.flushPendingUpdates(e.partialTicks, now);
            this.updateTrackedStates();
            this.pruneExpiredTags(now);
            if (!this.activeTags.isEmpty()) {
                FeralFontRenderer fontRenderer = this.getDamageTagFontRenderer();
                EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
                if (fontRenderer != null && renderManager != null) {
                    double viewerX = renderManager.cameraX;
                    double viewerY = renderManager.cameraY;
                    double viewerZ = renderManager.cameraZ;
                    int depthOrdinal = (int) this.depthMode.getInput();
                    float scaleMul = (float) this.scale.getInput();
                    boolean bgEnabled = this.background.isToggled();
                    float bgOpacitySlider = (float) this.backgroundOpacity.getInput();
                    GlStateManager.pushLightingAttributes();

                    try {
                        for (DamageTags.DamageTag tag : this.activeTags) {
                            double dx = tag.x - viewerX;
                            double dy = tag.y - viewerY;
                            double dz = tag.z - viewerZ;
                            if (!(dx * dx + dy * dy + dz * dz > 4096.0)
                                    && RenderUtils.isInViewFrustum(tag.frustumAabb)) {
                                this.renderTag(
                                        tag,
                                        now,
                                        renderManager,
                                        fontRenderer,
                                        viewerX,
                                        viewerY,
                                        viewerZ,
                                        depthOrdinal,
                                        scaleMul,
                                        bgEnabled,
                                        bgOpacitySlider);
                            }
                        }
                    } finally {
                        GlStateManager.popAttributes();
                    }
                }
            }
        }
    }

    private void flushPendingUpdates(float partialTicks, long nowMillis) {
        if (mc.world == null) {
            this.pendingUpdates.clear();
        } else {
            DamageTags.QueuedMetadataUpdate update;
            while ((update = this.pendingUpdates.poll()) != null) {
                Entity entity = mc.world.getEntity(update.entityId);
                if (entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity) && entity != mc.player) {
                    LivingEntity living = (LivingEntity) entity;
                    DamageTags.HealthSnapshot previous = this.trackedStates.get(update.entityId);
                    float newHealth = update.hasHealth ? update.health : this.getHealth(living);
                    float newAbsorption = update.hasAbsorption ? update.absorption : this.getAbsorption(living);
                    DamageTags.HealthSnapshot current = new DamageTags.HealthSnapshot(newHealth, newAbsorption);
                    if (previous == null) {
                        this.trackedStates.put(update.entityId, current);
                    } else {
                        float delta = current.total() - previous.total();
                        this.trackedStates.put(update.entityId, current);
                        if (!(Math.abs(delta) <= 0.01F)) {
                            this.spawnTag(living, delta, partialTicks, nowMillis);
                        }
                    }
                } else {
                    this.trackedStates.remove(update.entityId);
                }
            }
        }
    }

    private void updateTrackedStates() {
        if (mc.world == null) {
            this.trackedStates.clear();
        } else {
            this.trackedStates.entrySet().removeIf(entry -> {
                Entity entityx = mc.world.getEntity(entry.getKey());
                return !(entityx instanceof LivingEntity)
                        || entityx instanceof ArmorStandEntity
                        || entityx == mc.player;
            });
            List<Entity> loaded = mc.world.entities;
            int i = 0;

            for (int n = loaded.size(); i < n; i++) {
                Entity entity = loaded.get(i);
                if (entity instanceof LivingEntity && !(entity instanceof ArmorStandEntity) && entity != mc.player) {
                    LivingEntity living = (LivingEntity) entity;
                    this.trackedStates.put(
                            entity.getNetworkId(),
                            new DamageTags.HealthSnapshot(this.getHealth(living), this.getAbsorption(living)));
                }
            }
        }
    }

    private void pruneExpiredTags(long now) {
        Iterator<DamageTags.DamageTag> iterator = this.activeTags.iterator();

        while (iterator.hasNext()) {
            DamageTags.DamageTag tag = iterator.next();
            if (now - tag.createdAt >= tag.durationMs) {
                iterator.remove();
            }
        }
    }

    private void spawnTag(LivingEntity living, float delta, float partialTicks, long nowMillis) {
        double x = this.interpolate(living.prevX, living.x, partialTicks);
        double y = this.interpolate(living.prevY, living.y, partialTicks) + living.height + 0.5;
        double z = this.interpolate(living.prevZ, living.z, partialTicks);
        y += this.getStackOffset(x, y, z, nowMillis);
        long durationMs = Math.max(1L, Math.round(this.duration.getInput()));
        String text = (delta > 0.0F ? "+" : "-") + this.fastOneDecimal(Math.abs(delta));
        int color = delta > 0.0F ? -11141291 : -43691;
        FeralFontRenderer fr = this.getDamageTagFontRenderer();
        int halfW = fr != null ? fr.getStringWidth(text) >> 1 : 0;
        this.activeTags.add(new DamageTags.DamageTag(text, color, x, y, z, nowMillis, durationMs, halfW));
        if (this.activeTags.size() > 256) {
            this.activeTags.remove(0);
        }
    }

    private void renderTag(
            DamageTags.DamageTag tag,
            long now,
            EntityRenderDispatcher renderManager,
            FeralFontRenderer fontRenderer,
            double viewerX,
            double viewerY,
            double viewerZ,
            int depthOrdinal,
            float scaleMul,
            boolean bgEnabled,
            float bgOpacitySlider) {
        float progress = this.getProgress(tag, now);
        if (!(progress >= 1.0F)) {
            float rise = this.expoOut(progress);
            float alpha = this.getAlpha(progress);
            if (!(alpha <= 0.0F)) {
                double x = tag.x - viewerX;
                double y = tag.y + rise * 1.0 - viewerY;
                double z = tag.z - viewerZ;
                float renderScale = 0.02666667F * scaleMul;
                int halfWidth = tag.textHalfWidth;
                GlStateManager.pushMatrix();
                GlStateManager.translatef((float) x, (float) y, (float) z);
                GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(-renderManager.cameraYaw, 0.0F, 1.0F, 0.0F);
                GlStateManager.rotatef(
                        renderManager.cameraPitch * (mc.options.perspective == 2 ? -1 : 1), 1.0F, 0.0F, 0.0F);
                GlStateManager.scalef(-renderScale, -renderScale, renderScale);
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                switch (depthOrdinal) {
                    case 1:
                        this.renderVisibleTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                        break;
                    case 2:
                        this.renderThroughWallsTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                        break;
                    default:
                        this.renderVanillaDepthTag(fontRenderer, tag, halfWidth, alpha, bgEnabled, bgOpacitySlider);
                }

                GlStateManager.enableDepthTest();
                GlStateManager.depthMask(true);
                GlStateManager.enableTexture();
                GlStateManager.disableBlend();
                GlStateManager.enableLighting();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.popMatrix();
            }
        }
    }

    private void renderVanillaDepthTag(
            FeralFontRenderer fontRenderer,
            DamageTags.DamageTag tag,
            int halfWidth,
            float alpha,
            boolean bgEnabled,
            float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.disableDepthTest();
        this.drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(tag.text, -halfWidth, 0.0F, this.applyFontAlpha(tag.color, 0.1254902F * alpha), false);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        fontRenderer.drawString(
                tag.text, -halfWidth, 0.0F, this.applyFontAlpha(tag.color, alpha), this.textShadow.isToggled());
    }

    private void renderVisibleTag(
            FeralFontRenderer fontRenderer,
            DamageTags.DamageTag tag,
            int halfWidth,
            float alpha,
            boolean bgEnabled,
            float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.enableDepthTest();
        this.drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(
                tag.text, -halfWidth, 0.0F, this.applyFontAlpha(tag.color, alpha), this.textShadow.isToggled());
    }

    private void renderThroughWallsTag(
            FeralFontRenderer fontRenderer,
            DamageTags.DamageTag tag,
            int halfWidth,
            float alpha,
            boolean bgEnabled,
            float bgOpacitySlider) {
        GlStateManager.depthMask(false);
        GlStateManager.disableDepthTest();
        this.drawBackground(halfWidth, alpha, bgEnabled, bgOpacitySlider);
        fontRenderer.drawString(
                tag.text, -halfWidth, 0.0F, this.applyFontAlpha(tag.color, alpha), this.textShadow.isToggled());
    }

    private void drawBackground(int halfWidth, float alpha, boolean bgEnabled, float bgOpacitySlider) {
        if (bgEnabled) {
            float bgAlpha = bgOpacitySlider * alpha;
            if (!(bgAlpha <= 0.0F)) {
                GlStateManager.disableTexture();
                Tesselator tessellator = Tesselator.getInstance();
                BufferBuilder worldRenderer = tessellator.getBuffer();
                worldRenderer.begin(7, DefaultVertexFormat.POSITION_COLOR);
                worldRenderer
                        .vertex(-halfWidth - 1, -1.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, bgAlpha)
                        .nextVertex();
                worldRenderer
                        .vertex(-halfWidth - 1, 8.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, bgAlpha)
                        .nextVertex();
                worldRenderer
                        .vertex(halfWidth + 1, 8.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, bgAlpha)
                        .nextVertex();
                worldRenderer
                        .vertex(halfWidth + 1, -1.0, 0.0)
                        .color(0.0F, 0.0F, 0.0F, bgAlpha)
                        .nextVertex();
                tessellator.end();
                GlStateManager.enableTexture();
            }
        }
    }

    private float getProgress(DamageTags.DamageTag tag, long now) {
        return MathHelper.clamp((float) (now - tag.createdAt) / (float) tag.durationMs, 0.0F, 1.0F);
    }

    private float getAlpha(float progress) {
        if (progress <= 0.72F) {
            return 1.0F;
        }

        float fadeProgress = MathHelper.clamp((progress - 0.72F) / 0.27999997F, 0.0F, 1.0F);
        return 1.0F - fadeProgress;
    }

    private double getStackOffset(double x, double y, double z, long nowMillis) {
        int overlaps = 0;

        for (DamageTags.DamageTag tag : this.activeTags) {
            if (nowMillis - tag.createdAt < tag.durationMs) {
                double dx = tag.x - x;
                double dz = tag.z - z;
                if (dx * dx + dz * dz <= 0.36 && Math.abs(tag.y - y) <= 1.5) {
                    overlaps++;
                }
            }
        }

        return overlaps * 0.15;
    }

    private float getHealth(LivingEntity living) {
        return Math.max(0.0F, living.getHealth());
    }

    private float getAbsorption(LivingEntity living) {
        return living instanceof PlayerEntity ? Math.max(0.0F, ((PlayerEntity) living).getAbsorption()) : 0.0F;
    }

    private double interpolate(double start, double end, float partialTicks) {
        return start + (end - start) * partialTicks;
    }

    private int applyFontAlpha(int color, float alpha) {
        int alphaChannel = MathHelper.clamp(Math.round(alpha * 255.0F), 0, 255);
        if (alpha > 0.0F && alphaChannel < 4) {
            alphaChannel = 4;
        }

        return alphaChannel << 24 | color & 16777215;
    }

    private String fastOneDecimal(float value) {
        int tenths = Math.round(value * 10.0F);
        int intPart = tenths / 10;
        int fracPart = Math.abs(tenths % 10);
        return fracPart == 0 ? String.valueOf(intPart) : intPart + "." + fracPart;
    }

    private float expoOut(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        } else {
            return t >= 1.0F ? 1.0F : 1.0F - (float) Math.pow(2.0, -10.0 * t);
        }
    }

    private String getSelectedFontName() {
        if (this.font == null) {
            return FontManager.getDefaultFontName();
        }

        int index = (int) Math.max(0.0, Math.min(this.font.getOptions().length - 1, this.font.getInput()));
        return this.font.getOptions()[index];
    }

    private FeralFontRenderer getDamageTagFontRenderer() {
        return FontManager.getNametagRenderer(this.getSelectedFontName());
    }

    private void resetState() {
        this.activeTags.clear();
        this.trackedStates.clear();
        this.pendingUpdates.clear();
    }

    private static class DamageTag {
        private final String text;
        private final int color;
        private final double x;
        private final double y;
        private final double z;
        private final long createdAt;
        private final long durationMs;
        private final Box frustumAabb;
        private final int textHalfWidth;

        private DamageTag(
                String text,
                int color,
                double x,
                double y,
                double z,
                long createdAt,
                long durationMs,
                int textHalfWidth) {
            this.text = text;
            this.color = color;
            this.x = x;
            this.y = y;
            this.z = z;
            this.createdAt = createdAt;
            this.durationMs = durationMs;
            this.textHalfWidth = textHalfWidth;
            double e = 0.35;
            this.frustumAabb = new Box(x - e, y - e, z - e, x + e, y + 1.0 + e, z + e);
        }
    }

    private static class HealthSnapshot {
        private final float health;
        private final float absorption;

        private HealthSnapshot(float health, float absorption) {
            this.health = health;
            this.absorption = absorption;
        }

        private float total() {
            return this.health + this.absorption;
        }
    }

    private static class QueuedMetadataUpdate {
        private final int entityId;
        private final boolean hasHealth;
        private final float health;
        private final boolean hasAbsorption;
        private final float absorption;

        private QueuedMetadataUpdate(
                int entityId, boolean hasHealth, float health, boolean hasAbsorption, float absorption) {
            this.entityId = entityId;
            this.hasHealth = hasHealth;
            this.health = health;
            this.hasAbsorption = hasAbsorption;
            this.absorption = absorption;
        }
    }
}
