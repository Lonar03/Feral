package keystrokesmod.mixin.impl.ornithe;

import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class OrnitheEntityHooks {
    private OrnitheEntityHooks() {}

    @Mixin(MobEntity.class)
    public static class AttackTarget {
        @Inject(method = "setAttackTarget", at = @At("RETURN"))
        private void feral$onAttackTarget(LivingEntity target, CallbackInfo ci) {
            MobEntity entity = (MobEntity) (Object) this;
            if (entity.world.isClient) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.LivingSetAttackTargetEvent(entity, target));
            }
        }
    }

    @Mixin(LivingEntity.class)
    public static class Living {
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void feral$onLivingUpdate(CallbackInfo ci) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (entity.world.isClient
                    && ForgeEvents.MinecraftForge.EVENT_BUS.post(
                            new ForgeEvents.LivingEvent.LivingUpdateEvent(entity))) {
                ci.cancel();
            }
        }

        @Inject(method = "setAttacker", at = @At("RETURN"))
        private void feral$onRevengeTarget(LivingEntity target, CallbackInfo ci) {
            LivingEntity entity = (LivingEntity) (Object) this;
            if (entity.world.isClient) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.LivingSetAttackTargetEvent(entity, target));
            }
        }
    }

    @Mixin(LivingEntityRenderer.class)
    public static class LivingRenderer {
        @Inject(
                method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V",
                at = @At("HEAD"),
                cancellable = true)
        private void feral$renderLivingPre(
                LivingEntity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
            LivingEntityRenderer<?> renderer = (LivingEntityRenderer<?>) (Object) this;
            if (ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.RenderLivingEvent.Pre(entity, renderer, x, y, z))) {
                ci.cancel();
            }
        }

        @Inject(method = "render(Lnet/minecraft/entity/living/LivingEntity;DDDFF)V", at = @At("RETURN"))
        private void feral$renderLivingPost(
                LivingEntity entity, double x, double y, double z, float yaw, float partialTicks, CallbackInfo ci) {
            LivingEntityRenderer<?> renderer = (LivingEntityRenderer<?>) (Object) this;
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.RenderLivingEvent.Post(entity, renderer, x, y, z));
        }

        @Inject(
                method = "renderNameTag(Lnet/minecraft/entity/living/LivingEntity;DDD)V",
                at = @At("HEAD"),
                cancellable = true)
        private void feral$renderNamePre(LivingEntity entity, double x, double y, double z, CallbackInfo ci) {
            LivingEntityRenderer<?> renderer = (LivingEntityRenderer<?>) (Object) this;
            if (ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.RenderLivingEvent.Specials.Pre(entity, renderer, x, y, z))) {
                ci.cancel();
            }
        }
    }

    @Mixin(PlayerEntity.class)
    public static class Player {
        @Shadow
        private int itemUseTimer;

        @Inject(method = "tick", at = @At("HEAD"))
        private void feral$playerTickStart(CallbackInfo ci) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            if (player.world.isClient) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(
                        new ForgeEvents.TickEvent.PlayerTickEvent(ForgeEvents.TickEvent.Phase.START, player));
            }
        }

        @Inject(method = "tick", at = @At("RETURN"))
        private void feral$playerTickEnd(CallbackInfo ci) {
            PlayerEntity player = (PlayerEntity) (Object) this;
            if (player.world.isClient) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(
                        new ForgeEvents.TickEvent.PlayerTickEvent(ForgeEvents.TickEvent.Phase.END, player));
            }
        }

        @Redirect(
                method = "finishUsingItem",
                at =
                        @At(
                                value = "INVOKE",
                                target =
                                        "Lnet/minecraft/item/ItemStack;finishUsing(Lnet/minecraft/world/World;Lnet/minecraft/entity/living/player/PlayerEntity;)Lnet/minecraft/item/ItemStack;"))
        private ItemStack feral$finishItemUse(ItemStack stack, World world, PlayerEntity player) {
            ItemStack result = stack.finishUsing(world, player);
            if (!world.isClient) {
                return result;
            }

            ForgeEvents.PlayerUseItemEvent.Finish event =
                    new ForgeEvents.PlayerUseItemEvent.Finish(player, stack, this.itemUseTimer, result);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
            return event.result;
        }
    }

    @Mixin(value = net.minecraft.client.render.entity.PlayerRenderer.class, priority = 1100)
    public static class PlayerRenderer {
        @Inject(
                method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",
                at = @At("HEAD"),
                cancellable = true)
        private void feral$renderPlayerPre(
                ClientPlayerEntity player,
                double x,
                double y,
                double z,
                float yaw,
                float partialTicks,
                CallbackInfo ci) {
            if (ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.RenderPlayerEvent.Pre(
                    player,
                    (net.minecraft.client.render.entity.PlayerRenderer) (Object) this,
                    partialTicks,
                    x,
                    y,
                    z))) {
                ci.cancel();
            }
        }

        @Inject(
                method = "render(Lnet/minecraft/client/entity/living/player/ClientPlayerEntity;DDDFF)V",
                at = @At("RETURN"))
        private void feral$renderPlayerPost(
                ClientPlayerEntity player,
                double x,
                double y,
                double z,
                float yaw,
                float partialTicks,
                CallbackInfo ci) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.RenderPlayerEvent.Post(
                    player, (net.minecraft.client.render.entity.PlayerRenderer) (Object) this, partialTicks, x, y, z));
        }
    }
}
