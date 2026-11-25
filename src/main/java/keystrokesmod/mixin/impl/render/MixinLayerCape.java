package keystrokesmod.mixin.impl.render;

import keystrokesmod.module.impl.client.Settings;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.CapeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerRenderer;
import net.minecraft.client.render.entity.layer.CapeLayer;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(CapeLayer.class)
public class MixinLayerCape {
    @Shadow
    private final PlayerRenderer parent;

    public MixinLayerCape(PlayerRenderer playerRendererIn) {
        this.parent = playerRendererIn;
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;isModelPartVisible(Lnet/minecraft/client/render/model/PlayerModelPart;)Z"))
    private boolean modifyIsWearing(ClientPlayerEntity player, PlayerModelPart part) {
        return player.equals(Minecraft.getInstance().player) && this.getSelectedCape() != null
                ? true
                : player.isModelPartVisible(part);
    }

    @Redirect(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;getCapeTextureLocation()Lnet/minecraft/resource/Identifier;"))
    private Identifier modifyGetLocationCape(ClientPlayerEntity player) {
        Identifier selectedCape = this.getSelectedCape();
        return player.equals(Minecraft.getInstance().player) && selectedCape != null
                ? selectedCape
                : player.getCapeTextureLocation();
    }

    private Identifier getSelectedCape() {
        return Settings.customCapes == null ? null : CapeManager.getCape((int) Settings.customCapes.getInput());
    }
}
