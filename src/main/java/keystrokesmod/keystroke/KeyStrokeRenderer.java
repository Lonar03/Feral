package keystrokesmod.keystroke;

import java.awt.Color;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;

public class KeyStrokeRenderer {
    private static final int[] a = new int[] {16777215, 16711680, 65280, 255, 16776960, 11141290};
    private Minecraft mc = Minecraft.getInstance();
    private KeyStrokeKeyRenderer[] b = new KeyStrokeKeyRenderer[4];
    private KeyStrokeMouse[] c = new KeyStrokeMouse[2];

    public KeyStrokeRenderer() {
        this.b[0] = new KeyStrokeKeyRenderer(this.mc.options.forwardKey, 26, 2);
        this.b[1] = new KeyStrokeKeyRenderer(this.mc.options.backKey, 26, 26);
        this.b[2] = new KeyStrokeKeyRenderer(this.mc.options.leftKey, 2, 26);
        this.b[3] = new KeyStrokeKeyRenderer(this.mc.options.rightKey, 50, 26);
        this.c[0] = new KeyStrokeMouse(0, 2, 50);
        this.c[1] = new KeyStrokeMouse(1, 38, 50);
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (this.mc.screen != null) {
            if (this.mc.screen instanceof KeyStrokeConfigGui) {
                ClientPlatform.handleInput(this.mc.screen);
            }
        } else if (this.mc.focused && !this.mc.options.debugEnabled) {
            this.renderKeystrokes();
        }
    }

    public void renderKeystrokes() {
        if (KeyStroke.e) {
            int x = KeyStroke.x;
            int y = KeyStroke.y;
            int g = this.getColor(KeyStroke.currentColorNumber);
            boolean h = KeyStroke.d;
            Window res = new Window(this.mc);
            int width = 74;
            int height = h ? 74 : 50;
            if (x < 0) {
                KeyStroke.x = 0;
                x = KeyStroke.x;
            } else if (x > res.getWidth() - width) {
                KeyStroke.x = res.getWidth() - width;
                x = KeyStroke.x;
            }

            if (y < 0) {
                KeyStroke.y = 0;
                y = KeyStroke.y;
            } else if (y > res.getHeight() - height) {
                KeyStroke.y = res.getHeight() - height;
                y = KeyStroke.y;
            }

            this.drawMovementKeys(x, y, g);
            if (h) {
                this.drawMouseButtons(x, y, g);
            }
        }
    }

    private int getColor(int index) {
        return index == 6
                ? Color.getHSBColor((float) (System.currentTimeMillis() % 3750L) / 3750.0F, 1.0F, 1.0F)
                        .getRGB()
                : a[index];
    }

    private void drawMovementKeys(int x, int y, int textColor) {
        for (KeyStrokeKeyRenderer key : this.b) {
            key.renderKey(x, y, textColor);
        }
    }

    private void drawMouseButtons(int x, int y, int textColor) {
        for (KeyStrokeMouse button : this.c) {
            button.n(x, y, textColor);
        }
    }
}
