package keystrokesmod.module.impl.render;

import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.platform.ornithe.ForgeEvents.EventPriority;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import net.minecraft.entity.living.LivingEntity;

public class NoRender extends Module {
    public NoRender() {
        super("No Render", Module.category.render);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderLiving(ForgeEvents.RenderLivingEvent.Pre<?> event) {
        if (event.entity instanceof LivingEntity) {
            LivingEntity entity = event.entity;
            if (entity != mc.player && !entity.isAlive()) {
                event.setCanceled(true);
            }
        }
    }
}
