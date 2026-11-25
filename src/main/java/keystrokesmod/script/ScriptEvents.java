package keystrokesmod.script;

import keystrokesmod.Feral;
import keystrokesmod.event.AntiCheatFlagEvent;
import keystrokesmod.event.AttackEvent;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.GuiUpdateEvent;
import keystrokesmod.event.KeyEvent;
import keystrokesmod.event.KeyPressEvent;
import keystrokesmod.event.PlayerMoveEvent;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PostUpdateEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PreMotionEvent;
import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityRenderer;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.script.model.Entity;
import keystrokesmod.script.model.MovementInput;
import keystrokesmod.script.model.PlayerState;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.script.packet.clientbound.SPacket;
import keystrokesmod.script.packet.serverbound.CPacket;
import keystrokesmod.script.packet.serverbound.PacketHandler;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import org.lwjgl.opengl.GL11;

public class ScriptEvents {
    public Module module;

    public ScriptEvents(Module module) {
        this.module = module;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onChat(ForgeEvents.ClientChatReceivedEvent e) {
        if (Utils.nullCheck()) {
            if (!Utils.stripColor(e.message.getString()).isEmpty()) {
                if (Feral.scriptManager.invokeBoolean("onChat", this.module, e.message.getString(), e.type) == 0) {
                    e.setCanceled(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onSendPacket(SendPacketEvent e) {
        if (!e.isCanceled()) {
            CPacket packet = PacketHandler.convertServerBound(e.getPacket());
            if (packet != null && Feral.scriptManager.invokeBoolean("onPacketSent", this.module, packet) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onDispatchPacket(DispatchPacketEvent e) {
        CPacket packet = PacketHandler.convertServerBound(e.getPacket());
        if (packet != null) {
            Feral.scriptManager.invoke("onDispatchPacket", this.module, packet);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!e.isCanceled() && e.getPacket() != null) {
            SPacket packet = PacketHandler.convertClientBound(e.getPacket());
            if (packet != null && Feral.scriptManager.invokeBoolean("onPacketReceived", this.module, packet) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreAttack(PreAttackEvent e) {
        if (!e.isCanceled()) {
            if (Feral.scriptManager.invokeBoolean("onPreAttack", this.module) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onAttack(AttackEvent e) {
        if (!e.isCanceled()) {
            Entity target = Entity.convert(e.target);
            Entity attacker = Entity.convert(e.attacker);
            if (Feral.scriptManager.invokeBoolean("onAttackEntity", this.module, target, attacker) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onClientRotations(ClientRotationEvent e) {
        Float[] rotations = Feral.scriptManager.invokeFloatArray("getRotations", this.module);
        if (rotations != null && rotations.length != 0 && rotations.length <= 2) {
            if (rotations[0] != null) {
                e.yaw = rotations[0];
                e.scriptRotations = true;
            }

            if (rotations.length == 2 && rotations[1] != null) {
                e.pitch = rotations[1];
                e.scriptRotations = true;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPrePlayerMovementInput(PrePlayerInputEvent e) {
        MovementInput input = new MovementInput(e, (byte) 0);
        Feral.scriptManager.invoke("onPrePlayerInput", this.module, input);
        if (!e.isEquals(input)) {
            e.setForward(input.forward);
            e.setSneak(input.sneak);
            e.setJump(input.jump);
            e.setStrafe(input.strafe);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onKeyTyped(KeyPressEvent e) {
        if (!e.isCanceled()) {
            if (Feral.scriptManager.invokeBoolean("onKeyPress", this.module, e.typedChar, e.keyCode) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onKeyTyped(KeyEvent e) {
        if (!e.isCanceled()) {
            Feral.scriptManager.invoke("onKey", this.module, e.keyName, e.keyCode, e.state, e.inGui);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRenderWorldLast(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            Minecraft mc = Minecraft.getInstance();
            ((IAccessorEntityRenderer) mc.gameRenderer).callSetupCameraTransform(e.partialTicks, 0);
            ScriptDefaults.render.beginWorldToScreen(new Window(mc).getScale());

            try {
                Feral.scriptManager.invoke("onRenderWorld", this.module, e.partialTicks);
            } finally {
                ScriptDefaults.render.endWorldToScreen();
                this.restoreWorldRenderState(mc, e.partialTicks);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreUpdate(PreUpdateEvent e) {
        Feral.scriptManager.invoke("onPreUpdate", this.module);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPostUpdate(PostUpdateEvent e) {
        Feral.scriptManager.invoke("onPostUpdate", this.module);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onRenderTick(ForgeEvents.TickEvent.RenderTickEvent e) {
        if (e.phase == ForgeEvents.TickEvent.Phase.END && Utils.nullCheck()) {
            Minecraft mc = Minecraft.getInstance();
            mc.gameRenderer.setupGuiState();

            try {
                Feral.scriptManager.invoke("onRenderTick", this.module, e.renderTickTime);
            } finally {
                this.restoreOverlayRenderState(mc);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onAntiCheatFlag(AntiCheatFlagEvent e) {
        Feral.scriptManager.invoke("onAntiCheatFlag", this.module, e.flag, Entity.convert(e.entity));
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onGuiUpdate(GuiUpdateEvent e) {
        if (e.guiScreen != null) {
            Feral.scriptManager.invoke("onGuiUpdate", this.module, ScriptDefaults.getScreenName(e.guiScreen), e.opened);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onDisconnect(ForgeEvents.FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        Minecraft.getInstance().executeTask(() -> Feral.scriptManager.invoke("onDisconnect", this.module));
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreMotion(PreMotionEvent e) {
        PlayerState playerState = new PlayerState(e, (byte) 0);
        Feral.scriptManager.invoke("onPreMotion", this.module, playerState);
        if (!e.isEquals(playerState)) {
            if (e.getYaw() != playerState.yaw) {
                e.setYaw(playerState.yaw);
            }

            e.setPitch(playerState.pitch);
            e.setPosX(playerState.x);
            e.setPosY(playerState.y);
            e.setPosZ(playerState.z);
            e.setOnGround(playerState.onGround);
            e.setSprinting(playerState.isSprinting);
            e.setSneaking(playerState.isSneaking);
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        Feral.scriptManager.invoke("onPrePlayerInteract", this.module);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onWorldJoin(ForgeEvents.EntityJoinWorldEvent e) {
        if (e.entity != null) {
            Feral.scriptManager.invoke("onWorldJoin", this.module, Entity.convert(e.entity));
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPostInput(PostPlayerInputEvent e) {
        Feral.scriptManager.invoke("onPostPlayerInput", this.module);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPostMotion(PostMotionEvent e) {
        Feral.scriptManager.invoke("onPostMotion", this.module);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (e.button != -1 || e.dwheel != 0) {
            boolean state = e.buttonstate;
            if (e.button == -1) {
                if (e.dwheel > 0) {
                    state = true;
                } else {
                    state = false;
                }
            }

            if (Feral.scriptManager.invokeBoolean("onMouse", this.module, e.button, state, e.x, e.y) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPreRenderModel(ForgeEvents.RenderLivingEvent.Pre e) {
        if (Utils.nullCheck()) {
            if (Feral.scriptManager.invokeBoolean("onPreRenderModel", this.module, Entity.convert(e.entity)) == 0) {
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPostRenderModel(ForgeEvents.RenderLivingEvent.Post e) {
        if (Utils.nullCheck()) {
            Feral.scriptManager.invoke("onPostRenderModel", this.module, Entity.convert(e.entity));
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onScreenRender(ForgeEvents.GuiScreenEvent.DrawScreenEvent.Post event) {
        Feral.scriptManager.invoke(
                "onScreenRender",
                this.module,
                ScriptDefaults.getScreenName(event.gui),
                event.mouseX,
                event.mouseY,
                event.renderPartialTicks);
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        Feral.scriptManager.invoke("onPlayerMove", this.module, new Vec3(e.x, e.y, e.z));
    }

    private void restoreWorldRenderState(Minecraft mc, float partialTicks) {
        ((IAccessorEntityRenderer) mc.gameRenderer)
                .callSetupCameraTransform(((IAccessorMinecraft) mc).getTimer().partialTick, 0);
        this.restoreCommonRenderState();
        GlStateManager.shadeModel(7424);
        GlStateManager.enableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.disableFog();
        Lighting.turnOff();
    }

    private void restoreOverlayRenderState(Minecraft mc) {
        mc.gameRenderer.setupGuiState();
        this.restoreCommonRenderState();
        GlStateManager.disableDepthTest();
        GlStateManager.depthMask(true);
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        Lighting.turnOff();
    }

    private void restoreCommonRenderState() {
        GlStateManager.clearColor();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableTexture();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GL11.glLineWidth(1.0F);
        GL11.glDisable(2848);
        GL11.glDisable(3089);
    }
}
