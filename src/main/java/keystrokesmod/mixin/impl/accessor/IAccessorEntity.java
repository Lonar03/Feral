package keystrokesmod.mixin.impl.accessor;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface IAccessorEntity {
    @Accessor("onFireTimer")
    int getFire();

    @Accessor("blocksWalkedOn")
    int getNextStepDistance();

    @Accessor("inCobweb")
    boolean getIsInWeb();
}
