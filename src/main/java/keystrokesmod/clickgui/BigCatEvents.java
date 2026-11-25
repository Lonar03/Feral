package keystrokesmod.clickgui;

import keystrokesmod.event.KeyPressEvent;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.MouseEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.Phase;
import keystrokesmod.platform.ornithe.ForgeEvents.TickEvent.RenderTickEvent;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import org.lwjgl.opengl.GL11;

/** Feeds {@link BigCatGui} the same events the script engine gave the catGUI script, while its style is on. */
public class BigCatEvents {
    private final BigCatGui gui = BigCatGui.INSTANCE;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderTick(RenderTickEvent e) {
        if (e.phase != Phase.END || !Utils.nullCheck()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (!Gui.isBigCatStyle()) {
            if (this.gui.isOpen()) {
                // The style was switched from inside the window: close it. The next open uses the new style.
                this.gui.onDisable();
            }
            return;
        }

        mc.gameRenderer.setupGuiState();
        try {
            this.gui.onRenderTick(e.renderTickTime);
        } finally {
            restoreOverlayRenderState(mc);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!Gui.isBigCatStyle() || e.button == -1 && e.dwheel == 0) {
            return;
        }

        boolean state = e.button == -1 ? e.dwheel > 0 : e.buttonstate;
        if (!this.gui.onMouse(e.button, state, e.x, e.y)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onKeyPress(KeyPressEvent e) {
        if (!e.isCanceled() && Gui.isBigCatStyle() && !this.gui.onKeyPress(e.typedChar, e.keyCode)) {
            e.setCanceled(true);
        }
    }

    private static void restoreOverlayRenderState(Minecraft mc) {
        mc.gameRenderer.setupGuiState();
        GlStateManager.clearColor();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        Lighting.turnOff();
    }
}
