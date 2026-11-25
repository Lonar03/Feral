package keystrokesmod.mixin.impl.accessor;

import net.minecraft.entity.living.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface IAccessorEntityLivingBase {
    @Accessor("jumpingCooldown")
    void setJumpTicks(int var1);

    @Accessor("jumpingCooldown")
    int getJumpTicks();
}
