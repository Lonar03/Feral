package keystrokesmod.mixin.impl.client;

import keystrokesmod.Feral;
import keystrokesmod.event.ClickMouseEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.GuiUpdateEvent;
import keystrokesmod.event.KeyEvent;
import keystrokesmod.event.PostMouseSelectionEvent;
import keystrokesmod.event.PostRightClickMouseEvent;
import keystrokesmod.event.PreAttackEvent;
import keystrokesmod.event.PreInputEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.RightClickDelayTickEvent;
import keystrokesmod.event.RightClickMouseEvent;
import keystrokesmod.event.RunGameLoopEvent;
import keystrokesmod.event.SlotUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.player.BedAura;
import keystrokesmod.module.impl.player.FastMine;
import keystrokesmod.module.impl.render.Freelook;
import keystrokesmod.platform.ornithe.ForgeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.GameOptions;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.world.HitResult;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void feral$saveProfilesOnShutdown(CallbackInfo ci) {
        if (Feral.profileManager != null) {
            Feral.profileManager.saveOnShutdown();
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/render/GameRenderer;pick(F)V",
                            shift = Shift.BEFORE))
    public void onBeforeGetMouseOver(CallbackInfo ci) {
        RotationHelper.get().updateServerRotations();
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/render/GameRenderer;pick(F)V",
                            shift = Shift.AFTER))
    public void onRunTickMouseOver(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostMouseSelectionEvent());
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            opcode = 180,
                            target =
                                    "Lnet/minecraft/client/options/GameOptions;chatVisibility:Lnet/minecraft/entity/living/player/PlayerEntity$ChatVisibility;"))
    private void injectBeforeChatVisibility(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PrePlayerInteractEvent());
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;handleGuiKeyBindings()V"))
    private void injectBeforeDispatchKeypresses(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new KeyEvent(
                Keyboard.getKeyName(Keyboard.getEventKey()),
                Keyboard.getEventKey(),
                Keyboard.getEventKeyState(),
                Minecraft.getInstance().screen != null));
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/profiler/Profiler;swap(Ljava/lang/String;)V",
                            ordinal = 2))
    private void onRunTick(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PreInputEvent());
    }

    @Inject(method = "runGame", at = @At("HEAD"))
    public void onRunGameLoop(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new RunGameLoopEvent());
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    public void injectClickMouse(CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        HitResult mop = mc.crosshairTarget;
        PreAttackEvent preAttack = new PreAttackEvent(mop);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(preAttack);
        if (preAttack.isCanceled()) {
            ci.cancel();
        } else {
            ForgeEvents.MinecraftForge.EVENT_BUS.post(new ClickMouseEvent());
        }
    }

    @Redirect(
            method = "doAttack",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/entity/living/player/LocalClientPlayerEntity;swingArm()V"))
    private void feral$swingAttack(LocalClientPlayerEntity player) {
        if (ModuleManager.killAura != null && ModuleManager.killAura.shouldSwingSilentlyWhileBlocking()) {
            player.networkHandler.sendPacket(new ArmSwingC2SPacket());
        } else {
            player.swingArm();
        }
    }

    @Inject(method = "doUse", at = @At("HEAD"), cancellable = true)
    public void injectRightClickMouse(CallbackInfo ci) {
        RightClickMouseEvent event = new RightClickMouseEvent();
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    public void onRunTickStart(CallbackInfo ci) {
        if (ModuleManager.velocity != null) {
            ModuleManager.velocity.beginClientTick();
        }

        ForgeEvents.MinecraftForge.EVENT_BUS.post(new GameTickEvent());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onRunTickEnd(CallbackInfo ci) {
        if (ModuleManager.velocity != null) {
            ModuleManager.velocity.finishClientTick();
        }
    }

    @Inject(method = "handleMouseDown", at = @At("HEAD"), cancellable = true)
    private void feral$gateBlockAttack(boolean attack, CallbackInfo ci) {
        if (ModuleManager.bedAura != null && ModuleManager.bedAura.handleAutomaticMining()) {
            ci.cancel();
        }
    }

    @Inject(method = "doUse", at = @At("RETURN"))
    public void postRightClickMouse(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new PostRightClickMouseEvent());
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/util/profiler/Profiler;push(Ljava/lang/String;)V",
                            ordinal = 0,
                            shift = Shift.BEFORE))
    public void onRunTickAfterRightClickDelay(CallbackInfo ci) {
        ForgeEvents.MinecraftForge.EVENT_BUS.post(new RightClickDelayTickEvent());
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/Minecraft;handleMouseDown(Z)V",
                            shift = Shift.AFTER))
    private void feral$fastMinePassiveBlockHitDelay(CallbackInfo ci) {
        BedAura bedAura = ModuleManager.bedAura;
        if (bedAura == null || !bedAura.shouldOverrideFastMine()) {
            FastMine fm = ModuleManager.fastMine;
            if (fm != null) {
                fm.tickPassiveBlockHitDecay((Minecraft) (Object) this);
            }
        }
    }

    @Inject(method = "openScreen(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    public void onDisplayGuiScreen(Screen guiScreen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        Screen previousGui = mc.screen;
        Screen setGui = guiScreen;
        boolean opened = setGui != null;
        if (!opened) {
            setGui = previousGui;
        }

        GuiUpdateEvent event = new GuiUpdateEvent(setGui, opened);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/entity/living/player/PlayerInventory;scrollInHotbar(I)V"))
    public void changeCurrentItem(PlayerInventory inventoryPlayer, int slot) {
        PreSlotScrollEvent event = new PreSlotScrollEvent(slot, inventoryPlayer.selectedSlot);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(event);
        if (!event.isCanceled()) {
            inventoryPlayer.scrollInHotbar(slot);
        }
    }

    @Redirect(
            method = "tick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/options/GameOptions;perspective:I", opcode = 181))
    private void onSetThirdPersonView(GameOptions gameSettings, int value) {
        if (ModuleManager.freelook != null && Freelook.perspectiveToggled) {
            ModuleManager.freelook.resetPerspective();
        } else {
            gameSettings.perspective = value;
        }
    }

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lnet/minecraft/entity/living/player/PlayerInventory;selectedSlot:I",
                            opcode = 181))
    private void onSetCurrentItem(PlayerInventory inventoryPlayer, int slot) {
        SlotUpdateEvent e = new SlotUpdateEvent(slot);
        ForgeEvents.MinecraftForge.EVENT_BUS.post(e);
        if (!e.isCanceled()) {
            inventoryPlayer.selectedSlot = slot;
        }
    }
}
