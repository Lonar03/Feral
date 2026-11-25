package keystrokesmod.module.impl.zombies;

import keystrokesmod.mixin.impl.accessor.IAccessorEntityPlayer;
import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents.RenderWorldLastEvent;
import keystrokesmod.platform.ornithe.ForgeEvents.SubscribeEvent;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;

public class EasyRevive extends Module {
    public EasyRevive() {
        super("Easy Revive", Module.category.zombies);
    }

    @SubscribeEvent
    public void re(RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            for (Entity entity : mc.world.entities) {
                if (entity instanceof RemoteClientPlayerEntity) {
                    PlayerEntity player = (PlayerEntity) entity;
                    if (((IAccessorEntityPlayer) player).isSleepingRaw()) {
                        player.setShape(new Box(
                                player.x - 0.3,
                                player.y,
                                player.z - 0.3,
                                player.x + 0.3,
                                player.y + 1.8,
                                player.z + 0.3));
                    }
                }
            }
        }
    }
}
