package keystrokesmod.module.impl.render;

import java.awt.Color;
import java.util.ArrayList;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.font.FeralFontRenderer;
import keystrokesmod.utility.font.FontManager;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

public class Arrows extends Module {
    private SliderSetting arrow;
    private SliderSetting range;
    private SliderSetting radius;
    private SliderSetting scale;
    private SliderSetting font;
    private ButtonSetting tiltArrow;
    private ButtonSetting teamColor;
    private ButtonSetting hideTeammates;
    private ButtonSetting renderFriends;
    private ButtonSetting renderEnemies;
    private ButtonSetting renderDistance;
    private ButtonSetting renderOnlyOffScreen;
    private int friendColor = new Color(0, 255, 0, 255).getRGB();
    private int enemyColor = new Color(255, 0, 0, 255).getRGB();
    private final ArrayList<Arrows.ArrowRenderState> renderStates = new ArrayList<>();
    private int renderStateCount = 0;

    public Arrows() {
        super("Arrows", Module.category.render);
        this.registerSetting(
                this.arrow =
                        new SliderSetting("Arrow", 0, new String[] {"Chevron", "Caret", "Greater than", "Triangle"}));
        this.registerSetting(this.radius = new SliderSetting("Circle radius", 50.0, 30.0, 200.0, 5.0));
        this.registerSetting(this.scale = new SliderSetting("Sscale", 1.0, 0.5, 3.0, 0.1));
        this.registerSetting(this.range = new SliderSetting("Range", " block", 200.0, 25.0, 300.0, 5.0));
        this.registerSetting(this.font = FontManager.createFontSetting("Font"));
        this.registerSetting(this.tiltArrow = new ButtonSetting("Tilt arrow", false));
        this.registerSetting(this.teamColor = new ButtonSetting("Team color", true));
        this.registerSetting(this.hideTeammates = new ButtonSetting("Hide teammates", true));
        this.registerSetting(this.renderFriends = new ButtonSetting("Render friends (green)", true));
        this.registerSetting(this.renderEnemies = new ButtonSetting("Render enemies (red)", true));
        this.registerSetting(this.renderDistance = new ButtonSetting("Render distance", true));
        this.registerSetting(this.renderOnlyOffScreen = new ButtonSetting("Render only offscreen", false));
    }

    @ForgeEvents.SubscribeEvent
    public void onClientTick(ForgeEvents.TickEvent.ClientTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            this.updateRenderStates();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END) {
            if (mc.screen == null && Utils.nullCheck()) {
                try {
                    for (int i = 0; i < this.renderStateCount; i++) {
                        Arrows.ArrowRenderState renderState = this.renderStates.get(i);
                        if (renderState.player != null) {
                            this.renderIndicatorFor(renderState.player, renderState.color, event.renderTickTime);
                        }
                    }
                } catch (Exception var4) {
                }
            }
        }
    }

    private void updateRenderStates() {
        this.renderStateCount = 0;
        if (Utils.nullCheck() && mc.world != null) {
            for (PlayerEntity en : mc.world.players) {
                if (en != null
                        && en != mc.player
                        && !AntiBot.isBot(en)
                        && (!Utils.isTeammate(en) || !this.hideTeammates.isToggled())
                        && !(mc.player.distanceTo(en) > this.range.getInput())) {
                    int color = -1;
                    if (this.renderFriends.isToggled() && Utils.isFriended(en)) {
                        color = this.friendColor;
                    } else if (this.renderEnemies.isToggled() && Utils.isEnemy(en)) {
                        color = this.enemyColor;
                    } else if (this.teamColor.isToggled()) {
                        color = Utils.getColorFromEntity(en);
                    }

                    if (this.renderStateCount >= this.renderStates.size()) {
                        this.renderStates.add(new Arrows.ArrowRenderState());
                    }

                    this.renderStates.get(this.renderStateCount++).set(en, color);
                }
            }
        }
    }

    private void renderIndicatorFor(PlayerEntity en, int color, float partialTicks) {
        if (!this.renderOnlyOffScreen.isToggled() || !RenderUtils.isInViewFrustum(en)) {
            double x = en.prevX + (en.x - en.prevX) * partialTicks - mc.getEntityRenderDispatcher().cameraX;
            double y = en.prevY
                    + (en.y - en.prevY) * partialTicks
                    - mc.getEntityRenderDispatcher().cameraY
                    + en.height / 2.0F;
            double z = en.prevZ + (en.z - en.prevZ) * partialTicks - mc.getEntityRenderDispatcher().cameraZ;
            Window scaledResolution = new Window(mc);
            boolean render3D = this.tiltArrow.isToggled();
            Vec3d vec = null;
            if (!render3D) {
                ((IAccessorEntityRenderer) mc.gameRenderer)
                        .callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().partialTick, 0);
                vec = RenderUtils.convertTo2D(scaledResolution.getScale(), x, y, z);
            }

            if (render3D || vec != null) {
                mc.gameRenderer.setupGuiState();
                Window res = new Window(mc);
                boolean inFrustum = false;
                double dx;
                double dy;
                if (render3D) {
                    double angle =
                            Math.atan2(z, x) - (Math.PI / 2) - Math.toRadians(mc.getEntityRenderDispatcher().cameraYaw);
                    dx = Math.sin(angle);
                    dy = -Math.cos(angle);
                } else {
                    dx = vec.x - res.getWidth() / 2.0;
                    dy = vec.y - res.getHeight() / 2.0;
                    inFrustum = vec.z < 1.0003684;
                    if (!inFrustum) {
                        dx *= -1.0;
                        dy *= -1.0;
                    }
                }

                double angle1 = Math.atan2(dx, dy);
                double angle2 = Math.atan2(dy, dx) * (float) (180.0 / Math.PI) + 90.0;
                double hypotenuse = Math.hypot(dx, dy);
                double radiusInput = this.radius.getInput();
                double scaleInput = this.scale.getInput();
                if (inFrustum && hypotenuse < radiusInput + 15.0 * scaleInput) {
                    return;
                }

                double baseX = res.getWidth() / 2.0;
                double baseY = res.getHeight() / 2.0;
                double sinAng = Math.sin(angle1);
                double cosAng = Math.cos(angle1);
                float tilt = render3D ? 90.0F - Math.max(30.0F, mc.getEntityRenderDispatcher().cameraPitch) : 0.0F;
                double verticalScale = Math.cos(Math.toRadians(tilt));
                double renderX = baseX + radiusInput * sinAng;
                double renderY = baseY + radiusInput * cosAng * verticalScale;
                GlStateManager.pushMatrix();
                GlStateManager.translated(renderX, renderY, 0.0);
                GlStateManager.rotatef(tilt, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef((float) angle2, 0.0F, 0.0F, 1.0F);
                GlStateManager.scaled(scaleInput, scaleInput, scaleInput);
                int arrowInput = (int) this.arrow.getInput();
                if (arrowInput == 0) {
                    if (color == -1) {
                        GL11.glColor3d(1.0, 1.0, 1.0);
                    } else {
                        int rgb = color;
                        float red = (rgb >> 16 & 0xFF) / 255.0F;
                        float green = (rgb >> 8 & 0xFF) / 255.0F;
                        float blue = (rgb & 0xFF) / 255.0F;
                        GL11.glColor4f(red, green, blue, 1.0F);
                    }

                    GL11.glEnable(3042);
                    GL11.glDisable(3553);
                    GL11.glBlendFunc(770, 771);
                    GL11.glEnable(2848);
                    double halfAngle = 0.61086524F;
                    double size = 9.0;
                    double offsetY = 5.0;
                    GL11.glLineWidth((float) (3.0 * scaleInput));
                    GL11.glBegin(3);
                    GL11.glVertex2d(Math.sin(-halfAngle) * size, Math.cos(-halfAngle) * size - offsetY);
                    GL11.glVertex2d(0.0, -offsetY);
                    GL11.glVertex2d(Math.sin(halfAngle) * size, Math.cos(halfAngle) * size - offsetY);
                    GL11.glEnd();
                    GL11.glEnable(3553);
                    GL11.glDisable(3042);
                    GL11.glDisable(2848);
                } else if (arrowInput == 1) {
                    RenderUtils.drawFilledCaret(10.0 / scaledResolution.getScale(), color);
                } else if (arrowInput == 2) {
                    GlStateManager.rotatef(-90.0F, 0.0F, 0.0F, 1.0F);
                    GlStateManager.scaled(1.5, 1.5, 1.5);
                    FeralFontRenderer fr = this.getArrowFontRenderer();
                    fr.drawString(">", -2.0F, -4.0F, color, false);
                } else if (arrowInput == 3) {
                    RenderUtils.draw2DPolygon(0.0, 0.0, 5.0, 3, Utils.mergeAlpha(color, 255));
                }

                GlStateManager.popMatrix();
                renderX = baseX + (radiusInput - 13.0 * scaleInput) * sinAng;
                renderY = baseY + (radiusInput - 13.0 * scaleInput) * cosAng * verticalScale;
                GlStateManager.pushMatrix();
                GlStateManager.translated(renderX, renderY, 0.0);
                GlStateManager.scaled(0.8 * scaleInput, 0.8 * scaleInput, 0.8 * scaleInput);
                if (this.renderDistance.isToggled()) {
                    String text = (int) mc.player.distanceTo(en) + "m";
                    FeralFontRenderer fr = this.getArrowFontRenderer();
                    fr.drawString(text, -fr.getStringWidth(text) / 2, -4.0F, -1, true);
                }

                GlStateManager.popMatrix();
            }
        }
    }

    private String getSelectedFontName() {
        if (this.font == null) {
            return FontManager.getDefaultFontName();
        }

        int index = (int) Math.max(0.0, Math.min(this.font.getOptions().length - 1, this.font.getInput()));
        return this.font.getOptions()[index];
    }

    private FeralFontRenderer getArrowFontRenderer() {
        return FontManager.getNametagRenderer(this.getSelectedFontName());
    }

    private static final class ArrowRenderState {
        private PlayerEntity player;
        private int color;

        private ArrowRenderState() {}

        private void set(PlayerEntity player, int color) {
            this.player = player;
            this.color = color;
        }
    }
}
