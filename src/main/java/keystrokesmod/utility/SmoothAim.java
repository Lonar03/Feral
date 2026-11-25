package keystrokesmod.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

/**
 * Human-like aim smoothing. Each axis follows a critically damped spring (ease-in at the start,
 * ease-out near the target, no overshoot), capped at a max angular speed. Steps are snapped to the
 * mouse sensitivity grid so they look like real mouse input.
 *
 * <p>Speed is a 0..1 fraction: 1 means instant (the old snapping behaviour).
 */
public final class SmoothAim {
    private float velYaw;
    private float velPitch;
    private long lastNanos;
    private double jitter = 1.0;
    private float restYaw;
    private float restPitch;
    /** Smoothed angular velocity of the target (deg/s), added on top of the spring as lead. */
    private float ffYaw;

    private float ffPitch;
    private float lastTgtYaw;
    private float lastTgtPitch;
    private boolean hasLastTgt;

    /** Target angle changes faster than this per frame are aim point jumps, not movement. */
    private static final float JUMP_DEG = 15.0F;
    /** Averaging time for the target velocity; smooths out the 20 Hz tick steps. */
    private static final float FF_TAU = 0.08F;

    private static final float FF_MAX = 720.0F;

    /** Forget velocity; call when aiming stops or the target is lost. */
    public void reset() {
        this.velYaw = 0.0F;
        this.velPitch = 0.0F;
        this.lastNanos = 0L;
        this.jitter = 1.0;
        this.restYaw = 0.0F;
        this.restPitch = 0.0F;
        this.newTarget();
    }

    /** Call when the aimed-at mob changes so its velocity isn't carried over. */
    public void newTarget() {
        this.ffYaw = 0.0F;
        this.ffPitch = 0.0F;
        this.hasLastTgt = false;
    }

    public boolean isIdle() {
        return this.lastNanos == 0L;
    }

    /** Re-roll the per-engagement speed variation (randomization 0..1). */
    public void randomize(double randomization) {
        double range = 0.5 * Math.max(0.0, Math.min(1.0, randomization));
        this.jitter = 1.0 - range / 2.0 + Math.random() * range;
    }

    /** Seconds since the previous step, clamped; 0 on the first step. */
    public float frameDelta() {
        long now = System.nanoTime();
        float dt = this.lastNanos == 0L ? 0.0F : (now - this.lastNanos) / 1.0E9F;
        this.lastNanos = now;
        return Math.min(dt, 0.1F);
    }

    /**
     * Returns the {yaw, pitch} delta to add to the current rotation this step.
     *
     * @param speed 0..1, 1 = instant
     * @param dt seconds elapsed (use {@link #frameDelta()} per frame or 0.05 per tick)
     */
    public float[] step(float curYaw, float curPitch, float tgtYaw, float tgtPitch, double speed, float dt) {
        float errYaw = MathHelper.wrapDegrees(tgtYaw - curYaw);
        float errPitch = MathHelper.clamp(tgtPitch, -90.0F, 90.0F) - curPitch;
        if (speed >= 0.999) {
            this.velYaw = 0.0F;
            this.velPitch = 0.0F;
            this.restYaw = 0.0F;
            this.restPitch = 0.0F;
            return this.snap(errYaw, errPitch);
        }

        if (dt <= 0.0F) {
            return new float[] {0.0F, 0.0F};
        }

        this.trackTarget(tgtYaw, tgtPitch, dt);

        double s = Math.max(0.0, speed);
        // smoothTime ~ time to settle: 0.45s at the slowest, ~0.04s near max.
        float smoothTime = (float) ((0.04 + 0.41 * (1.0 - s) * (1.0 - s)) * this.jitter);
        // Max angular speed in deg/s: 300 at the slowest, ~3000 near max.
        float maxSpeed = (float) ((300.0 + 2700.0 * s * s) / this.jitter);

        float[] y = smoothDamp(errYaw, this.velYaw, smoothTime, maxSpeed, dt);
        float[] p = smoothDamp(errPitch, this.velPitch, smoothTime, maxSpeed, dt);
        this.velYaw = y[1];
        this.velPitch = p[1];
        // Follow the target's own motion so a moving mob (or our walking) doesn't leave the aim
        // trailing by smoothTime * angular speed.
        return this.snap(y[0] + this.ffYaw * dt, p[0] + this.ffPitch * dt);
    }

    private void trackTarget(float tgtYaw, float tgtPitch, float dt) {
        if (this.hasLastTgt) {
            float dYaw = MathHelper.wrapDegrees(tgtYaw - this.lastTgtYaw);
            float dPitch = tgtPitch - this.lastTgtPitch;
            if (Math.abs(dYaw) < JUMP_DEG && Math.abs(dPitch) < JUMP_DEG) {
                float a = Math.min(1.0F, dt / FF_TAU);
                this.ffYaw = MathHelper.clamp(this.ffYaw + (dYaw / dt - this.ffYaw) * a, -FF_MAX, FF_MAX);
                this.ffPitch = MathHelper.clamp(this.ffPitch + (dPitch / dt - this.ffPitch) * a, -FF_MAX, FF_MAX);
            }
        }
        this.lastTgtYaw = tgtYaw;
        this.lastTgtPitch = tgtPitch;
        this.hasLastTgt = true;
    }

    /**
     * Unity-style SmoothDamp moving from 0 towards {@code target}. Returns {movedAmount, newVelocity}.
     */
    private static float[] smoothDamp(float target, float velocity, float smoothTime, float maxSpeed, float dt) {
        float omega = 2.0F / smoothTime;
        float x = omega * dt;
        float exp = 1.0F / (1.0F + x + 0.48F * x * x + 0.235F * x * x * x);
        float maxChange = maxSpeed * smoothTime;
        float change = MathHelper.clamp(-target, -maxChange, maxChange);
        float clampedTarget = -change;
        float temp = (velocity + omega * change) * dt;
        float newVel = (velocity - omega * temp) * exp;
        float out = clampedTarget + (change + temp) * exp;
        if ((target > 0.0F) == (out > target)) {
            out = target;
            newVel = 0.0F;
        }
        return new float[] {out, newVel};
    }

    /**
     * Quantize a rotation delta to what the mouse at the current sensitivity could produce. The
     * sub-step remainder carries over, so slow movement at high FPS still adds up.
     */
    private float[] snap(float dYaw, float dPitch) {
        float f = Minecraft.getInstance().options.mouseSensitivity * 0.6F + 0.2F;
        double gcd = f * f * f * 1.2;
        if (gcd <= 1.0E-6) {
            return new float[] {dYaw, dPitch};
        }
        double wantYaw = dYaw + this.restYaw;
        double wantPitch = dPitch + this.restPitch;
        float outYaw = (float) (Math.round(wantYaw / gcd) * gcd);
        float outPitch = (float) (Math.round(wantPitch / gcd) * gcd);
        this.restYaw = (float) (wantYaw - outYaw);
        this.restPitch = (float) (wantPitch - outPitch);
        return new float[] {outYaw, outPitch};
    }
}
