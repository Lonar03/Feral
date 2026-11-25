package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.SimulatedPlayer;
import keystrokesmod.utility.Utils;

public class AutoJump extends Module {
    public AutoJump() {
        super("Auto Jump", Module.category.player);
    }

    @ForgeEvents.SubscribeEvent
    public void onPrePlayerInput(PrePlayerInputEvent e) {
        if (Utils.nullCheck() && mc.screen == null && !mc.player.abilities.flying) {
            if (mc.player.onGround) {
                SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(mc.player.input);
                sim.tick();
                if (!sim.onGround && !e.isJump()) {
                    e.setJump(true);
                }
            }
        }
    }
}
