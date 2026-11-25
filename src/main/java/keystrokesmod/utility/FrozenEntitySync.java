package keystrokesmod.utility;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.AddEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddExperienceOrbS2CPacket;
import net.minecraft.network.packet.s2c.play.AddGlobalEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.AddMobS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPaintingS2CPacket;
import net.minecraft.network.packet.s2c.play.AddPlayerS2CPacket;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDataS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEventS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityHeadAnglesS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPickupS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityRemoveStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.RemoveEntitiesS2CPacket;

public class FrozenEntitySync {
    private static final FrozenEntitySync INSTANCE = new FrozenEntitySync();
    private final Queue<Packet<?>> liveEntityQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Packet<?>> deferredQueue = new ConcurrentLinkedQueue<>();
    private boolean active;
    private long lastFrozenTickNano;
    private double frozenTickAccumulator;
    private static final long TICK_NANOS = 50000000L;
    private static final int MAX_STEPS_PER_FRAME = 10;

    public static FrozenEntitySync get() {
        return INSTANCE;
    }

    public boolean isActive() {
        return this.active;
    }

    public void start() {
        if (!this.active) {
            this.active = true;
            this.lastFrozenTickNano = System.nanoTime();
            this.frozenTickAccumulator = 0.0;
        }
    }

    public void stop() {
        if (this.active) {
            this.active = false;
            this.flush();
        }
    }

    public void clearAll() {
        this.active = false;
        this.liveEntityQueue.clear();
        this.deferredQueue.clear();
        this.frozenTickAccumulator = 0.0;
    }

    public boolean intercept(Packet<?> packet) {
        if (!this.active) {
            return false;
        }

        if (isEntityPacket(packet)) {
            this.liveEntityQueue.add(packet);
        } else {
            this.deferredQueue.add(packet);
        }

        return true;
    }

    public void pumpFrame() {
        if (this.active) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.world != null && mc.player != null) {
                this.drainLiveQueue();
                long now = System.nanoTime();
                long elapsed = now - this.lastFrozenTickNano;
                this.lastFrozenTickNano = now;
                this.frozenTickAccumulator += elapsed;
                int steps = 0;

                while (this.frozenTickAccumulator >= 5.0E7 && steps < 10) {
                    this.frozenTickAccumulator -= 5.0E7;
                    steps++;
                    this.tickNonLocalEntities(mc);
                }

                if (steps >= 10) {
                    this.frozenTickAccumulator = 0.0;
                }

                float frozenPartial = (float) (this.frozenTickAccumulator / 5.0E7);
                TickTimer timer = ((IAccessorMinecraft) mc).getTimer();
                timer.partialTick = frozenPartial;
                this.snapLocalPlayerInterpolation(mc.player);
            }
        }
    }

    private void snapLocalPlayerInterpolation(LocalClientPlayerEntity player) {
        player.prevX = player.x;
        player.prevY = player.y;
        player.prevZ = player.z;
        player.lastYaw = player.yaw;
        player.lastPitch = player.pitch;
        player.lastHeadYaw = player.headYaw;
        player.lastBodyYaw = player.bodyYaw;
        player.lastWalkAnimationSpeed = player.walkAnimationSpeed;
        player.lastAttackAnimationProgress = player.attackAnimationProgress;
        player.lastTilt = player.tilt;
    }

    public void flush() {
        this.drainLiveQueue();

        Packet<?> p;
        while ((p = this.deferredQueue.poll()) != null) {
            PacketUtils.receivePacketNoEvent(p);
        }

        this.frozenTickAccumulator = 0.0;
    }

    private void drainLiveQueue() {
        Packet<?> p;
        while ((p = this.liveEntityQueue.poll()) != null) {
            PacketUtils.receivePacketNoEvent(p);
        }
    }

    private void tickNonLocalEntities(Minecraft mc) {
        LocalClientPlayerEntity local = mc.player;

        for (int i = 0; i < mc.world.globalEntities.size(); i++) {
            Entity entity = (Entity) mc.world.globalEntities.get(i);

            try {
                entity.ticks++;
                entity.tick();
            } catch (Throwable var7) {
            }

            if (entity.removed) {
                mc.world.globalEntities.remove(i--);
            }
        }

        for (Entity entity : mc.world.entities) {
            if (entity != null && !entity.removed && entity != local) {
                if (entity.vehicle != null) {
                    if (!entity.vehicle.removed && entity.vehicle.rider == entity) {
                        continue;
                    }

                    entity.vehicle.rider = null;
                    entity.vehicle = null;
                }

                try {
                    mc.world.tickEntity(entity, true);
                } catch (Throwable var6) {
                }
            }
        }
    }

    private static boolean isEntityPacket(Packet<?> packet) {
        return packet instanceof AddPlayerS2CPacket
                || packet instanceof AddMobS2CPacket
                || packet instanceof AddEntityS2CPacket
                || packet instanceof AddPaintingS2CPacket
                || packet instanceof AddExperienceOrbS2CPacket
                || packet instanceof AddGlobalEntityS2CPacket
                || packet instanceof EntityVelocityS2CPacket
                || packet instanceof RemoveEntitiesS2CPacket
                || packet instanceof EntityMoveS2CPacket
                || packet instanceof EntityTeleportS2CPacket
                || packet instanceof EntityHeadAnglesS2CPacket
                || packet instanceof EntityAnimationS2CPacket
                || packet instanceof PlayerSleepS2CPacket
                || packet instanceof AttachEntityS2CPacket
                || packet instanceof EntityDataS2CPacket
                || packet instanceof EntityEquipmentS2CPacket
                || packet instanceof EntityStatusEffectS2CPacket
                || packet instanceof EntityRemoveStatusEffectS2CPacket
                || packet instanceof EntityEventS2CPacket
                || packet instanceof EntityPickupS2CPacket;
    }
}
