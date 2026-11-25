package keystrokesmod.helper;

import java.util.Map;
import java.util.WeakHashMap;
import keystrokesmod.Feral;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.DispatchPacketEvent;
import keystrokesmod.event.GameTickEvent;
import keystrokesmod.platform.ornithe.ForgeEvents;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.network.Connection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class AutoLadderHelper {
    private final Minecraft mc = Minecraft.getInstance();
    private LocalClientPlayerEntity player;
    private Connection network;
    private World world;
    private final Map<Packet<?>, Boolean> dispatched = new WeakHashMap<>();
    private volatile AutoLadderHelper.SentState sent = AutoLadderHelper.SentState.empty();
    private volatile PlayerUseC2SPacket lastPlacement;
    private boolean registered;
    private boolean closing;
    private boolean restorePending;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private boolean manualChange;
    private long holdAfterMovement = -1L;
    private float holdYaw;
    private float holdPitch;
    private boolean aimed;
    private long slotRequestSequence = -1L;

    public void observe() {
        if (!this.registered) {
            ForgeEvents.MinecraftForge.EVENT_BUS.register(this);
            this.registered = true;
        }

        this.updatePlayer();
    }

    public void start() {
        this.observe();
        this.closing = false;
    }

    public synchronized void updatePlayer() {
        if (this.player != this.mc.player
                || this.world != this.mc.world
                || this.player != null && this.network != this.player.networkHandler.getConnection()) {
            this.player = this.mc.player;
            this.world = this.mc.world;
            this.network = this.player == null ? null : this.player.networkHandler.getConnection();
            this.sent = AutoLadderHelper.SentState.empty();
            this.lastPlacement = null;
            this.originalSlot = this.selectedSlot = -1;
            this.restorePending = this.manualChange = this.aimed = false;
            this.holdAfterMovement = this.slotRequestSequence = -1L;
            this.dispatched.clear();
        }
    }

    public AutoLadderHelper.SentState sent() {
        return this.sent;
    }

    public PlayerUseC2SPacket lastPlacement() {
        return this.lastPlacement;
    }

    @ForgeEvents.SubscribeEvent
    public synchronized void onDispatch(DispatchPacketEvent event) {
        this.updatePlayer();
        if (this.network != null && event.getNetworkManager() == this.network) {
            Packet<?> packet = event.getPacket();
            if (this.dispatched.put(packet, Boolean.TRUE) == null) {
                AutoLadderHelper.SentState old = this.sent;
                if (packet instanceof PlayerUseC2SPacket) {
                    this.lastPlacement = (PlayerUseC2SPacket) packet;
                }

                if (packet instanceof PlayerMoveC2SPacket) {
                    PlayerMoveC2SPacket movement = (PlayerMoveC2SPacket) packet;
                    this.sent = new AutoLadderHelper.SentState(
                            old.sequence + 1L,
                            -1L,
                            movement.hasPos() ? movement.getX() : old.x,
                            movement.hasPos() ? movement.getMinY() : old.y,
                            movement.hasPos() ? movement.getZ() : old.z,
                            movement.hasAngles() ? movement.getYaw() : old.yaw,
                            movement.hasAngles() ? movement.getPitch() : old.pitch,
                            old.positionKnown || movement.hasPos(),
                            old.rotationKnown || movement.hasAngles(),
                            old.slot);
                } else if (packet instanceof SelectSlotC2SPacket) {
                    this.sent = new AutoLadderHelper.SentState(
                            old.sequence,
                            old.actionSequence,
                            old.x,
                            old.y,
                            old.z,
                            old.yaw,
                            old.pitch,
                            old.positionKnown,
                            old.rotationKnown,
                            ((SelectSlotC2SPacket) packet).getSlot());
                } else if (packet instanceof PlayerUseC2SPacket
                        || packet instanceof PlayerInteractEntityC2SPacket
                        || packet instanceof PlayerHandActionC2SPacket
                        || packet instanceof PlayerMovementActionC2SPacket
                        || packet instanceof ClientStatusC2SPacket) {
                    this.sent = new AutoLadderHelper.SentState(
                            old.sequence,
                            old.sequence,
                            old.x,
                            old.y,
                            old.z,
                            old.yaw,
                            old.pitch,
                            old.positionKnown,
                            old.rotationKnown,
                            old.slot);
                }
            }
        }
    }

    public boolean canSwitch() {
        AutoLadderHelper.SentState state = this.sent;
        return this.player != null
                && this.mc.interactionManager != null
                && state.sequence > 0L
                && state.actionSequence != state.sequence
                && (Feral.lagHandler == null || !Feral.lagHandler.isBuffering());
    }

    public boolean select(int slot) {
        if (slot < 0 || slot > 8 || this.player == null || this.manualChange || this.restorePending) {
            return false;
        }

        if (this.selectedSlot >= 0 && this.player.inventory.selectedSlot != this.selectedSlot) {
            this.abandonManualSelection();
            return false;
        }

        if (!this.canSwitch()) {
            return false;
        }

        if (this.originalSlot < 0) {
            this.originalSlot = this.player.inventory.selectedSlot;
        }

        if (!this.requestSlot(slot)) {
            return false;
        }

        this.selectedSlot = slot;
        return this.sent.slot == slot;
    }

    private boolean requestSlot(int slot) {
        if (this.sent.slot == slot && this.player.inventory.selectedSlot == slot) {
            return true;
        }

        if (this.slotRequestSequence == this.sent.sequence) {
            return false;
        }

        this.slotRequestSequence = this.sent.sequence;
        if (this.player.inventory.selectedSlot != slot) {
            Utils.switchSlot(slot, true);
        } else {
            this.player.networkHandler.sendPacket(new SelectSlotC2SPacket(slot));
        }

        return this.player.inventory.selectedSlot == slot;
    }

    public boolean changedManually() {
        if (this.player != null && this.selectedSlot >= 0 && this.player.inventory.selectedSlot != this.selectedSlot) {
            this.abandonManualSelection();
        }

        return this.manualChange;
    }

    private void abandonManualSelection() {
        this.originalSlot = this.selectedSlot = -1;
        this.restorePending = false;
        this.manualChange = true;
    }

    public void clearManualChange() {
        this.manualChange = false;
    }

    public boolean restoring() {
        return this.restorePending;
    }

    public void restore() {
        this.restorePending = this.originalSlot >= 0;
    }

    public int transferSlot() {
        this.updatePlayer();
        this.changedManually();
        int slot = this.originalSlot;
        this.originalSlot = this.selectedSlot = -1;
        this.restorePending = false;
        this.aimed = false;
        this.holdAfterMovement = -1L;
        return slot;
    }

    public void pinAim(float yaw, float pitch) {
        this.holdAfterMovement = this.sent.sequence;
        this.holdYaw = yaw;
        this.holdPitch = pitch;
        this.aimed = true;
    }

    public boolean holdingAim() {
        return this.holdAfterMovement >= 0L && this.sent.sequence <= this.holdAfterMovement;
    }

    public float heldYaw() {
        return this.holdYaw;
    }

    public float heldPitch() {
        return this.holdPitch;
    }

    public void rememberAim() {
        this.aimed = true;
    }

    public void finish() {
        this.restore();
        this.closing = true;
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.HIGHEST)
    public void onTick(GameTickEvent event) {
        this.updatePlayer();
        if (this.player != null
                && this.restorePending
                && !this.changedManually()
                && this.canSwitch()
                && this.requestSlot(this.originalSlot)) {
            this.selectedSlot = this.originalSlot;
            if (this.sent.slot == this.originalSlot) {
                this.originalSlot = this.selectedSlot = -1;
                this.restorePending = false;
            }
        }
    }

    @ForgeEvents.SubscribeEvent(priority = ForgeEvents.EventPriority.LOWEST)
    public void onRotation(ClientRotationEvent event) {
        if (this.closing && this.player != null && this.aimed) {
            if (event.yaw != null || event.pitch != null) {
                this.aimed = false;
            } else if (this.holdingAim()) {
                event.claimExactRotations(this.holdYaw, this.holdPitch, false);
            } else {
                AutoLadderHelper.SentState state = this.sent;
                float dy = MathHelper.wrapDegrees(this.player.yaw - state.yaw);
                float dp = this.player.pitch - state.pitch;
                if (Math.abs(dy) < 1.0F && Math.abs(dp) < 1.0F) {
                    this.aimed = false;
                } else {
                    float[] fixed = RotationUtils.fixRotation(
                            state.yaw + MathHelper.clamp(dy, -45.0F, 45.0F),
                            state.pitch + MathHelper.clamp(dp, -45.0F, 45.0F),
                            state.yaw,
                            state.pitch);
                    event.claimExactRotations(fixed[0], fixed[1], false);
                }
            }
        }
    }

    public static final class SentState {
        public final long sequence;
        public final long actionSequence;
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final boolean positionKnown;
        public final boolean rotationKnown;
        public final int slot;

        SentState(
                long sequence,
                long actionSequence,
                double x,
                double y,
                double z,
                float yaw,
                float pitch,
                boolean positionKnown,
                boolean rotationKnown,
                int slot) {
            this.sequence = sequence;
            this.actionSequence = actionSequence;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.positionKnown = positionKnown;
            this.rotationKnown = rotationKnown;
            this.slot = slot;
        }

        static AutoLadderHelper.SentState empty() {
            return new AutoLadderHelper.SentState(0L, -1L, 0.0, 0.0, 0.0, 0.0F, 0.0F, false, false, 0);
        }
    }
}
