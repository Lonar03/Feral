package keystrokesmod.mixin.impl.ornithe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.platform.ClientPlatform;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.game.DeathScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class OrnitheClientHooks {
    private OrnitheClientHooks() {}

    @Mixin(ClientPlayNetworkHandler.class)
    public static class Chat {
        @Inject(
                method = "handleChatMessage",
                at =
                        @At(
                                value = "INVOKE",
                                target = "Lnet/minecraft/network/packet/s2c/play/ChatMessageS2CPacket;getType()B",
                                ordinal = 0),
                cancellable = true)
        private void feral$receiveChat(ChatMessageS2CPacket packet, CallbackInfo ci) {
            ForgeEvents.ClientChatReceivedEvent event =
                    new ForgeEvents.ClientChatReceivedEvent(packet.getType(), packet.getMessage());
            if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(event) && event.message != null) {
                if (event.message != packet.getMessage()) {
                    ((OrnitheClientHooks.ChatPacket) packet).feral$setChatComponent(event.message);
                }
            } else {
                ci.cancel();
            }
        }
    }

    @Mixin(ChatMessageS2CPacket.class)
    public interface ChatPacket {
        @Accessor("message")
        void feral$setChatComponent(Text var1);
    }

    @Mixin(Minecraft.class)
    public abstract static class Client {
        @Shadow
        public net.minecraft.client.world.ClientWorld world;

        @Unique
        private boolean feral$replacingScreen;

        @Shadow
        public abstract void openScreen(net.minecraft.client.gui.screen.Screen var1);

        @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
        private void feral$onScreen(net.minecraft.client.gui.screen.Screen requested, CallbackInfo ci) {
            if (!this.feral$replacingScreen) {
                Minecraft client = (Minecraft) (Object) this;
                net.minecraft.client.gui.screen.Screen resolved = requested;
                if (resolved == null && this.world == null) {
                    resolved = new TitleScreen();
                } else if (resolved == null && client.player != null && client.player.getHealth() <= 0.0F) {
                    resolved = new DeathScreen();
                }

                ForgeEvents.GuiOpenEvent event = new ForgeEvents.GuiOpenEvent(resolved);
                if (ForgeEvents.MinecraftForge.EVENT_BUS.post(event)) {
                    ci.cancel();
                } else if (event.gui != resolved) {
                    ci.cancel();
                    this.feral$replacingScreen = true;

                    try {
                        this.openScreen(event.gui);
                    } finally {
                        this.feral$replacingScreen = false;
                    }
                }
            }
        }

        @Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
        private void feral$unloadWorld(net.minecraft.client.world.ClientWorld next, String message, CallbackInfo ci) {
            if (this.world != null) {
                ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.WorldEvent.Unload(this.world));
            }
        }

        @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z", remap = false))
        private boolean feral$nextMouseEvent() {
            while (Mouse.next()) {
                // Real mouse input only (Forge's MouseInputEvent): fake clicks post MouseEvent but never pass here
                keystrokesmod.helper.KeyInputTracker.onRawMouseInput();
                if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.MouseEvent())) {
                    return true;
                }
            }

            return false;
        }

        @Redirect(
                method = "runGame",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(FJ)V"))
        private void feral$renderFrame(GameRenderer renderer, float partialTicks, long frameTime) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.TickEvent.RenderTickEvent(ForgeEvents.TickEvent.Phase.START, partialTicks));
            renderer.render(partialTicks, frameTime);
            float endPartialTicks = ((IAccessorMinecraft) (Object) this).getTimer().partialTick;
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.TickEvent.RenderTickEvent(ForgeEvents.TickEvent.Phase.END, endPartialTicks));
        }
    }

    @Mixin(net.minecraft.client.world.ClientWorld.class)
    public static class ClientWorld {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void feral$loadWorld(CallbackInfo ci) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.WorldEvent.Load((net.minecraft.client.world.ClientWorld) (Object) this));
        }
    }

    @Mixin(GLX.class)
    public static class Lightmap {
        @Inject(method = "multiTexCoord2f", at = @At("HEAD"))
        private static void feral$rememberLightmap(int unit, float x, float y, CallbackInfo ci) {
            if (unit == GLX.GL_TEXTURE1) {
                ClientPlatform.lastBrightnessX = x;
                ClientPlatform.lastBrightnessY = y;
            }
        }
    }

    @Mixin(GameRenderer.class)
    public static class Renderer {
        @Shadow
        private Minecraft minecraft;

        @Inject(
                method = "render(IFJ)V",
                at =
                        @At(
                                value = "FIELD",
                                opcode = 180,
                                target = "Lnet/minecraft/client/render/GameRenderer;newCamPitch:Z"))
        private void feral$renderWorldLast(int pass, float partialTicks, long finishTime, CallbackInfo ci) {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.RenderWorldLastEvent(this.minecraft.worldRenderer, partialTicks));
        }

        @Redirect(
                method = "render(IFJ)V",
                at =
                        @At(
                                value = "INVOKE",
                                target =
                                        "Lnet/minecraft/client/render/world/WorldRenderer;renderBlockOutline(Lnet/minecraft/entity/living/player/PlayerEntity;Lnet/minecraft/world/HitResult;IF)V"))
        private void feral$selectionBox(
                WorldRenderer renderer, PlayerEntity player, HitResult target, int subId, float partialTicks) {
            ForgeEvents.DrawBlockHighlightEvent event = new ForgeEvents.DrawBlockHighlightEvent(
                    renderer, player, target, subId, player.getDisplayItemInHand(), partialTicks);
            if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(event)) {
                renderer.renderBlockOutline(player, target, subId, partialTicks);
            }
        }

        @Redirect(
                method = "render(FJ)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V"))
        private void feral$drawScreen(
                net.minecraft.client.gui.screen.Screen screen, int mouseX, int mouseY, float partialTicks) {
            screen.render(mouseX, mouseY, partialTicks);
            ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.GuiScreenEvent.DrawScreenEvent.Post(screen, mouseX, mouseY, partialTicks));
        }
    }

    @Mixin(net.minecraft.client.gui.screen.Screen.class)
    public static class Screen {
        @Redirect(
                method = "handleInputs",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;handleMouse()V"))
        private void feral$screenMouseInput(net.minecraft.client.gui.screen.Screen screen) throws IOException {
            if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(
                    new ForgeEvents.GuiScreenEvent.MouseInputEvent.Pre(screen))) {
                screen.handleMouse();
            }
        }
    }

    @Mixin(World.class)
    public static class Spawn {
        @Inject(
                method = "addEntity",
                at =
                        @At(
                                value = "INVOKE",
                                target =
                                        "Lnet/minecraft/world/World;getChunkAt(II)Lnet/minecraft/world/chunk/WorldChunk;"),
                cancellable = true)
        private void feral$onEntitySpawn(Entity entity, CallbackInfoReturnable<Boolean> ci) {
            World world = (World) (Object) this;
            if (world.isClient
                    && ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.EntityJoinWorldEvent(entity, world))
                    && !entity.teleporting
                    && !(entity instanceof PlayerEntity)) {
                ci.setReturnValue(false);
            }
        }

        @ModifyVariable(method = "loadEntities", at = @At("HEAD"), argsOnly = true)
        private Collection<Entity> feral$onChunkEntities(Collection<Entity> entities) {
            World world = (World) (Object) this;
            if (!world.isClient) {
                return entities;
            }

            List<Entity> accepted = new ArrayList<>(entities.size());

            for (Entity entity : entities) {
                if (!ForgeEvents.MinecraftForge.EVENT_BUS.post(new ForgeEvents.EntityJoinWorldEvent(entity, world))) {
                    accepted.add(entity);
                }
            }

            return accepted;
        }

        @Redirect(
                method = "addEntityAlways",
                at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", remap = false))
        private boolean feral$joinNearbyEntities(List<Entity> entities, Object value) {
            World world = (World) (Object) this;
            Entity entity = (Entity) value;
            return (!world.isClient
                            || !ForgeEvents.MinecraftForge.EVENT_BUS.post(
                                    new ForgeEvents.EntityJoinWorldEvent(entity, world)))
                    && entities.add(entity);
        }
    }
}
