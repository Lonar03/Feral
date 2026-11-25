package keystrokesmod.helper;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.PlayerInfo;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Player movement prediction for hitscan aiming, built on AutoAim's (Jade's) packet-based motion
 * predictor. It predicts from the positions the server sent (not the smoothed client position, which
 * trails 1-3 ticks behind), with strafe/jump/collision handling. The owner forwards received
 * packets to {@link #onPacket} and calls {@link #tick} once per client tick; tracking mirrors
 * AutoAim's updatePlayerTracking.
 */
public final class PlayerLeadTracker {
    private static final double MAX_POSITION_DRIFT_SQUARED = 2.25;
    private final Minecraft mc = Minecraft.getInstance();
    private final LeadPrediction.EntityPositionTracker positions = new LeadPrediction.EntityPositionTracker();
    private final LeadPrediction.MotionPredictor predictor = new LeadPrediction.MotionPredictor();
    private final Map<Integer, PlayerEntity> tracked = new HashMap<>();
    private World lastWorld;
    private double smoothedPingMs;

    public void onPacket(Packet<?> packet) {
        this.positions.onPacketReceived(packet, this.mc.world);
    }

    public void reset() {
        this.positions.clear();
        this.predictor.resetAll();
        this.tracked.clear();
        this.smoothedPingMs = 0.0;
    }

    public void tick() {
        if (this.lastWorld != this.mc.world) {
            this.lastWorld = this.mc.world;
            this.reset();
        }
        if (this.mc.player == null || this.mc.world == null) {
            return;
        }
        this.positions.flushToPredictor(this.predictor);
        this.predictor.setCurrentTime(System.nanoTime() / 5.0E7);
        for (int id : this.positions.pollRemovedEntityIds()) {
            this.predictor.removeEntity(id);
            this.tracked.remove(id);
        }

        for (PlayerEntity p : this.mc.world.players) {
            if (p == this.mc.player) {
                continue;
            }
            int id = p.getNetworkId();
            PlayerEntity previous = this.tracked.put(id, p);
            if (p.removed || p.deathTicks > 0 || p.getHealth() <= 0.0F) {
                this.predictor.removeEntity(id);
                this.positions.removeEntity(id);
                continue;
            }
            double x = p.lastKnownX / 32.0;
            double y = p.lastKnownY / 32.0;
            double z = p.lastKnownZ / 32.0;
            if (previous != null && previous != p) {
                // Same id, new entity object (respawn / re-track): start over.
                this.predictor.removeEntity(id);
                this.positions.removeEntity(id);
                this.positions.forceSample(id, x, y, z);
            }
            StatusEffectInstance jump = p.getEffectInstance(StatusEffect.JUMP_BOOST);
            ItemStack inUse = p.getItemInUse();
            ItemStack held = p.getItemInHand();
            boolean slowed =
                    p.isSwordBlocking() || p.hasItemInUse() && inUse != null && inUse.getItem() instanceof BowItem;
            boolean bridging = held != null && held.getItem() instanceof BlockItem && p.pitch > 70.0F;
            this.predictor.updateMotionStateInternal(
                    id,
                    p.headYaw,
                    slowed,
                    p.isSprinting(),
                    p.onGround,
                    jump == null ? -1 : jump.getAmplifier(),
                    bridging);
            if (this.predictor.hasSamples(id)
                    && this.predictor.computeSquaredDistanceToLastSample(id, x, y, z) > MAX_POSITION_DRIFT_SQUARED) {
                this.predictor.removeEntity(id);
                this.positions.forceSample(id, x, y, z);
            } else if (!this.predictor.hasSamples(id)) {
                this.positions.trackInitialPosition(id, x, y, z);
            }
        }
        this.tracked.values().removeIf(p -> !this.mc.world.players.contains(p));

        PlayerInfo info = this.mc.getNetworkHandler() == null
                ? null
                : this.mc.getNetworkHandler().getOnlinePlayer(this.mc.player.getUuid());
        if (info != null && info.getPing() >= 0) {
            double ping = Math.min(1000.0, info.getPing());
            this.smoothedPingMs = this.smoothedPingMs == 0.0 ? ping : this.smoothedPingMs * 0.85 + ping * 0.15;
        }
    }

    /** Round-trip ping in ticks (smoothed tab-list ping). */
    public double getPingTicks() {
        return this.smoothedPingMs / 50.0;
    }

    /**
     * Offset from the player's current client position to where the server will have them
     * {@code ticks} from now, or {@code null} while there's no data yet.
     */
    public Vec3d lead(PlayerEntity p, double ticks) {
        LeadPrediction.TargetPrediction tp = this.predictor.predict(p.getNetworkId(), ticks, (x, y, z) -> {
            double half = p.width * 0.5;
            Box box = new Box(x - half, y, z - half, x + half, y + p.height, z + half);
            return this.mc.world != null && this.mc.world.getCollisions(p, box).isEmpty();
        });
        return tp == null ? null : new Vec3d(tp.getX() - p.x, tp.getY() - p.y, tp.getZ() - p.z);
    }
}
