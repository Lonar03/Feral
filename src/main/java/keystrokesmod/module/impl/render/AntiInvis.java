// Ported from Meowtils (wtf.tatp.meowtils), modified for Meower and Feral
package keystrokesmod.module.impl.render;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderPlayerEvent.Post;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderPlayerEvent.Pre;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

/** Renders invisible players semi-transparent instead of hiding them. */
public class AntiInvis extends Module {
    private final SliderSetting opacity;
    // Players we flipped to visible for this frame, plus whether we turned blending on for them.
    private final Set<PlayerEntity> revealed = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean blending;

    public AntiInvis() {
        super("AntiInvis", Module.category.render);
        this.registerSetting(this.opacity = new SliderSetting("Opacity", "%", 50.0, 0.0, 100.0, 1.0));
    }

    // LOWEST so other Pre handlers (e.g. Chams hide players) can cancel first; a cancelled
    // render never fires Post, so we'd leave the player visible.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPreRender(Pre e) {
        if (e.isCanceled() || !(e.entity instanceof PlayerEntity)) {
            return;
        }

        PlayerEntity player = e.entityPlayer;
        if (player == mc.player || !player.isInvisible() || AntiBot.isBot(player)) {
            return;
        }

        player.setInvisible(false);
        this.revealed.add(player);
        float a = (float) (this.opacity.getInput() / 100.0);
        if (a < 1.0F) {
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Raw GL on purpose: GlStateManager.color4f(1,1,1,1) calls during the model render get
            // skipped by its cache, so our alpha survives the whole player render.
            GL11.glColor4f(1.0F, 1.0F, 1.0F, a);
            this.blending = true;
        }
    }

    @SubscribeEvent
    public void onPostRender(Post e) {
        PlayerEntity player = e.entityPlayer;
        if (!this.revealed.remove(player)) {
            return;
        }

        player.setInvisible(true);
        if (this.blending) {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            this.blending = false;
        }
    }

    @Override
    public void onDisable() {
        for (PlayerEntity player : this.revealed) {
            player.setInvisible(true);
        }

        this.revealed.clear();
        this.blending = false;
    }
}
