package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.player.PlayerEntity;

public class TPAura extends Module {
    private SliderSetting range;
    private ButtonSetting weaponOnly;
    private double x = 0.0;
    private double z = 0.0;
    private double y = 0.0;

    public TPAura() {
        super("TPAura", Module.category.combat);
        this.registerSetting(this.range = new SliderSetting("Range", 0.0, 0.0, 50.0, 1.0));
        this.registerSetting(this.weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public void onEnable() {
        if (this.range.getInput() == 0.0) {
            Utils.sendMessage("&cTPAura range values are set to 0.");
            this.disable();
        } else {
            this.updatePosition();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onLivingUpdate(ForgeEvents.LivingEvent.LivingUpdateEvent e) {
        if (Utils.nullCheck() && mc.player.damagedTime > 0 && mc.player.damagedTimer == mc.player.damagedTime) {
            this.updatePosition();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!this.weaponOnly.isToggled() || Utils.holdingWeapon()) {
            double rangeSq = this.range.getInput() * this.range.getInput();

            for (PlayerEntity entityPlayer : mc.world.players) {
                if (entityPlayer != mc.player
                        && entityPlayer.deathTicks == 0
                        && !(mc.player.squaredDistanceTo(entityPlayer) > rangeSq)
                        && !AntiBot.isBot(entityPlayer)
                        && !Utils.isFriended(entityPlayer)) {
                    mc.player.setPosition(entityPlayer.x + this.x, entityPlayer.y + this.y, entityPlayer.z + this.z);
                    Utils.attackEntity(entityPlayer, true, false);
                    break;
                }
            }
        }
    }

    private void updatePosition() {
        this.x = Utils.randomizeInt(-15, 15) / 10.0;
        this.y = Utils.randomizeInt(10, 15) / 10.0;
        this.z = Utils.randomizeInt(-15, 15) / 10.0;
    }
}
