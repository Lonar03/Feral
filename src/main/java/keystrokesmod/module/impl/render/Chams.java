package keystrokesmod.module.impl.render;

import java.util.HashSet;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import org.lwjgl.opengl.GL11;

public class Chams extends Module {
    private ButtonSetting ignoreBots;
    private ButtonSetting renderSelf;
    private ButtonSetting hidePlayers;
    private final HashSet<Entity> renderingPlayers = new HashSet<>();

    public Chams() {
        super("Chams", Module.category.render, 0);
        this.liteModule = true;
        this.registerSetting(this.ignoreBots = new ButtonSetting("Ignore bots", false));
        this.registerSetting(this.hidePlayers = new ButtonSetting("Hide players", false));
        this.registerSetting(this.renderSelf = new ButtonSetting("Render self", false));
    }

    public boolean shouldRenderThroughWalls(Entity entity) {
        if (this.isEnabled() && entity instanceof PlayerEntity) {
            if (entity == mc.player) {
                if (!this.renderSelf.isToggled() || mc.screen != null) {
                    return false;
                }
            } else if (this.hidePlayers.isToggled()) {
                return false;
            }

            return !this.ignoreBots.isToggled() || !AntiBot.isBot(entity);
        } else {
            return false;
        }
    }

    public boolean isRenderingThroughWalls() {
        return !this.renderingPlayers.isEmpty();
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreRender(ForgeEvents.RenderPlayerEvent.Pre e) {
        Entity entity = e.entity;
        if (entity != mc.player && this.hidePlayers.isToggled()) {
            e.setCanceled(true);
        } else {
            if (this.shouldRenderThroughWalls(entity)) {
                this.renderingPlayers.add(entity);
                GL11.glEnable(32823);
                GL11.glPolygonOffset(1.0F, -4000000.0F);
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPostRender(ForgeEvents.RenderPlayerEvent.Post e) {
        if (this.renderingPlayers.remove(e.entity) && this.renderingPlayers.isEmpty()) {
            this.resetPolygonOffset();
        }
    }

    private void resetPolygonOffset() {
        GL11.glDisable(32823);
        GL11.glPolygonOffset(1.0F, 4000000.0F);
    }

    @Override
    public void onDisable() {
        if (this.isRenderingThroughWalls()) {
            this.renderingPlayers.clear();
            this.resetPolygonOffset();
        }
    }
}
