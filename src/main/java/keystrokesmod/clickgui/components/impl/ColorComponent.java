package keystrokesmod.clickgui.components.impl;

import java.awt.Color;
import keystrokesmod.Feral;
import keystrokesmod.clickgui.ClickGui;
import keystrokesmod.clickgui.ColorPickerState;
import keystrokesmod.clickgui.GuiText;
import keystrokesmod.clickgui.PickerGradient;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.module.setting.impl.ColorSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.font.FeralFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import org.lwjgl.opengl.GL11;

public class ColorComponent extends Component {
    public ColorSetting colorSetting;
    private ModuleComponent moduleComponent;
    public float o;
    public float x;
    private float y;
    public float xOffset;
    public boolean expanded;
    private int dragMode;
    private final ColorPickerState picker = new ColorPickerState();
    private Timer smoothTimer;
    private float animationProgress;
    private float animationStartProgress;
    private float animationTargetProgress;

    public ColorComponent(ColorSetting colorSetting, ModuleComponent moduleComponent, float o) {
        this.colorSetting = colorSetting;
        this.moduleComponent = moduleComponent;
        this.o = o;
        this.animationProgress = 0.0F;
        this.animationStartProgress = 0.0F;
        this.animationTargetProgress = 0.0F;
    }

    public float getExpandedHeight() {
        return 66.0F;
    }

    public float getAnimationProgress() {
        if (this.smoothTimer != null) {
            if ((float) (System.currentTimeMillis() - this.smoothTimer.last) >= 280.0F) {
                this.smoothTimer = null;
                this.animationProgress = this.animationTargetProgress;
                this.animationStartProgress = this.animationTargetProgress;
            } else {
                this.animationProgress =
                        this.smoothTimer.getValueFloat(this.animationStartProgress, this.animationTargetProgress, 1);
                if (this.animationProgress == this.animationTargetProgress) {
                    this.smoothTimer = null;
                    this.animationStartProgress = this.animationTargetProgress;
                }
            }
        }

        return this.animationProgress;
    }

    @Override
    public void render() {
        float cx = this.moduleComponent.categoryComponent.getX();
        float cy = this.moduleComponent.categoryComponent.getY();
        float cw = this.moduleComponent.categoryComponent.getWidth();
        float boxX = cx + 4.0F + this.xOffset / 2.0F;
        float boxY = cy + this.o + 3.0F;
        RenderUtils.drawRect(boxX - 0.5, boxY - 0.5, boxX + 5.0F + 0.5, boxY + 5.0F + 0.5, -12829626);
        if (this.colorSetting.hasAlpha()) {
            int checkSize = 2;

            for (float px = boxX; px < boxX + 5.0F; px += checkSize) {
                for (float py = boxY; py < boxY + 5.0F; py += checkSize) {
                    int col = ((int) ((px - boxX) / checkSize) + (int) ((py - boxY) / checkSize)) % 2 == 0
                            ? -10066330
                            : -6710887;
                    RenderUtils.drawRect(
                            px, py, Math.min(px + checkSize, boxX + 5.0F), Math.min(py + checkSize, boxY + 5.0F), col);
                }
            }
        }

        RenderUtils.drawRect(boxX, boxY, boxX + 5.0F, boxY + 5.0F, this.colorSetting.getColor());
        FeralFontRenderer renderer = Gui.getClickGuiSettingFontRenderer();
        GL11.glPushMatrix();
        GL11.glScaled(0.5, 0.5, 0.5);
        float textOffset = 17f;
        renderer.drawString(
                GuiText.fit(renderer, this.colorSetting.getName(), (cw - 16f) * 2f - this.xOffset - textOffset),
                (cx + 4.0F) * 2.0F + this.xOffset + textOffset,
                (cy + this.o + 6f) * 2f - renderer.getFontHeight() / 2f,
                -1,
                true);
        GL11.glPopMatrix();
        float progress = this.getAnimationProgress();
        if (!(progress <= 0.0F)) {
            float scrollOffset = this.moduleComponent.categoryComponent.moduleY - cy;
            float contentTopScreen = cy + this.o + 12.0F + scrollOffset;
            float revealH = (this.getExpandedHeight() - 12.0F) * progress;
            RenderUtils.scissorPushGui(cx, contentTopScreen, cw, revealH);
            this.renderPickerContent(cx, cy);
            RenderUtils.scissorPop();
        }
    }

    private void renderPickerContent(float cx, float cy) {
        float areaLeft = cx + 4.0F + this.xOffset / 2.0F;
        float sqTop = cy + this.o + 12.0F + 2.0F;
        float sqRight = areaLeft + 50.0F;
        float sqBottom = sqTop + 50.0F;
        picker.sync(colorSetting);
        float hue = picker.hue() / 360f;
        float pixels = (float) (ClickGui.getActiveRenderScale() * new Window(Minecraft.getInstance()).getScale());
        int hueRGB = Color.HSBtoRGB(hue, 1.0F, 1.0F) | 0xFF000000;
        if (!PickerGradient.draw(
                PickerGradient.SATURATION_VALUE, areaLeft, sqTop, 50f, 50f, 2f, pixels, 1f, hueRGB, 1f)) {
            RenderUtils.drawHorizontalGradientRect(areaLeft, sqTop, sqRight, sqBottom, -1, hueRGB);
            RenderUtils.drawVerticalGradientRect(areaLeft, sqTop, sqRight, sqBottom, 0, -16777216);
        }
        float indX = areaLeft + 2f + picker.saturation() * 46f;
        float indY = sqTop + 2f + (1f - picker.brightness()) * 46f;
        RenderUtils.drawRoundedRectangle(indX - 2f, indY - 2f, indX + 2f, indY + 2f, 2f, -1);
        RenderUtils.drawRoundedRectangle(indX - 1f, indY - 1f, indX + 1f, indY + 1f, 1f, 0xFF222222);
        float hueLeft = sqRight + 4.0F;
        float hueRight = hueLeft + 10.0F;
        if (!PickerGradient.draw(PickerGradient.HUE, hueLeft, sqTop, 10f, 50f, 2f, pixels, 1f, 0, 1f)) {
            for (int i = 0; i < 6; i++) {
                RenderUtils.drawVerticalGradientRect(
                        hueLeft,
                        sqTop + i * 50f / 6f,
                        hueRight,
                        sqTop + (i + 1) * 50f / 6f,
                        Color.HSBtoRGB(i / 6f, 1f, 1f),
                        Color.HSBtoRGB((i + 1) / 6f, 1f, 1f));
            }
        }

        drawMarker(hueLeft, hueRight, sqTop, hue);
        if (this.colorSetting.hasAlpha()) {
            float alphaLeft = hueRight + 4.0F;
            float alphaRight = alphaLeft + 10.0F;
            if (!PickerGradient.draw(
                    PickerGradient.ALPHA, alphaLeft, sqTop, 10f, 50f, 2f, pixels, 4f, colorSetting.getColor(), 1f)) {
                RenderUtils.drawVerticalGradientRect(
                        alphaLeft, sqTop, alphaRight, sqBottom, 0xFF666666, 0xFF000000 | colorSetting.getRGB());
            }
            drawMarker(alphaLeft, alphaRight, sqTop, colorSetting.getAlpha() / 255f);
        }
    }

    private static void drawMarker(float left, float right, float top, float value) {
        float y = top + Math.max(1f, Math.min(49f, value * 50f));
        RenderUtils.drawRect(left, y - 1f, right, y + 1f, 0xFF222222);
        RenderUtils.drawRect(left + 1f, y - 0.5f, right - 1f, y + 0.5f, -1);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        this.y = this.moduleComponent.categoryComponent.getModuleY() + this.o;
        this.x = this.moduleComponent.categoryComponent.getX();
        if (this.dragMode != 0 && !(this.getAnimationProgress() < 1.0F)) {
            float areaLeft = this.x + 4.0F + this.xOffset / 2.0F;
            float sqTop = this.y + 12.0F + 2.0F;
            int before = colorSetting.getColor();
            if (this.dragMode == 1) {
                picker.setSaturationValue(
                        colorSetting,
                        Math.max(0f, Math.min(1f, (mouseX - areaLeft) / 50f)),
                        Math.max(0f, Math.min(1f, 1f - (mouseY - sqTop) / 50f)));
            } else if (this.dragMode == 2) {
                picker.setHue(colorSetting, Math.max(0f, Math.min(360f, (mouseY - sqTop) / 50f * 360f)));
            } else if (this.dragMode == 3 && this.colorSetting.hasAlpha()) {
                float a = Math.max(0.0F, Math.min(1.0F, (mouseY - sqTop) / 50.0F));
                this.colorSetting.setAlpha((int) (a * 255.0F));
            }
            if (before != colorSetting.getColor()) {
                this.markUnsaved();
            }
        }
    }

    @Override
    public boolean onClick(int mouseX, int mouseY, int button) {
        if (this.moduleComponent.isOpened && this.moduleComponent.isVisible(this)) {
            float cw = this.moduleComponent.categoryComponent.getWidth();
            if (!(mouseX > this.x)
                    || !(mouseX < this.x + cw)
                    || !(mouseY > this.y)
                    || !(mouseY < this.y + 12.0F)
                    || button != 0 && button != 1) {
                if (button != 0) {
                    return false;
                }

                if (this.getAnimationProgress() < 1.0F) {
                    return false;
                }

                float areaLeft = this.x + 4.0F + this.xOffset / 2.0F;
                float sqTop = this.y + 12.0F + 2.0F;
                float sqRight = areaLeft + 50.0F;
                float sqBottom = sqTop + 50.0F;
                float hueLeft = sqRight + 4.0F;
                float hueRight = hueLeft + 10.0F;
                if (mouseX >= areaLeft && mouseX <= sqRight && mouseY >= sqTop && mouseY <= sqBottom) {
                    picker.sync(colorSetting);
                    this.dragMode = 1;
                    drawScreen(mouseX, mouseY);
                    return false;
                }

                if (mouseX >= hueLeft - 2.0F && mouseX <= hueRight + 2.0F && mouseY >= sqTop && mouseY <= sqBottom) {
                    picker.sync(colorSetting);
                    this.dragMode = 2;
                    drawScreen(mouseX, mouseY);
                    return false;
                }

                if (this.colorSetting.hasAlpha()) {
                    float alphaLeft = hueRight + 4.0F;
                    float alphaRight = alphaLeft + 10.0F;
                    if (mouseX >= alphaLeft - 2.0F
                            && mouseX <= alphaRight + 2.0F
                            && mouseY >= sqTop
                            && mouseY <= sqBottom) {
                        picker.sync(colorSetting);
                        this.dragMode = 3;
                        drawScreen(mouseX, mouseY);
                        return false;
                    }
                }

                return false;
            } else {
                float currentProgress = this.getAnimationProgress();
                this.animationStartProgress = currentProgress;
                this.expanded = !this.expanded;
                this.animationTargetProgress = this.expanded ? 1.0F : 0.0F;
                (this.smoothTimer = new Timer(250.0F)).start();
                this.moduleComponent.updateSettingPositions();
                return true;
            }
        } else {
            return false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0) {
            this.dragMode = 0;
        }
    }

    @Override
    public void onGuiClosed() {
        this.dragMode = 0;
        this.smoothTimer = null;
        this.animationProgress = this.expanded ? 1.0F : 0.0F;
        this.animationStartProgress = this.animationProgress;
        this.animationTargetProgress = this.animationProgress;
    }

    @Override
    public void updateHeight(float n) {
        this.o = n;
    }

    @Override
    public float getOffset() {
        return this.o;
    }

    @Override
    public boolean isBaseVisible() {
        return this.colorSetting.visible;
    }

    public void restoreExpandedState(boolean expanded) {
        this.expanded = expanded;
        this.smoothTimer = null;
        this.animationProgress = expanded ? 1.0F : 0.0F;
        this.animationStartProgress = this.animationProgress;
        this.animationTargetProgress = this.animationProgress;
    }

    private void markUnsaved() {
        if (Feral.currentProfile != null) {
            Feral.currentProfile.getModule().saved = false;
        }
    }
}
