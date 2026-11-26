package keystrokesmod.module.impl.render;

import java.awt.Color;
import keystrokesmod.clickgui.HudPositionEditor;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.render.Window;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

public class Radar extends Module {
    private static final float BASE_SIZE = 100.0F;
    private static final float DEFAULT_POS_X = 5.0F;
    private static final float DEFAULT_POS_Y = 70.0F;
    private static final float CORNER_RADIUS = 8.0F;
    private static final int BACKGROUND_COLOR = new Color(0, 0, 0, 125).getRGB();
    private static final int OUTLINE_COLOR = Color.WHITE.getRGB();
    private final SliderSetting range;
    private final SliderSetting radarScale;
    private final ButtonSetting tracerLines;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public Radar() {
        super("Radar", Module.category.render);
        this.registerSetting(this.range = new SliderSetting("Range", " block", 20.0, 8.0, 256.0, 1.0));
        this.registerSetting(this.radarScale = new SliderSetting("Scale", 1.0, 0.5, 2.0, 0.1));
        this.registerSetting(this.tracerLines = new ButtonSetting("Show tracer lines", false));
        this.registerSetting(new ButtonSetting("Edit position", () -> mc.openScreen(new Radar.EditScreen())));
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent event) {
        if (event.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            if (mc.screen == null && !mc.options.debugEnabled) {
                this.renderRadar(false);
            }
        }
    }

    public float getPosX() {
        this.syncPositionToResolution();
        return this.posX;
    }

    public float getPosY() {
        this.syncPositionToResolution();
        return this.posY;
    }

    public float getRelativePosX() {
        this.syncPositionToResolution();
        return this.relativePosX;
    }

    public float getRelativePosY() {
        this.syncPositionToResolution();
        return this.relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        this.relativePosX = normalizedX;
        this.relativePosY = normalizedY;
        this.syncPositionToResolution();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        this.setAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    public void resetPosition() {
        this.setAbsolutePosition(5.0F, 70.0F);
    }

    private void renderRadar(boolean editing) {
        Window resolution = new Window(mc);
        this.syncPositionToResolution(resolution);
        float size = this.getRadarSize();
        float centerX = this.posX + size / 2.0F;
        float centerY = this.posY + size / 2.0F;
        float visualScale = this.getRadarScale();
        RenderUtils.drawRoundedGradientOutlinedRectangle(
                this.posX,
                this.posY,
                this.posX + size,
                this.posY + size,
                8.0F * visualScale,
                BACKGROUND_COLOR,
                OUTLINE_COLOR,
                OUTLINE_COLOR);
        RenderUtils.drawPolygon(centerX, centerY + 2.0F * visualScale, 5.0F * visualScale, 3, Color.WHITE.getRGB());
        double selectedRange = this.range.getInput();
        double selectedRangeSquared = selectedRange * selectedRange;
        double usableRadius = Math.max(1.0, size / 2.0F - 5.0F * visualScale);

        for (PlayerEntity player : mc.world.players) {
            if (player != mc.player && player.deathTicks == 0 && !AntiBot.isBot(player)) {
                double deltaX = player.x - mc.player.x;
                double deltaZ = player.z - mc.player.z;
                double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                if (!(horizontalDistanceSquared > selectedRangeSquared)) {
                    double playerAngle = mc.player.yaw + Math.toDegrees(Math.atan2(deltaX, deltaZ));
                    double radarDistance = Math.sqrt(horizontalDistanceSquared) / selectedRange * usableRadius;
                    double xOffset = radarDistance * Math.sin(Math.toRadians(playerAngle));
                    double yOffset = radarDistance * Math.cos(Math.toRadians(playerAngle));
                    double markerX = centerX - xOffset;
                    double markerY = centerY - yOffset;
                    if (this.tracerLines.isToggled()) {
                        this.drawTracer(centerX, centerY, markerX, markerY);
                    }

                    RenderUtils.drawPolygon(markerX, markerY, 3.0F * visualScale, 4, Color.RED.getRGB());
                }
            }
        }
    }

    private void drawTracer(double startX, double startY, double endX, double endY) {
        GL11.glPushMatrix();
        GL11.glPushAttrib(1048575);
        GL11.glEnable(3042);
        GL11.glEnable(2848);
        GL11.glDisable(2929);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glLineWidth(0.5F);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glBegin(1);
        GL11.glVertex2d(startX, startY);
        GL11.glVertex2d(endX, endY);
        GL11.glEnd();
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }

    private float getRadarScale() {
        return (float) this.radarScale.getInput();
    }

    private float getRadarSize() {
        return 100.0F * this.getRadarScale();
    }

    private void syncPositionToResolution() {
        this.syncPositionToResolution(new Window(mc));
    }

    private void syncPositionToResolution(Window resolution) {
        int scaledWidth = Math.max(1, resolution.getWidth());
        int scaledHeight = Math.max(1, resolution.getHeight());
        if (Float.isNaN(this.relativePosX) || Float.isNaN(this.relativePosY)) {
            if (!Float.isNaN(this.posX) && !Float.isNaN(this.posY)) {
                this.relativePosX = this.posX / scaledWidth;
                this.relativePosY = this.posY / scaledHeight;
            } else {
                this.relativePosX = 5.0F / scaledWidth;
                this.relativePosY = 70.0F / scaledHeight;
            }
        }

        this.posX = this.relativePosX * scaledWidth;
        this.posY = this.relativePosY * scaledHeight;
        this.clampPosition(resolution);
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        this.posX = absoluteX;
        this.posY = absoluteY;
        this.clampPosition(resolution);
        this.updateRelativePosition(resolution);
    }

    private void clampPosition(Window resolution) {
        float maxX = Math.max(0.0F, resolution.getWidth() - this.getRadarSize());
        float maxY = Math.max(0.0F, resolution.getHeight() - this.getRadarSize());
        this.posX = Math.max(0.0F, Math.min(maxX, this.posX));
        this.posY = Math.max(0.0F, Math.min(maxY, this.posY));
    }

    private void updateRelativePosition(Window resolution) {
        this.relativePosX = this.posX / Math.max(1, resolution.getWidth());
        this.relativePosY = this.posY / Math.max(1, resolution.getHeight());
    }

    private class EditScreen extends HudPositionEditor {
        private EditScreen() {
            super("Drag the radar to reposition it.", 10);
        }

        @Override
        protected void syncAnchor(Window window) {
            Radar.this.syncPositionToResolution(window);
        }

        @Override
        protected float anchorX() {
            return Radar.this.posX;
        }

        @Override
        protected float anchorY() {
            return Radar.this.posY;
        }

        @Override
        protected void drawPreview(float x, float y, Window window) {
            Radar.this.setAbsolutePosition(x, y, window);
            Radar.this.renderRadar(true);
        }

        @Override
        protected boolean hitsPreview(int mouseX, int mouseY) {
            float size = Radar.this.getRadarSize();
            return mouseX >= Radar.this.posX
                    && mouseX <= Radar.this.posX + size
                    && mouseY >= Radar.this.posY
                    && mouseY <= Radar.this.posY + size;
        }

        @Override
        protected void resetAnchor() {
            Radar.this.resetPosition();
        }
    }
}
