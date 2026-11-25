package keystrokesmod.mixin.impl.world;

import keystrokesmod.mixin.interfaces.IMixinExplosion;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public class MixinExplosion implements IMixinExplosion {
    @Unique
    private boolean feral$hideEffects;

    @Override
    public void hideEffects() {
        this.feral$hideEffects = true;
    }

    @Redirect(
            method = "damageBlocks",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;playSound(DDDLjava/lang/String;FF)V"))
    private void feral$playExplosionSound(
            World world, double x, double y, double z, String sound, float volume, float pitch) {
        if (!this.feral$hideEffects) {
            world.playSound(x, y, z, sound, volume, pitch);
        }
    }

    @Redirect(
            method = "damageBlocks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/world/World;addParticle(Lnet/minecraft/entity/particle/ParticleType;DDDDDD[I)V"))
    private void feral$addExplosionParticle(
            World world,
            ParticleType type,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            int[] parameters) {
        if (!this.feral$hideEffects) {
            world.addParticle(type, x, y, z, velocityX, velocityY, velocityZ, parameters);
        }
    }
}
