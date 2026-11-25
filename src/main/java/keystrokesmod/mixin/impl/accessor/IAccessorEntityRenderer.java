package keystrokesmod.mixin.impl.accessor;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(GameRenderer.class)
public interface IAccessorEntityRenderer {
    @Invoker("setupCamera")
    void callSetupCameraTransform(float var1, int var2);

    @Invoker("loadShader")
    void callLoadShader(Identifier var1);

    @Accessor("SHADERS")
    Identifier[] getShaderResourceLocations();

    @Accessor("shadersEnabled")
    boolean getUseShader();

    @Accessor("shadersEnabled")
    void setUseShader(boolean var1);

    @Accessor("shaderIndex")
    int getShaderIndex();

    @Accessor("shaderIndex")
    void setShaderIndex(int var1);

    @Accessor("oldCamPos")
    void setThirdPersonDistance(float var1);

    @Accessor("targetEntity")
    Entity getPointedEntity();

    @Accessor("targetEntity")
    void setPointedEntity(Entity var1);
}
