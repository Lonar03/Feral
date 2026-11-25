package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;

public class RodAimbot extends Module {
    private SliderSetting fov;
    private SliderSetting predicatedTicks;
    private SliderSetting distance;
    private ButtonSetting aimInvis;
    private ButtonSetting ignoreTeammates;
    public boolean rotate;
    private boolean rightClick;
    private PlayerEntity entity;

    public RodAimbot() {
        super("RodAimbot", Module.category.combat, 0);
        this.registerSetting(this.fov = new SliderSetting("FOV", 180.0, 30.0, 360.0, 4.0));
        this.registerSetting(this.predicatedTicks = new SliderSetting("Predicted ticks", 5.0, 0.0, 20.0, 1.0));
        this.registerSetting(this.distance = new SliderSetting("Distance", 6.0, 3.0, 30.0, 0.5));
        this.registerSetting(this.aimInvis = new ButtonSetting("Aim invis", false));
        this.registerSetting(this.ignoreTeammates = new ButtonSetting("Ignore teammates", false));
    }

    @Override
    public void onDisable() {
        this.rotate = false;
        this.rightClick = false;
        this.entity = null;
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent mouseEvent) {
        if (mouseEvent.button == 1 && mouseEvent.buttonstate && Utils.nullCheck() && mc.screen == null) {
            if (mc.player.getItemInHand() != null
                    && mc.player.getItemInHand().getItem() instanceof FishingRodItem
                    && mc.player.fishingBobber == null) {
                this.entity = this.getEntity();
                if (this.entity != null) {
                    mouseEvent.setCanceled(true);
                    this.rightClick = true;
                    this.rotate = true;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onPreMotion(PreMotionEvent event) {
        if (Utils.nullCheck()) {
            if (this.rightClick || this.rotate) {
                if (mc.player.getItemInHand() == null
                        || !(mc.player.getItemInHand().getItem() instanceof FishingRodItem)) {
                    return;
                }

                float[] rotations =
                        RotationUtils.getRotationsPredicated(this.entity, (int) this.predicatedTicks.getInput());
                if (rotations == null) {
                    return;
                }

                event.setYaw(rotations[0]);
                event.setPitch(rotations[1]);
                if (!this.rightClick && this.rotate) {
                    this.rotate = false;
                }

                if (this.rightClick) {
                    ((IAccessorMinecraft) mc).callRightClickMouse();
                    this.rightClick = false;
                }
            }
        }
    }

    private PlayerEntity getEntity() {
        for (PlayerEntity entityPlayer : mc.world.players) {
            if (entityPlayer != mc.player
                    && entityPlayer.deathTicks == 0
                    && (this.aimInvis.isToggled() || !entityPlayer.isInvisible())
                    && !(mc.player.squaredDistanceTo(entityPlayer)
                            > this.distance.getInput() * this.distance.getInput())
                    && !Utils.isFriended(entityPlayer)) {
                float n = (float) this.fov.getInput();
                if ((n == 360.0F || Utils.inFov(n, entityPlayer))
                        && !AntiBot.isBot(entityPlayer)
                        && (!this.ignoreTeammates.isToggled() || !Utils.isTeammate(entityPlayer))) {
                    return entityPlayer;
                }
            }
        }

        return null;
    }
}
