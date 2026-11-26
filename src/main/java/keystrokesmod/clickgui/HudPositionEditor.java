package keystrokesmod.clickgui;

import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.utility.RenderUtils;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.Window;

/** Shared drag interaction for HUD previews; each module owns its anchor, bounds and drawing. */
public abstract class HudPositionEditor extends Screen {
    private final String message;
    private final int messageOffset;
    private ButtonWidget resetButton;
    private boolean dragging;
    private float actualX;
    private float actualY;
    private float startX;
    private float startY;
    private int mouseStartX;
    private int mouseStartY;

    protected HudPositionEditor(String message, int messageOffset) {
        this.message = message;
        this.messageOffset = messageOffset;
    }

    protected abstract void syncAnchor(Window window);

    protected abstract float anchorX();

    protected abstract float anchorY();

    protected abstract void drawPreview(float x, float y, Window window);

    protected abstract boolean hitsPreview(int mouseX, int mouseY);

    protected abstract void resetAnchor();

    @Override
    public void init() {
        super.init();
        this.buttons.add(
                this.resetButton = new ButtonWidget(1, this.width - 90, this.height - 25, 85, 20, "Reset position"));
        this.syncAnchor(new Window(this.minecraft));
        this.readAnchor();
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        Window window = new Window(this.minecraft);
        if (!this.dragging) {
            this.syncAnchor(window);
            this.readAnchor();
        }
        fill(0, 0, this.width, this.height, -1308622848);
        this.drawPreview(this.actualX, this.actualY, window);
        this.readAnchor();
        int textX = window.getWidth() / 2 - this.textRenderer.getWidth(this.message) / 2;
        int textY = window.getHeight() / 2 - this.messageOffset;
        RenderUtils.drawColoredString(this.message, '-', textX, textY, 2L, 0L, true, this.minecraft.textRenderer);
        ClientPlatform.handleInput(this);
        super.render(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long timeSinceLastClick) {
        super.mouseDragged(mouseX, mouseY, button, timeSinceLastClick);
        if (button == 0) {
            if (this.dragging) {
                this.actualX = this.startX + (mouseX - this.mouseStartX);
                this.actualY = this.startY + (mouseY - this.mouseStartY);
            } else if (this.hitsPreview(mouseX, mouseY)) {
                this.dragging = true;
                this.mouseStartX = mouseX;
                this.mouseStartY = mouseY;
                this.startX = this.actualX;
                this.startY = this.actualY;
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (state == 0) {
            this.dragging = false;
        }
    }

    @Override
    public void buttonClicked(ButtonWidget button) {
        if (button == this.resetButton) {
            this.resetAnchor();
            this.readAnchor();
        }
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    private void readAnchor() {
        this.actualX = this.anchorX();
        this.actualY = this.anchorY();
    }
}
