package keystrokesmod.module.impl.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import keystrokesmod.Feral;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.module.setting.impl.GroupSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.shader.GlowShader;
import keystrokesmod.utility.shader.HeavyGlowShader;
import keystrokesmod.utility.shader.OutlineShader;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import org.lwjgl.opengl.GL11;

public class PlayerESP extends Module {
    public ColorSetting color;
    public ButtonSetting teamColor;
    public ButtonSetting rainbow;
    public GroupSetting espTypes;
    public ButtonSetting twoD;
    public ButtonSetting box;
    public ButtonSetting healthBar;
    public ButtonSetting outline;
    public ButtonSetting heavyGlow;
    public ButtonSetting shaded;
    public ButtonSetting skeleton;
    public ButtonSetting ring;
    public ButtonSetting redOnDamage;
    public ButtonSetting renderSelf;
    public ButtonSetting showInvis;
    private final SliderSetting maxDistance;
    private static final float RAD_TO_DEG = (float) (180.0 / Math.PI);
    public static boolean renderingOutlinePass = false;
    private final List<PlayerESP.EspRenderState> renderStates = new ArrayList<>();
    private final List<PlayerESP.EspRenderState> visibleRenderStates = new ArrayList<>();
    private final Map<PlayerEntity, PlayerESP.EspRenderState> playerRenderStates = new HashMap<>();
    private final double[] projectedPoint = new double[3];
    private RenderUtils.ProjectionContext projectionContext;
    private int renderStateCount;
    private int visibleRenderStateCount;
    private RenderTarget outlineFramebuffer;
    private final OutlineShader outlineShader = new OutlineShader();
    private final HeavyGlowShader heavyGlowShader = new HeavyGlowShader();
    private final GlowShader glowShader = new GlowShader();

    public PlayerESP() {
        super("PlayerESP", Module.category.render, 0);
        this.liteModule = true;
        this.registerSetting(this.espTypes = new GroupSetting("Types"));
        this.registerSetting(this.twoD = new ButtonSetting(this.espTypes, "2D", false));
        this.registerSetting(this.box = new ButtonSetting(this.espTypes, "Box", false));
        this.registerSetting(this.outline = new ButtonSetting(this.espTypes, "Outline (Light Glow)", false));
        this.registerSetting(this.heavyGlow = new ButtonSetting(this.espTypes, "Outline (Heavy Glow)", false));
        this.registerSetting(this.ring = new ButtonSetting(this.espTypes, "Ring", false));
        this.registerSetting(this.shaded = new ButtonSetting(this.espTypes, "Shaded", false));
        this.registerSetting(this.skeleton = new ButtonSetting(this.espTypes, "Skeleton", false));
        this.registerSetting(this.color = new ColorSetting("Color", 0, 255, 0));
        this.registerSetting(this.rainbow = new ButtonSetting("Rainbow", false));
        this.registerSetting(this.healthBar = new ButtonSetting("Health bar", true));
        this.registerSetting(this.redOnDamage = new ButtonSetting("Red on damage", true));
        this.registerSetting(this.renderSelf = new ButtonSetting("Render self", false));
        this.registerSetting(this.teamColor = new ButtonSetting("Team color", false));
        this.registerSetting(this.showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(this.maxDistance = new SliderSetting("Max distance", 128.0, 32.0, 256.0, 8.0));
    }

    @Override
    public void onDisable() {
        this.clearRenderStates();
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (Utils.nullCheck() && mc.world != null) {
                this.updateRenderStates();
            } else {
                this.clearRenderStates();
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderPlayerEvent(ForgeEvents.RenderPlayerEvent.Post e) {
        if (this.skeleton.isToggled() && e.entityPlayer != null && Utils.nullCheck()) {
            PlayerESP.EspRenderState renderState = this.playerRenderStates.get(e.entityPlayer);
            if (renderState != null) {
                this.renderSkeleton(
                        e.entityPlayer,
                        e.renderer.getModel(),
                        this.resolveBaseRenderColor(renderState),
                        e.partialRenderTick);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        this.visibleRenderStateCount = 0;
        if (Utils.nullCheck()) {
            boolean renderWorldTypes = this.box.isToggled()
                    || this.shaded.isToggled()
                    || this.healthBar.isToggled()
                    || this.ring.isToggled();
            boolean captureVisibleStates =
                    this.outline.isToggled() || this.heavyGlow.isToggled() || this.twoD.isToggled();
            if (this.renderStateCount != 0 && (renderWorldTypes || captureVisibleStates)) {
                for (int i = 0; i < this.renderStateCount; i++) {
                    PlayerESP.EspRenderState renderState = this.renderStates.get(i);
                    LivingEntity entity = renderState.entity;
                    if (entity != null && RenderUtils.isInViewFrustum(entity)) {
                        renderState.renderColor = this.resolveBaseRenderColor(renderState);
                        if (renderWorldTypes) {
                            this.render(entity, renderState.renderColor);
                        }

                        if (captureVisibleStates) {
                            this.addVisibleRenderState(renderState);
                        }
                    }
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOW)
    public void onRenderOutline(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.visibleRenderStateCount != 0) {
            if (this.outline.isToggled() || this.heavyGlow.isToggled()) {
                this.runOutlinePass(e.partialTicks);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderTwo2D(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck() && this.visibleRenderStateCount != 0) {
            if (this.twoD.isToggled()) {
                this.renderTwoDPass(e.partialTicks);
            }
        }
    }

    private void runOutlinePass(float partialTicks) {
        if (this.heavyGlow.isToggled()) {
            this.runHeavyGlowPass(partialTicks);
        } else if (this.glowShader.isValid() && this.outlineShader.isValid() && this.visibleRenderStateCount != 0) {
            this.outlineFramebuffer = this.createOutlineFramebuffer(this.outlineFramebuffer);
            if (this.outlineFramebuffer != null) {
                mc.getRenderTarget().bindWrite(true);
                GlStateManager.pushMatrix();
                GlStateManager.pushLightingAttributes();
                this.outlineFramebuffer.clear();
                this.outlineFramebuffer.bindWrite(true);
                ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
                boolean shadows = mc.options.entityShadows;
                mc.options.entityShadows = false;
                renderingOutlinePass = true;
                this.glowShader.use();

                for (int i = 0; i < this.visibleRenderStateCount; i++) {
                    PlayerESP.EspRenderState renderState = this.visibleRenderStates.get(i);
                    LivingEntity ent = renderState.entity;
                    int col = this.resolveOutlineColor(renderState);
                    this.glowShader.setColor(col >> 16 & 0xFF, col >> 8 & 0xFF, col & 0xFF, col >> 24 & 0xFF);
                    boolean invis = ent.isInvisible();
                    if (this.showInvis.isToggled()) {
                        ent.setInvisible(false);
                    }

                    mc.getEntityRenderDispatcher().render(ent, partialTicks, true);
                    ent.setInvisible(invis);
                }

                this.glowShader.stop();
                renderingOutlinePass = false;
                mc.options.entityShadows = shadows;
                mc.gameRenderer.disableLightMap();
                mc.gameRenderer.setupGuiState();
                mc.getRenderTarget().bindWrite(true);
                this.outlineShader.use();
                RenderUtils.drawFramebufferFullscreen(this.outlineFramebuffer);
                this.outlineShader.stop();
                this.outlineFramebuffer.clear();
                mc.getRenderTarget().bindWrite(true);
                GlStateManager.popAttributes();
                GlStateManager.popMatrix();
                ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
            }
        }
    }

    private void runHeavyGlowPass(float partialTicks) {
        if (GLX.useFbo() && this.heavyGlowShader.isValid() && this.visibleRenderStateCount != 0) {
            this.outlineFramebuffer = this.createOutlineFramebuffer(this.outlineFramebuffer);
            EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
            boolean shadows = renderManager.shouldRenderShadow();
            int matrixMode = GL11.glGetInteger(2976);
            GL11.glMatrixMode(5889);
            GL11.glPushMatrix();
            GL11.glMatrixMode(5888);
            GL11.glPushMatrix();

            try {
                this.outlineFramebuffer.clear();
                this.outlineFramebuffer.bindWrite(true);
                ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
                renderManager.setRenderShadow(false);
                GlStateManager.disableLighting();
                GlStateManager.disableBlend();
                GlStateManager.enableAlphaTest();
                GlStateManager.alphaFunc(516, 0.001F);
                GlStateManager.depthMask(false);
                GlStateManager.activeTexture(GLX.GL_TEXTURE1);
                GlStateManager.disableTexture();
                GlStateManager.activeTexture(GLX.GL_TEXTURE0);
                GlStateManager.enableTexture();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                renderingOutlinePass = true;

                for (int i = 0; i < this.visibleRenderStateCount; i++) {
                    PlayerESP.EspRenderState renderState = this.visibleRenderStates.get(i);
                    LivingEntity entity = renderState.entity;
                    boolean invisible = entity.isInvisible();

                    try {
                        if (this.showInvis.isToggled()) {
                            entity.setInvisible(false);
                        }

                        this.heavyGlowShader.useMask(this.resolveOutlineColor(renderState));
                        renderManager.render(entity, partialTicks, true);
                    } finally {
                        this.heavyGlowShader.stopMask();
                        entity.setInvisible(invisible);
                    }
                }

                renderingOutlinePass = false;
                mc.gameRenderer.disableLightMap();
                mc.getRenderTarget().bindWrite(true);
                mc.gameRenderer.setupGuiState();
                GlStateManager.disableDepthTest();
                GlStateManager.depthMask(false);
                GlStateManager.disableLighting();
                GlStateManager.disableCull();
                GlStateManager.enableTexture();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.heavyGlowShader.render(this.outlineFramebuffer);
            } finally {
                this.heavyGlowShader.stopMask();
                renderingOutlinePass = false;
                renderManager.setRenderShadow(shadows);
                mc.getRenderTarget().bindWrite(true);
                GL11.glMatrixMode(5888);
                GL11.glPopMatrix();
                GL11.glMatrixMode(5889);
                GL11.glPopMatrix();
                GL11.glMatrixMode(matrixMode);
                GlStateManager.activeTexture(GLX.GL_TEXTURE0);
                GlStateManager.bindTexture(0);
                GlStateManager.enableTexture();
                GlStateManager.enableDepthTest();
                GlStateManager.depthMask(true);
                GlStateManager.enableCull();
                GlStateManager.enableAlphaTest();
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.disableBlend();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private RenderTarget createOutlineFramebuffer(RenderTarget framebuffer) {
        int width = mc.width;
        int height = mc.height;
        if (framebuffer == null || framebuffer.viewWidth != width || framebuffer.viewHeight != height) {
            if (framebuffer != null) {
                framebuffer.destroyBuffers();
            }

            framebuffer = new RenderTarget(width, height, false);
        }

        framebuffer.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        return framebuffer;
    }

    private void clearRenderStates() {
        this.renderStateCount = 0;
        this.visibleRenderStateCount = 0;
        this.playerRenderStates.clear();
    }

    private void updateRenderStates() {
        this.renderStateCount = 0;
        this.visibleRenderStateCount = 0;
        this.playerRenderStates.clear();
        double maxDistSq = this.maxDistance.getInput() * this.maxDistance.getInput();
        if (Feral.DEBUG && ModuleManager.debug.debugEntities.isToggled()) {
            for (Entity entity : mc.world.entities) {
                if (entity instanceof LivingEntity
                        && entity != mc.player
                        && RenderUtils.isWithinDistanceSqToRenderView(entity, maxDistSq)) {
                    this.addRenderState((LivingEntity) entity, this.resolveStaticColor(entity));
                }
            }
        } else {
            PlayerEntity selfPlayer = (PlayerEntity) (Freecam.freeEntity == null ? mc.player : Freecam.freeEntity);
            boolean allowSelf = this.shouldRenderSelf(selfPlayer);

            for (PlayerEntity player : mc.world.players) {
                if ((player != selfPlayer || allowSelf)
                        && player.deathTicks == 0
                        && (this.showInvis.isToggled() || !player.isInvisible())
                        && (selfPlayer == player || !AntiBot.isBot(player))
                        && RenderUtils.isWithinDistanceSqToRenderView(player, maxDistSq)) {
                    this.addRenderState(player, this.resolveStaticColor(player));
                }
            }
        }
    }

    private boolean shouldRenderSelf(PlayerEntity selfPlayer) {
        return selfPlayer == mc.player
                && this.renderSelf.isToggled()
                && (!Settings.hideFirstPersonESP.isToggled() || mc.options.perspective > 0);
    }

    private void addRenderState(LivingEntity entity, int staticColor) {
        if (this.renderStateCount >= this.renderStates.size()) {
            this.renderStates.add(new PlayerESP.EspRenderState());
        }

        PlayerESP.EspRenderState renderState = this.renderStates.get(this.renderStateCount++);
        renderState.set(entity, staticColor);
        if (entity instanceof PlayerEntity) {
            this.playerRenderStates.put((PlayerEntity) entity, renderState);
        }
    }

    private void addVisibleRenderState(PlayerESP.EspRenderState renderState) {
        if (this.visibleRenderStateCount >= this.visibleRenderStates.size()) {
            this.visibleRenderStates.add(renderState);
        } else {
            this.visibleRenderStates.set(this.visibleRenderStateCount, renderState);
        }

        this.visibleRenderStateCount++;
    }

    private int resolveStaticColor(Entity entity) {
        int rgb = this.teamColor.isToggled() ? Utils.getColorFromEntity(entity) : this.color.getColor();
        return Utils.mergeAlpha(rgb, 255);
    }

    private int resolveBaseRenderColor(PlayerESP.EspRenderState renderState) {
        return !this.teamColor.isToggled() && this.rainbow.isToggled()
                ? Utils.mergeAlpha(Utils.getChroma(2L, 0L), 255)
                : renderState.staticColor;
    }

    private int resolveOutlineColor(PlayerESP.EspRenderState renderState) {
        return this.redOnDamage.isToggled() && renderState.entity.damagedTimer != 0 ? -65536 : renderState.renderColor;
    }

    public void render(Entity en, int rgb) {
        if (this.box.isToggled() || this.shaded.isToggled() || this.healthBar.isToggled() || this.ring.isToggled()) {
            if (this.box.isToggled()) {
                RenderUtils.renderEntity(en, 1, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
            }

            if (this.shaded.isToggled()
                    && (ModuleManager.murderMystery == null
                            || !ModuleManager.murderMystery.isEnabled()
                            || ModuleManager.murderMystery.isEmpty())) {
                RenderUtils.renderEntity(en, 2, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
            }

            if (this.healthBar.isToggled()) {
                RenderUtils.renderEntity(en, 4, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
            }

            if (this.ring.isToggled()) {
                RenderUtils.renderEntity(en, 6, 0.0, 0.0, rgb, this.redOnDamage.isToggled());
            }
        }
    }

    private void renderTwoDPass(float partialTicks) {
        EntityRenderDispatcher renderManager = mc.getEntityRenderDispatcher();
        if (renderManager != null) {
            Window scaledResolution = new Window(mc);
            ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(partialTicks, 0);
            this.projectionContext =
                    RenderUtils.captureProjectionContext(this.projectionContext, scaledResolution.getScale());
            mc.gameRenderer.setupGuiState();
            int screenWidth = scaledResolution.getWidth();
            int screenHeight = scaledResolution.getHeight();

            for (int i = 0; i < this.visibleRenderStateCount; i++) {
                this.renderTwoD(
                        this.visibleRenderStates.get(i), renderManager, screenWidth, screenHeight, partialTicks);
            }
        }
    }

    private void renderTwoD(
            PlayerESP.EspRenderState renderState,
            EntityRenderDispatcher renderManager,
            int screenWidth,
            int screenHeight,
            float partialTicks) {
        LivingEntity en = renderState.entity;
        double playerX = en.prevX + (en.x - en.prevX) * partialTicks - renderManager.cameraX;
        double playerY = en.prevY + (en.y - en.prevY) * partialTicks - renderManager.cameraY;
        double playerZ = en.prevZ + (en.z - en.prevZ) * partialTicks - renderManager.cameraZ;
        Box bbox = en.getShape().grown(0.1, 0.1, 0.1);
        double minRelX = bbox.minX - en.x + playerX;
        double minRelY = bbox.minY - en.y + playerY;
        double minRelZ = bbox.minZ - en.z + playerZ;
        double maxRelX = bbox.maxX - en.x + playerX;
        double maxRelY = bbox.maxY - en.y + playerY;
        double maxRelZ = bbox.maxZ - en.z + playerZ;
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        boolean isInView = false;

        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? minRelX : maxRelX;
            double y = (corner & 2) == 0 ? minRelY : maxRelY;
            double z = (corner & 4) == 0 ? minRelZ : maxRelZ;
            if (RenderUtils.projectTo2D(this.projectionContext, x, y, z, this.projectedPoint)) {
                double depth = this.projectedPoint[2];
                if (!(depth >= 1.0003684) && !(depth <= 0.0)) {
                    isInView = true;
                    double screenX = this.projectedPoint[0];
                    double screenY = this.projectedPoint[1];
                    if (screenX < minX) {
                        minX = screenX;
                    }

                    if (screenY < minY) {
                        minY = screenY;
                    }

                    if (screenX > maxX) {
                        maxX = screenX;
                    }

                    if (screenY > maxY) {
                        maxY = screenY;
                    }
                }
            }
        }

        if (isInView) {
            minX = Math.max(0.0, minX);
            minY = Math.max(0.0, minY);
            maxX = Math.min(screenWidth, maxX);
            maxY = Math.min(screenHeight, maxY);
            if (!(maxX <= minX) && !(maxY <= minY)) {
                int rgb = renderState.renderColor;
                float red = (rgb >> 16 & 0xFF) / 255.0F;
                float green = (rgb >> 8 & 0xFF) / 255.0F;
                float blue = (rgb & 0xFF) / 255.0F;
                GL11.glPushMatrix();
                GL11.glDisable(3553);
                GL11.glDisable(2929);
                GL11.glEnable(2848);
                GL11.glLineWidth(1.0F);
                GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX, minY);
                GL11.glVertex2d(maxX, minY);
                GL11.glVertex2d(maxX, maxY);
                GL11.glVertex2d(minX, maxY);
                GL11.glEnd();
                GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.4F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX + 1.0, minY + 1.0);
                GL11.glVertex2d(maxX - 1.0, minY + 1.0);
                GL11.glVertex2d(maxX - 1.0, maxY - 1.0);
                GL11.glVertex2d(minX + 1.0, maxY - 1.0);
                GL11.glEnd();
                GL11.glColor4f(red, green, blue, 1.0F);
                GL11.glBegin(2);
                GL11.glVertex2d(minX + 0.5, minY + 0.5);
                GL11.glVertex2d(maxX - 0.5, minY + 0.5);
                GL11.glVertex2d(maxX - 0.5, maxY - 0.5);
                GL11.glVertex2d(minX + 0.5, maxY - 0.5);
                GL11.glEnd();
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glEnable(3553);
                GL11.glEnable(2929);
                GL11.glDisable(2848);
                GL11.glPopMatrix();
            }
        }
    }

    public void renderSkeleton(PlayerEntity player, HumanoidModel modelBiped, int color, float partialTicks) {
        GL11.glPushMatrix();
        GL11.glDisable(2929);
        double viewerPosX = mc.getEntityRenderDispatcher().cameraX;
        double viewerPosY = mc.getEntityRenderDispatcher().cameraY;
        double viewerPosZ = mc.getEntityRenderDispatcher().cameraZ;
        double posX = player.prevX + (player.x - player.prevX) * partialTicks - viewerPosX;
        double posY = player.prevY + (player.y - player.prevY) * partialTicks - viewerPosY;
        double posZ = player.prevZ + (player.z - player.prevZ) * partialTicks - viewerPosZ;
        boolean wasBlendEnabled = GL11.glIsEnabled(3042);
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        if (!wasBlendEnabled) {
            GL11.glEnable(3042);
        }

        GL11.glBlendFunc(770, 771);
        GL11.glColor4f((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        GL11.glDisable(2896);
        GL11.glEnable(2848);
        GL11.glDisable(3553);
        GL11.glTranslated(posX, posY, posZ);
        float distance = mc.player.distanceTo(player);
        float computedLineWidth = 4.0F * ((100.0F - Math.min(distance, 100.0F)) / 100.0F);
        float lineWidth = Math.max(1.0F, computedLineWidth);
        GL11.glLineWidth(lineWidth);
        boolean isSneaking = player.isSneaking();
        float legHeight = isSneaking ? 0.6F : 0.75F;
        double legOffsetZ = isSneaking ? -0.2 : 0.0;
        GL11.glRotatef(player.bodyYaw, 0.0F, -999.0F, 0.0F);
        GL11.glTranslated(-0.15, legHeight, legOffsetZ);
        float rightLegRotX = modelBiped.rightLeg.rotationX * (float) (180.0 / Math.PI);
        float rightLegRotY = modelBiped.rightLeg.rotationY * (float) (180.0 / Math.PI);
        float rightLegRotZ = modelBiped.rightLeg.rotationZ * (float) (180.0 / Math.PI);
        GL11.glRotatef(rightLegRotX, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-rightLegRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rightLegRotZ, 0.0F, 0.0F, 1.0F);
        this.drawLine(0.0, 0.0, 0.0, 0.0, -legHeight, 0.0);
        GL11.glRotatef(rightLegRotZ, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(rightLegRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rightLegRotX, 1.0F, 0.0F, 0.0F);
        GL11.glTranslated(0.3, 0.0, 0.0);
        float leftLegRotX = modelBiped.leftLeg.rotationX * (float) (180.0 / Math.PI);
        float leftLegRotY = modelBiped.leftLeg.rotationY * (float) (180.0 / Math.PI);
        float leftLegRotZ = modelBiped.leftLeg.rotationZ * (float) (180.0 / Math.PI);
        GL11.glRotatef(leftLegRotX, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-leftLegRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-leftLegRotZ, 0.0F, 0.0F, 1.0F);
        this.drawLine(0.0, 0.0, 0.0, 0.0, -legHeight, 0.0);
        GL11.glRotatef(leftLegRotZ, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(leftLegRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-leftLegRotX, 1.0F, 0.0F, 0.0F);
        GL11.glTranslated(-0.15, 0.0, 0.0);
        this.drawLine(0.15, 0.0, 0.0, -0.15, 0.0, 0.0);
        if (player.isSneaking()) {
            GL11.glRotatef(20.0F, 1.0F, 0.0F, 0.0F);
        }

        this.drawLine(0.0, 0.0, 0.0, 0.0, 0.65, 0.0);
        GL11.glTranslated(0.0, 0.65, 0.0);
        this.drawLine(0.35, 0.0, 0.0, -0.35, 0.0, 0.0);
        GL11.glTranslated(-0.35, 0.0, 0.0);
        float rightArmRotX = modelBiped.rightArm.rotationX * (float) (180.0 / Math.PI);
        float rightArmRotY = modelBiped.rightArm.rotationY * (float) (180.0 / Math.PI);
        float rightArmRotZ = modelBiped.rightArm.rotationZ * (float) (180.0 / Math.PI);
        GL11.glRotatef(rightArmRotX, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-rightArmRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rightArmRotZ, 0.0F, 0.0F, 1.0F);
        this.drawLine(0.0, 0.0, 0.0, 0.0, -0.6, 0.0);
        GL11.glRotatef(rightArmRotZ, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(rightArmRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rightArmRotX, 1.0F, 0.0F, 0.0F);
        GL11.glTranslated(0.7, 0.0, 0.0);
        float leftArmRotX = modelBiped.leftArm.rotationX * (float) (180.0 / Math.PI);
        float leftArmRotY = modelBiped.leftArm.rotationY * (float) (180.0 / Math.PI);
        float leftArmRotZ = modelBiped.leftArm.rotationZ * (float) (180.0 / Math.PI);
        GL11.glRotatef(leftArmRotX, 1.0F, 0.0F, 0.0F);
        GL11.glRotatef(-leftArmRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-leftArmRotZ, 0.0F, 0.0F, 1.0F);
        this.drawLine(0.0, 0.0, 0.0, 0.0, -0.6, 0.0);
        GL11.glRotatef(leftArmRotZ, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(leftArmRotY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-leftArmRotX, 1.0F, 0.0F, 0.0F);
        GL11.glTranslated(-0.35, 0.0, 0.0);
        GL11.glRotatef(-player.bodyYaw, 0.0F, -999.0F, 0.0F);
        double headHeight = 0.4;
        GL11.glRotated(player.yaw, 0.0, -999.0, 0.0);
        GL11.glRotated(player.pitch, 999.0, 0.0, 0.0);
        this.drawLine(0.0, 0.0, 0.0, 0.0, headHeight, 0.0);
        this.drawLine(0.0, headHeight, 0.0, 0.0, headHeight, 0.25);
        GL11.glRotated(player.pitch, 999.0, 0.0, 0.0);
        GL11.glRotated(-player.yaw, 0.0, 999.0, 0.0);
        if (!wasBlendEnabled) {
            GL11.glDisable(3042);
        }

        GL11.glEnable(3553);
        GL11.glDisable(2848);
        GL11.glEnable(2896);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(2929);
        GL11.glPopMatrix();
    }

    private void drawLine(double x1, double y1, double z1, double x2, double y2, double z2) {
        GL11.glBegin(1);
        GL11.glVertex3d(x1, y1, z1);
        GL11.glVertex3d(x2, y2, z2);
        GL11.glEnd();
    }

    private static final class EspRenderState {
        private LivingEntity entity;
        private int staticColor;
        private int renderColor;

        private EspRenderState() {}

        private void set(LivingEntity entity, int staticColor) {
            this.entity = entity;
            this.staticColor = staticColor;
            this.renderColor = staticColor;
        }
    }
}
