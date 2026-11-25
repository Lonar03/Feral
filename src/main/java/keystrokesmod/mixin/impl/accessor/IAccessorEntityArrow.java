package keystrokesmod.mixin.impl.accessor;

import net.minecraft.entity.projectile.ArrowEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ArrowEntity.class)
public interface IAccessorEntityArrow {
    @Accessor("inGround")
    boolean getInGround();
}
