package keystrokesmod.keystroke;

import keystrokesmod.Feral;
import keystrokesmod.helper.MouseHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

public class KeyStrokeConfigGui extends Screen {
    private static final String[] colors =
            new String[] {"White", "Red", "Green", "Blue", "Yellow", "Purple", "Rainbow"};
    private ButtonWidget modeBtn;
    private ButtonWidget textColorBtn;
    private ButtonWidget showMouseBtn;
    private ButtonWidget outlineBtn;
    private boolean d = false;
    private int lx;
    private int ly;

    public void init() {
        this.buttons.add(
                this.modeBtn = new ButtonWidget(
                        0,
                        this.width / 2 - 70,
                        this.height / 2 - 28,
                        140,
                        20,
                        "Mod: " + (KeyStroke.e ? "Enabled" : "Disabled")));
        this.buttons.add(
                this.textColorBtn = new ButtonWidget(
                        1,
                        this.width / 2 - 70,
                        this.height / 2 - 6,
                        140,
                        20,
                        "Text color: " + colors[KeyStroke.currentColorNumber]));
        this.buttons.add(
                this.showMouseBtn = new ButtonWidget(
                        2,
                        this.width / 2 - 70,
                        this.height / 2 + 16,
                        140,
                        20,
                        "Show mouse buttons: " + (KeyStroke.d ? "On" : "Off")));
        this.buttons.add(
                this.outlineBtn = new ButtonWidget(
                        3,
                        this.width / 2 - 70,
                        this.height / 2 + 38,
                        140,
                        20,
                        "Outline: " + (KeyStroke.f ? "On" : "Off")));
    }

    public void render(int mouseX, int mouseY, float partialTicks) {
        Feral.getKeyStrokeRenderer().renderKeystrokes();
        super.render(mouseX, mouseY, partialTicks);
    }

    protected void buttonClicked(ButtonWidget button) {
        if (button == this.modeBtn) {
            KeyStroke.e = !KeyStroke.e;
            this.modeBtn.message = "Mod: " + (KeyStroke.e ? "Enabled" : "Disabled");
        } else if (button == this.textColorBtn) {
            KeyStroke.currentColorNumber = KeyStroke.currentColorNumber == 6 ? 0 : KeyStroke.currentColorNumber + 1;
            this.textColorBtn.message = "Text color: " + colors[KeyStroke.currentColorNumber];
        } else if (button == this.showMouseBtn) {
            KeyStroke.d = !KeyStroke.d;
            this.showMouseBtn.message = "Show mouse buttons: " + (KeyStroke.d ? "On" : "Off");
        } else if (button == this.outlineBtn) {
            KeyStroke.f = !KeyStroke.f;
            this.outlineBtn.message = "Outline: " + (KeyStroke.f ? "On" : "Off");
        }
    }

    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button == 0) {
            MouseHelper.aL();
            int startX = KeyStroke.x;
            int startY = KeyStroke.y;
            int endX = startX + 74;
            int endY = startY + (KeyStroke.d ? 74 : 50);
            if (mouseX >= startX && mouseX <= endX && mouseY >= startY && mouseY <= endY) {
                this.d = true;
                this.lx = mouseX;
                this.ly = mouseY;
            }
        } else if (button == 1) {
            MouseHelper.aR();
        }
    }

    protected void mouseReleased(int mouseX, int mouseY, int action) {
        super.mouseReleased(mouseX, mouseY, action);
        this.d = false;
    }

    protected void mouseDragged(int mouseX, int mouseY, int lastButtonClicked, long timeSinceMouseClick) {
        super.mouseDragged(mouseX, mouseY, lastButtonClicked, timeSinceMouseClick);
        if (this.d) {
            KeyStroke.x = KeyStroke.x + mouseX - this.lx;
            KeyStroke.y = KeyStroke.y + mouseY - this.ly;
            this.lx = mouseX;
            this.ly = mouseY;
        }
    }

    public boolean shouldPauseGame() {
        return false;
    }
}
