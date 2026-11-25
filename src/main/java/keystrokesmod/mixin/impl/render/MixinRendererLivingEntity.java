package keystrokesmod.mixin.impl.render;

import java.awt.Color;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.other.AccountHider;
import keystrokesmod.module.impl.render.DamageTint;
import keystrokesmod.module.impl.render.MobESP;
import keystrokesmod.module.impl.render.PlayerESP;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.GhostHandRenderState;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@ForgeEvents.SideOnly(ForgeEvents.Side.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class MixinRendererLivingEntity<T extends LivingEntity> extends EntityRenderer<T> {
    @Shadow
    protected boolean renderMarker;

    @Unique
    private boolean ghostHand$renderingTransparent;

    @Unique
    private LivingEntity nameHider$renderNameEntity;

    @Unique
    private LivingEntity damageTint$entity;

    @Shadow
    protected abstract void tearDownOverlayColor();

    protected MixinRendererLivingEntity(EntityRenderDispatcher renderManager) {
        super(renderManager);
    }

    @Inject(
            method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;F)Z"))
    private void ghostHand$beginTransparency(
            T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!this.renderMarker
                && !PlayerESP.renderingOutlinePass
                && !MobESP.renderingOutlinePass
                && ModuleManager.ghostHand != null) {
            this.ghostHand$renderingTransparent =
                    GhostHandRenderState.begin(ModuleManager.ghostHand.getRenderOpacity(entity, partialTicks));
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
            at = {
                @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;enableCull()V"),
                @At("RETURN")
            })
    private void ghostHand$endTransparency(
            T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (this.ghostHand$renderingTransparent) {
            GhostHandRenderState.end();
            this.ghostHand$renderingTransparent = false;
        }
    }

    @Unique
    private boolean shouldRender() {
        return ModuleManager.playerESP != null
                && ModuleManager.playerESP.isEnabled()
                && ModuleManager.playerESP.outline.isToggled();
    }

    @Redirect(
            method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupSolidState(Lnet/minecraft/entity/living/LivingEntity;)Z"))
    private boolean setOutlineColor(LivingEntityRenderer instance, T entityLivingBaseIn) {
        int i = 16777215;
        boolean drawOutline = this.shouldRender()
                && (entityLivingBaseIn != Minecraft.getInstance().player && !AntiBot.isBot(entityLivingBaseIn)
                        || entityLivingBaseIn == Minecraft.getInstance().player
                                && ModuleManager.playerESP.renderSelf.isToggled());
        if (drawOutline && !ModuleManager.playerESP.teamColor.isToggled()) {
            if (ModuleManager.playerESP.rainbow.isToggled()) {
                i = Utils.getChroma(2L, 0L);
            } else {
                i = ModuleManager.playerESP.color.getColor();
            }
        } else if (entityLivingBaseIn instanceof PlayerEntity) {
            Team scoreplayerteam = (Team) entityLivingBaseIn.getScoreboardTeam();
            if (scoreplayerteam != null) {
                String s = TextRenderer.isolateFormatting(scoreplayerteam.getPrefix());
                if (s.length() >= 2) {
                    i = this.getTextRenderer().getColor(s.charAt(1));
                }
            }
        }

        if (drawOutline && ModuleManager.playerESP.redOnDamage.isToggled() && entityLivingBaseIn.damagedTimer != 0) {
            i = Color.RED.getRGB();
        }

        if (drawOutline) {
            return false;
        }

        float f1 = (i >> 16 & 0xFF) / 255.0F;
        float f2 = (i >> 8 & 0xFF) / 255.0F;
        float f = (i & 0xFF) / 255.0F;
        GlStateManager.disableLighting();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.color4f(f1, f2, f, 1.0F);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        return true;
    }

    @ModifyVariable(method = "renderModel", at = @At("STORE"), ordinal = 0)
    private boolean modifyInvisibleFlag(boolean flag) {
        return flag || this.renderMarker && this.shouldRender() && ModuleManager.playerESP.showInvis.isToggled();
    }

    @Inject(
            method = "shouldRenderNameTag(Lnet/minecraft/entity/living/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void suppressNameDuringOutlinePass(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (PlayerESP.renderingOutlinePass || MobESP.renderingOutlinePass) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("HEAD"))
    private void mobEsp$chamsPre(
            T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            MobESP.onRenderMobPre(entity);
        }
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("RETURN"))
    private void mobEsp$chamsPost(
            T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof PlayerEntity)) {
            MobESP.onRenderMobPost();
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("HEAD"))
    private void nameHider$captureRenderEntity(T entity, double x, double y, double z, CallbackInfo ci) {
        this.nameHider$renderNameEntity = entity;
    }

    @ModifyVariable(
            method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
            at = @At("STORE"),
            ordinal = 0)
    private String nameHider$hideRenderedName(String displayName) {
        if (displayName == null || ModuleManager.accountHider == null || !ModuleManager.accountHider.isEnabled()) {
            return displayName;
        } else if (this.nameHider$renderNameEntity instanceof PlayerEntity) {
            PlayerEntity entityPlayer = (PlayerEntity) (Object) this.nameHider$renderNameEntity;
            return AccountHider.getPlayerDisplayName(entityPlayer, entityPlayer.getDisplayName())
                    .getFormattedString();
        } else {
            return AccountHider.getFakeName(displayName);
        }
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V", at = @At("RETURN"))
    private void nameHider$clearRenderEntity(T entity, double x, double y, double z, CallbackInfo ci) {
        this.nameHider$renderNameEntity = null;
    }

    @Inject(method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z", at = @At("HEAD"))
    private void damageTint$captureEntity(
            T entitylivingbaseIn, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        this.damageTint$entity = entitylivingbaseIn;
    }

    @ModifyArg(
            method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z",
            at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0))
    private float damageTint$modifyRed(float f) {
        return DamageTint.instance != null ? DamageTint.instance.color.getRed() / 255.0F : f;
    }

    @ModifyArg(
            method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z",
            at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1))
    private float damageTint$modifyGreen(float f) {
        return DamageTint.instance != null ? DamageTint.instance.color.getGreen() / 255.0F : f;
    }

    @ModifyArg(
            method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z",
            at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2))
    private float damageTint$modifyBlue(float f) {
        return DamageTint.instance != null ? DamageTint.instance.color.getBlue() / 255.0F : f;
    }

    @ModifyArg(
            method = "setupOverlayColor(Lnet/minecraft/entity/living/LivingEntity;FZ)Z",
            at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3))
    private float damageTint$modifyAlpha(float f) {
        return DamageTint.instance != null ? DamageTint.computeAlpha(this.damageTint$entity) : f;
    }

    @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("RETURN"))
    private void damageTint$restoreRenderState(
            T entity, double x, double y, double z, float entityYaw, float partialTicks, CallbackInfo ci) {
        if (DamageTint.instance != null) {
            this.tearDownOverlayColor();
            GlStateManager.activeTexture(GLX.GL_TEXTURE1);
            GlStateManager.enableTexture();
            GlStateManager.activeTexture(GLX.GL_TEXTURE0);
            GlStateManager.enableTexture();
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.damageTint$entity = null;
        }
    }
}
