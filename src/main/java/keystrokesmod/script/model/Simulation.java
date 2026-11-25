package keystrokesmod.script.model;

import keystrokesmod.utility.SimulatedPlayer;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.Input;
import net.minecraft.util.math.BlockPos;

public class Simulation {
    private final SimulatedPlayer raw;

    private Simulation(SimulatedPlayer raw) {
        this.raw = raw;
    }

    public static Simulation create() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.player.input != null) {
            Input input = mc.player.input;
            SimulatedPlayer sim = SimulatedPlayer.fromClientPlayer(input);
            return new Simulation(sim);
        } else {
            throw new IllegalStateException("Simulation requires an initialized client player");
        }
    }

    public void setForward(float forward) {
        this.raw.movementInput.movementForward = forward;
    }

    public void setStrafe(float strafe) {
        this.raw.movementInput.movementSideways = strafe;
    }

    public void setJump(boolean jump) {
        this.raw.movementInput.jumping = jump;
    }

    public void setSneak(boolean sneak) {
        this.raw.movementInput.sneaking = sneak;
    }

    public void setYaw(float yaw) {
        this.raw.rotationYaw = yaw;
    }

    public void setPitch(float pitch) {
        this.raw.rotationPitch = pitch;
    }

    public void setSprinting(boolean sprinting) {
        this.raw.setSprintRequested(sprinting);
    }

    public float getAirAcceleration() {
        return this.raw.getAirAcceleration();
    }

    public void resetState(double x, double y, double z, boolean onGround) {
        this.raw.resetSimulationState(x, y, z, onGround);
    }

    public void tick() {
        this.raw.tick();
    }

    public Vec3 getPosition() {
        return new Vec3(this.raw.getPos());
    }

    public Vec3 getMotion() {
        return new Vec3(this.raw.motionX, this.raw.motionY, this.raw.motionZ);
    }

    public void setBlockState(BlockPos pos, BlockState state) {
        this.raw.setBlockState(pos, state);
    }

    public boolean isInLiquid() {
        return this.raw.isInWater() || this.raw.isInLava();
    }

    public boolean isOnLadder() {
        return this.raw.isOnLadder();
    }

    public boolean onGround() {
        return this.raw.onGround;
    }
}
