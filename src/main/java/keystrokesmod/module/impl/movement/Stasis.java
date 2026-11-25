package keystrokesmod.module.impl.movement;

import keystrokesmod.event.PostPlayerInputEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.DescriptionSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.Utils;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.Angles;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;

public class Stasis extends Module {
    private final SliderSetting pulse;
    private boolean frozen;
    private double savedX;
    private double savedY;
    private double savedZ;
    private int frozenAt;
    private int delay;
    /** Set by Scaffold (Adjust) auto stasis: turn off again on landing, ladder, teleport or world change. */
    private Object autoOffPlayer;

    private boolean autoOffTeleport;

    public Stasis() {
        super("Stasis", Module.category.movement);
        this.registerSetting(new DescriptionSetting("Hangs the player midair."));
        this.registerSetting(this.pulse = new SliderSetting("Pulse", " ticks", "§cDisabled", 65.0, 2.0, 85.0, 1.0));
    }

    @Override
    public void onEnable() {
        this.delay = 2;
    }

    /** Enables Stasis until you land/teleport/change world (like Adjust's Stasis).. */
    public void enableAuto() {
        if (!this.isEnabled()) {
            this.enable();
            this.autoOffPlayer = mc.player;
        }
    }

    public boolean isAuto() {
        return this.autoOffPlayer != null;
    }

    @Override
    public void onDisable() {
        this.autoOffPlayer = null;
        this.autoOffTeleport = false;
        this.freeze(false);
        this.delay = 0;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPreUpdate(PreUpdateEvent e) {
        if (this.autoOffPlayer != null
                && (this.autoOffTeleport
                        || !Utils.nullCheck()
                        || mc.player != this.autoOffPlayer
                        || mc.player.onGround
                        || mc.player.isClimbing())) {
            this.disable();
            return;
        }
        if (Utils.nullCheck()) {
            if (this.delay > 0 && --this.delay == 0) {
                this.freeze(true);
            }

            if (this.frozen) {
                mc.player.velocityX = 0.0;
                mc.player.velocityY = 0.0;
                mc.player.velocityZ = 0.0;
                if (this.pulse.getInput() != this.pulse.getMin()
                        && mc.player.ticks - this.frozenAt >= (int) this.pulse.getInput()) {
                    this.freeze(false);
                    this.delay = 1;
                }
            }
        }
    }

    @ForgeEvents.SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (Utils.nullCheck() && this.frozen && e.getPacket() instanceof PlayerMoveC2SPacket) {
            if (Utils.isHypixel() && !(e.getPacket() instanceof Angles)) {
                mc.player.networkHandler.sendPacket(new Angles(mc.player.yaw, mc.player.pitch, mc.player.onGround));
                e.setCanceled(true);
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOW)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (this.autoOffPlayer != null && e.getPacket() instanceof PlayerMoveS2CPacket) {
            // Packet thread: turn off on the next tick.
            this.autoOffTeleport = true;
        }
        if (Utils.nullCheck() && this.frozen) {
            if (e.getPacket() instanceof PlayerMoveS2CPacket) {
                this.freeze(false);
                this.delay = 3;
            } else if (e.getPacket() instanceof EntityVelocityS2CPacket
                    && ((EntityVelocityS2CPacket) e.getPacket()).getId() == mc.player.getNetworkId()) {
                this.freeze(false);
                this.delay = 4;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onPostPlayerInput(PostPlayerInputEvent e) {
        if (Utils.nullCheck() && (this.frozen || this.delay == 1)) {
            mc.player.input.movementForward = 0.0F;
            mc.player.input.movementSideways = 0.0F;
        }
    }

    private void freeze(boolean freeze) {
        if (!Utils.nullCheck()) {
            this.frozen = false;
        } else {
            if (freeze) {
                this.savedX = mc.player.velocityX;
                this.savedY = mc.player.velocityY;
                this.savedZ = mc.player.velocityZ;
                this.frozenAt = mc.player.ticks;
            } else {
                mc.player.velocityX = this.savedX;
                mc.player.velocityY = this.savedY;
                mc.player.velocityZ = this.savedZ;
            }

            this.frozen = freeze;
        }
    }
}
