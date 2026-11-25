package keystrokesmod.module.impl.combat;

import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.options.KeyBinding;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.HitResult;
import net.minecraft.world.HitResult.Type;

public class JumpReset extends Module {
    private SliderSetting chance;
    private ButtonSetting requireMouseDown;
    private ButtonSetting requireMovingForward;
    private ButtonSetting requireAim;
    private boolean setJump;
    private boolean ignoreNext;
    private int lastHurtTime;
    private double lastFallDistance;

    public JumpReset() {
        super("Jump Reset", Module.category.combat);
        this.liteModule = true;
        this.registerSetting(this.chance = new SliderSetting("Chance", "%", 80.0, 0.0, 100.0, 1.0));
        this.registerSetting(this.requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(this.requireMovingForward = new ButtonSetting("Require moving forward", true));
        this.registerSetting(this.requireAim = new ButtonSetting("Require aim", true));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return (int) this.chance.getInput() == 100 ? "" : (int) this.chance.getInput() + "%";
    }

    @ForgeEvents.SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        int hurtTime = mc.player.damagedTimer;
        boolean onGround = mc.player.onGround;
        if (onGround && this.lastFallDistance > 3.0 && !mc.player.abilities.canFly) {
            this.ignoreNext = true;
        }

        if (hurtTime > this.lastHurtTime) {
            boolean mouseDown = mc.options.attackKey.isPressed() || !this.requireMouseDown.isToggled();
            boolean aimingAt = !this.requireAim.isToggled() || this.checkAim();
            boolean forward = mc.options.forwardKey.isPressed() || !this.requireMovingForward.isToggled();
            boolean randomization =
                    (int) this.chance.getInput() == 100 || Utils.randomizeDouble(0.0, 100.0) < this.chance.getInput();
            boolean fov = Utils.inFov(
                    Utils.getDirection(), 330.0F, RotationUtils.deltaAngle(mc.player.velocityX, mc.player.velocityZ));
            if (!this.ignoreNext
                    && !mc.player.isOnFire()
                    && onGround
                    && aimingAt
                    && forward
                    && mouseDown
                    && randomization
                    && !this.hasBadEffect()
                    && fov) {
                KeyBinding.set(mc.options.jumpKey.getKeyCode(), this.setJump = true);
            }

            this.ignoreNext = false;
        }

        this.lastHurtTime = hurtTime;
        this.lastFallDistance = mc.player.fallDistance;
    }

    @ForgeEvents.SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (this.setJump && !Utils.jumpDown()) {
            KeyBinding.set(mc.options.jumpKey.getKeyCode(), this.setJump = false);
        }
    }

    private boolean hasBadEffect() {
        StatusEffectInstance jump = mc.player.getEffectInstance(StatusEffect.JUMP_BOOST);
        StatusEffectInstance poison = mc.player.getEffectInstance(StatusEffect.POISON);
        StatusEffectInstance wither = mc.player.getEffectInstance(StatusEffect.WITHER);
        return jump != null || poison != null || wither != null;
    }

    private boolean checkAim() {
        HitResult result = mc.crosshairTarget;
        return result != null && result.type == Type.ENTITY && result.entity instanceof PlayerEntity;
    }
}
