package keystrokesmod.mixin.impl.client;

import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.module.impl.player.Freecam;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.client.entity.living.player.KeyboardInput;
import net.minecraft.client.options.GameOptions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(value = KeyboardInput.class, priority = 999)
public class MixinMovementInputFromOptions extends Input {
    @Shadow
    @Final
    private GameOptions options;

    @Overwrite
    public void tick() {
        this.movementSideways = 0.0F;
        this.movementForward = 0.0F;
        if (this.options.forwardKey.isPressed()) {
            this.movementForward++;
        }

        if (this.options.backKey.isPressed()) {
            this.movementForward--;
        }

        if (this.options.leftKey.isPressed()) {
            this.movementSideways++;
        }

        if (this.options.rightKey.isPressed()) {
            this.movementSideways--;
        }

        this.jumping = this.options.jumpKey.isPressed();
        this.sneaking = this.options.sneakKey.isPressed();
        PrePlayerInputEvent moveInputEvent =
                new PrePlayerInputEvent(this.movementForward, this.movementSideways, this.jumping, this.sneaking, 0.3);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(moveInputEvent);
        double sneakMultiplier = moveInputEvent.getSneakSlowDownMultiplier();
        this.movementForward = moveInputEvent.getForward();
        this.movementSideways = moveInputEvent.getStrafe();
        this.jumping = moveInputEvent.isJump();
        this.sneaking = moveInputEvent.isSneak();
        if (this.sneaking) {
            this.movementSideways = (float) (this.movementSideways * sneakMultiplier);
            this.movementForward = (float) (this.movementForward * sneakMultiplier);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void onUpdatePlayerMoveState(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostPlayerInputEvent());
        if (Freecam.freeEntity != null) {
            this.movementForward = 0.0F;
            this.movementSideways = 0.0F;
            this.jumping = false;
            this.sneaking = false;
        }
    }
}
