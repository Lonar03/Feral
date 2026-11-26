package keystrokesmod.utility;

import java.util.function.IntSupplier;
import keystrokesmod.clickgui.HudPositionEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;

/** The independently positioned block counter shared by the player modules. */
public final class BlockCountHud {
    private final Minecraft mc;
    private final IntSupplier count;
    private float posX = Float.NaN;
    private float posY = Float.NaN;
    private float relativePosX = Float.NaN;
    private float relativePosY = Float.NaN;

    public BlockCountHud(Minecraft mc, IntSupplier count) {
        this.mc = mc;
        this.count = count;
    }

    public float getPosX() {
        syncPosition();
        return posX;
    }

    public float getPosY() {
        syncPosition();
        return posY;
    }

    public float getRelativePosX() {
        syncPosition();
        return relativePosX;
    }

    public float getRelativePosY() {
        syncPosition();
        return relativePosY;
    }

    public void setRelativePosition(float normalizedX, float normalizedY) {
        relativePosX = normalizedX;
        relativePosY = normalizedY;
        syncPosition();
    }

    public void setAbsolutePosition(float absoluteX, float absoluteY) {
        setAbsolutePosition(absoluteX, absoluteY, new Window(mc));
    }

    private void setAbsolutePosition(float absoluteX, float absoluteY, Window resolution) {
        posX = absoluteX;
        posY = absoluteY;
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        relativePosX = absoluteX / w;
        relativePosY = absoluteY / h;
    }

    private void resetPosition() {
        relativePosX = Float.NaN;
        relativePosY = Float.NaN;
        posX = Float.NaN;
        posY = Float.NaN;
        syncPosition();
    }

    private void syncPosition() {
        syncPosition(new Window(mc));
    }

    private void syncPosition(Window resolution) {
        int w = Math.max(1, resolution.getWidth());
        int h = Math.max(1, resolution.getHeight());
        if (Float.isNaN(relativePosX) || Float.isNaN(relativePosY)) {
            if (Float.isNaN(posX) || Float.isNaN(posY)) {
                posX = w / 2.0F + 9.0F;
                posY = h / 2.0F - mc.textRenderer.fontHeight / 2.0F + 1.0F;
            }
            relativePosX = posX / w;
            relativePosY = posY / h;
        }
        posX = relativePosX * w;
        posY = relativePosY * h;
    }

    public void render(int alpha) {
        render(alpha, false);
    }

    private void render(int alpha, boolean editing) {
        int blocks = count.getAsInt();
        if (editing && blocks == 0) {
            blocks = 64;
        }
        String color = "§";
        if (blocks <= 5) {
            color = color + "c";
        } else if (blocks <= 15) {
            color = color + "6";
        } else if (blocks <= 25) {
            color = color + "e";
        } else {
            color = "";
        }
        int colorAlpha = Utils.mergeAlpha(-1, alpha);
        Window scaledResolution = new Window(mc);
        syncPosition(scaledResolution);
        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        mc.textRenderer.drawWithShadow(color + blocks + " §rblock" + (blocks == 1 ? "" : "s"), posX, posY, colorAlpha);
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    public Screen editScreen() {
        return new EditScreen();
    }

    private class EditScreen extends HudPositionEditor {
        private EditScreen() {
            super("Edit the position by dragging.", 20);
        }

        @Override
        protected void syncAnchor(Window window) {
            syncPosition(window);
        }

        @Override
        protected float anchorX() {
            return posX;
        }

        @Override
        protected float anchorY() {
            return posY;
        }

        @Override
        protected void drawPreview(float x, float y, Window window) {
            setAbsolutePosition(x, y, window);
            BlockCountHud.this.render(255, true);
        }

        @Override
        protected boolean hitsPreview(int mouseX, int mouseY) {
            int blocks = count.getAsInt();
            if (blocks == 0) {
                blocks = 64;
            }
            String text = blocks + " block" + (blocks == 1 ? "" : "s");
            int textWidth = this.textRenderer.getWidth(text);
            int textHeight = this.textRenderer.fontHeight;
            float minX = posX - 2.0F;
            float minY = posY - 2.0F;
            float maxX = posX + textWidth + 2.0F;
            float maxY = posY + textHeight + 2.0F;
            return mouseX >= minX && mouseX <= maxX && mouseY >= minY && mouseY <= maxY;
        }

        @Override
        protected void resetAnchor() {
            resetPosition();
        }
    }
}
