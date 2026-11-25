package keystrokesmod.module.impl.player;

import java.awt.Color;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.entity.living.player.RemoteClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.world.HitResult.Type;
import org.lwjgl.input.Keyboard;

public class Freecam extends Module {
    public SliderSetting speed;
    private ButtonSetting disableOnDamage;
    private ButtonSetting showArm;
    private ButtonSetting allowDigging;
    private ButtonSetting allowInteracting;
    private ButtonSetting allowPlacing;
    public static RemoteClientPlayerEntity freeEntity = null;
    private int[] lcc = new int[] {Integer.MAX_VALUE, 0};
    private float[] sAng = new float[] {0.0F, 0.0F};

    public Freecam() {
        super("Freecam", Module.category.player);
        this.registerSetting(this.speed = new SliderSetting("Speed", 2.5, 0.5, 10.0, 0.5));
        this.registerSetting(this.disableOnDamage = new ButtonSetting("Disable on damage", true));
        this.registerSetting(this.allowDigging = new ButtonSetting("Allow digging", false));
        this.registerSetting(this.allowInteracting = new ButtonSetting("Allow interacting", false));
        this.registerSetting(this.allowPlacing = new ButtonSetting("Allow placing", false));
        this.registerSetting(this.showArm = new ButtonSetting("Show arm", false));
    }

    @Override
    public void onEnable() {
        if (!mc.player.onGround) {
            this.disable();
        } else {
            freeEntity = new RemoteClientPlayerEntity(mc.world, mc.player.getGameProfile());
            freeEntity.copyPositionAndRotationFrom(mc.player);
            this.sAng[0] = freeEntity.headYaw = mc.player.headYaw;
            this.sAng[1] = mc.player.pitch;
            freeEntity.lerpVelocity(0.0, 0.0, 0.0);
            freeEntity.setInvisible(true);
            mc.world.forceEntity(-8008, freeEntity);
            mc.player.velocityX = 0.0;
            mc.player.velocityZ = 0.0;
            mc.setCamera(freeEntity);
        }
    }

    @Override
    public void onDisable() {
        if (freeEntity != null) {
            mc.setCamera(mc.player);
            if (mc.player != null && mc.player.world == freeEntity.world) {
                mc.player.yaw = mc.player.headYaw = this.sAng[0];
                mc.player.pitch = this.sAng[1];
            }

            freeEntity.world.removeEntity(freeEntity);
            freeEntity = null;
        }

        this.lcc = new int[] {Integer.MAX_VALUE, 0};
        if (Utils.nullCheck()) {
            int x = mc.player.chunkX;
            int z = mc.player.chunkZ;

            for (int x2 = -1; x2 <= 1; x2++) {
                for (int z2 = -1; z2 <= 1; z2++) {
                    int a = x + x2;
                    int b = z + z2;
                    mc.world.notifyRegionChanged(a * 16, 0, b * 16, a * 16 + 15, 256, b * 16 + 15);
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (this.disableOnDamage.isToggled() && mc.player.damagedTimer != 0) {
            this.disable();
        } else {
            double s = 0.215 * this.speed.getInput();
            if (Keyboard.isKeyDown(mc.options.forwardKey.getKeyCode())) {
                double rad = freeEntity.headYaw * (Math.PI / 180.0);
                double dx = -1.0 * Math.sin(rad) * s;
                double dz = Math.cos(rad) * s;
                freeEntity.x += dx;
                freeEntity.z += dz;
            }

            if (Keyboard.isKeyDown(mc.options.backKey.getKeyCode())) {
                double rad = freeEntity.headYaw * (Math.PI / 180.0);
                double dx = -1.0 * Math.sin(rad) * s;
                double dz = Math.cos(rad) * s;
                freeEntity.x -= dx;
                freeEntity.z -= dz;
            }

            if (Keyboard.isKeyDown(mc.options.leftKey.getKeyCode())) {
                double rad = (freeEntity.headYaw - 90.0F) * (Math.PI / 180.0);
                double dx = -1.0 * Math.sin(rad) * s;
                double dz = Math.cos(rad) * s;
                freeEntity.x += dx;
                freeEntity.z += dz;
            }

            if (Keyboard.isKeyDown(mc.options.rightKey.getKeyCode())) {
                double rad = (freeEntity.headYaw + 90.0F) * (Math.PI / 180.0);
                double dx = -1.0 * Math.sin(rad) * s;
                double dz = Math.cos(rad) * s;
                freeEntity.x += dx;
                freeEntity.z += dz;
            }

            if (Utils.jumpDown()) {
                freeEntity.y += 0.93 * s;
            }

            if (Keyboard.isKeyDown(mc.options.sneakKey.getKeyCode())) {
                freeEntity.y -= 0.93 * s;
            }

            if (this.lcc[0] != Integer.MAX_VALUE
                    && (this.lcc[0] != freeEntity.chunkX || this.lcc[1] != freeEntity.chunkZ)) {
                int x = freeEntity.chunkX;
                int z = freeEntity.chunkZ;
                mc.world.notifyRegionChanged(x * 16, 0, z * 16, x * 16 + 15, 256, z * 16 + 15);
            }

            this.lcc[0] = freeEntity.chunkX;
            this.lcc[1] = freeEntity.chunkZ;
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onWorldUnload(ForgeEvents.WorldEvent.Unload e) {
        if (freeEntity != null && e.world == freeEntity.world) {
            this.disable();
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onRenderWorld(ForgeEvents.RenderWorldLastEvent e) {
        if (Utils.nullCheck()) {
            if (!this.showArm.isToggled()) {
                mc.player.easedPitch = mc.player.lastEasedPitch = 700.0F;
            }

            RenderUtils.renderEntity(mc.player, 1, 0.0, 0.0, Color.green.getRGB(), false);
            RenderUtils.renderEntity(mc.player, 2, 0.0, 0.0, Color.green.getRGB(), false);
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onMouse(ForgeEvents.MouseEvent e) {
        if (Utils.nullCheck()) {
            Type hitType = mc.crosshairTarget == null ? Type.MISS : mc.crosshairTarget.type;
            if (e.button == 0) {
                if (hitType == Type.BLOCK && !this.allowDigging.isToggled()
                        || hitType == Type.ENTITY && !this.allowInteracting.isToggled()) {
                    e.setCanceled(true);
                }
            } else if (e.button == 1) {
                if (hitType == Type.ENTITY) {
                    if (!this.allowInteracting.isToggled()) {
                        e.setCanceled(true);
                    }
                } else if (!this.allowPlacing.isToggled()) {
                    e.setCanceled(true);
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (Utils.nullCheck()) {
            if (e.getPacket() instanceof PlayerHandActionC2SPacket
                    && !this.allowDigging.isToggled()
                    && this.isBlockDigging((PlayerHandActionC2SPacket) e.getPacket())) {
                e.setCanceled(true);
            } else if (e.getPacket() instanceof PlayerUseC2SPacket && !this.allowPlacing.isToggled()) {
                e.setCanceled(true);
            } else if (e.getPacket() instanceof PlayerInteractEntityC2SPacket && !this.allowInteracting.isToggled()) {
                e.setCanceled(true);
            }
        }
    }

    private boolean isBlockDigging(PlayerHandActionC2SPacket packet) {
        Action action = packet.getAction();
        return action == Action.START_DESTROY_BLOCK
                || action == Action.ABORT_DESTROY_BLOCK
                || action == Action.STOP_DESTROY_BLOCK;
    }
}
