package keystrokesmod.utility.displace;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public final class VoidDisplaceSearch {
    private static final long CAPTURE_BUDGET_NS = 350000L;
    private static final long CAPTURE_INTERVAL_NS = 8000000L;
    private static final long VALIDATION_BUDGET_NS = 1000000L;
    private static final long MAX_RESULT_AGE_NS = 150000000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Feral Void Displace");
        thread.setDaemon(true);
        thread.setPriority(4);
        return thread;
    });
    private World world;
    private PlayerEntity target;
    private VoidGeometry capture;
    private VoidGeometry snapshot;
    private VoidDisplaceSearch.Sample latest;
    private final AtomicReference<VoidDisplaceSearch.Completed> finished = new AtomicReference<>();
    private boolean inFlight;
    private VoidDisplaceSearch.Completed completed;
    private VoidDisplaceSearch.Validation validation;
    private long generation;
    private long nextCapture;

    public void prepare(
            World world,
            PlayerEntity target,
            PlayerEntity attacker,
            float referenceYaw,
            float yawOffset,
            double radius,
            boolean ignoreLowWall,
            boolean predict,
            boolean expectSprinting,
            int lead,
            Float previousYaw,
            boolean debug) {
        VoidDisplacePlanner.Input input =
                VoidDisplacePlanner.input(target, attacker, referenceYaw, yawOffset, radius, ignoreLowWall, predict);
        if (expectSprinting && !attacker.isSprinting()) {
            input.knockback++;
        }

        this.observe(world, target, input, predict ? lead : 0, previousYaw, debug);
    }

    public VoidDisplacePlanner.Result resolve(
            World world,
            PlayerEntity target,
            PlayerEntity attacker,
            float referenceYaw,
            float yawOffset,
            double radius,
            boolean ignoreLowWall,
            boolean predict,
            int lead,
            Float previousYaw,
            boolean debug) {
        VoidDisplacePlanner.Input input =
                VoidDisplacePlanner.input(target, attacker, referenceYaw, yawOffset, radius, ignoreLowWall, predict);
        int predictionTicks = predict ? lead : 0;
        this.observe(world, target, input, predictionTicks, previousYaw, debug);
        if (this.completed != null
                && this.completed.result != null
                && this.completed.result.best != null
                && this.completed.sample.canReuse(this.latest)) {
            long tick = world.getTime();
            if (this.validation != null
                    && this.validation.tick == tick
                    && this.validation.result == this.completed.result
                    && this.validation.lead == predictionTicks
                    && this.validation.input.matches(input)) {
                return this.validation.usable ? this.validation.result : null;
            }

            long deadline = System.nanoTime() + 1000000L;
            VoidGeometry liveTerrain = new VoidGeometry(world, input, false, null);
            VoidDisplacePlanner.Input launch = VoidDisplacePlanner.predictLaunch(liveTerrain, input, predictionTicks);
            boolean usable =
                    VoidDisplacePlanner.validate(liveTerrain, launch, this.completed.result.best.yaw, deadline);
            this.validation =
                    new VoidDisplaceSearch.Validation(tick, input, predictionTicks, this.completed.result, usable);
            return usable ? this.completed.result : null;
        } else {
            return null;
        }
    }

    private void observe(
            World world,
            PlayerEntity target,
            VoidDisplacePlanner.Input input,
            int lead,
            Float previousYaw,
            boolean debug) {
        if (this.world != world) {
            this.clear();
            this.world = world;
        }

        if (this.target != target) {
            this.forgetTarget();
            this.target = target;
        }

        if (this.capture == null || !this.capture.covers(input)) {
            this.capture = new VoidGeometry(world, input, true, this.capture);
            this.invalidateSnapshot();
            this.nextCapture = System.nanoTime();
        }

        this.latest = new VoidDisplaceSearch.Sample(input, lead, previousYaw, debug);
        this.poll();
        this.submit();
    }

    public void advance(World world) {
        if (this.world == world && this.latest != null) {
            this.poll();
            long now = System.nanoTime();
            if (this.snapshot == null && now - this.nextCapture >= 0L) {
                this.nextCapture = now + 8000000L;
                this.snapshot = this.capture.capture(now + 350000L);
            }

            this.submit();
        }
    }

    public void invalidate(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (this.world == world && this.capture != null && this.capture.invalidate(x1, y1, z1, x2, y2, z2)) {
            this.invalidateSnapshot();
        }
    }

    private void invalidateSnapshot() {
        this.snapshot = null;
        this.completed = null;
        this.validation = null;
        this.generation++;
    }

    public void forgetTarget() {
        if (this.target != null) {
            this.generation++;
        }

        this.target = null;
        this.latest = null;
        this.completed = null;
        this.validation = null;
    }

    public void clear() {
        if (this.world != null) {
            this.forgetTarget();
            this.capture = null;
            this.world = null;
            this.invalidateSnapshot();
        }
    }

    private void poll() {
        if (this.inFlight) {
            VoidDisplaceSearch.Completed result = this.finished.getAndSet(null);
            if (result != null) {
                this.inFlight = false;
                if (result.generation == this.generation) {
                    this.completed = result;
                }
            }
        }
    }

    private void submit() {
        if (!this.inFlight
                && this.snapshot != null
                && this.latest != null
                && (this.completed == null || !this.completed.sample.matches(this.latest))) {
            this.inFlight = true;
            VoidGeometry terrain = this.snapshot;
            VoidDisplaceSearch.Sample sample = this.latest;
            long submittedGeneration = this.generation;
            AtomicReference<VoidDisplaceSearch.Completed> mailbox = this.finished;
            WORKER.execute(() -> {
                VoidDisplacePlanner.Result result = null;

                try {
                    VoidDisplacePlanner.Input launch =
                            VoidDisplacePlanner.predictLaunch(terrain, sample.input, sample.lead);
                    result = VoidDisplacePlanner.plan(terrain, launch, sample.previousYaw, sample.debug);
                } catch (RuntimeException exception) {
                    exception.printStackTrace();
                } finally {
                    mailbox.set(new VoidDisplaceSearch.Completed(submittedGeneration, sample, result));
                }
            });
        }
    }

    private static final class Completed {
        private final long generation;
        private final VoidDisplaceSearch.Sample sample;
        private final VoidDisplacePlanner.Result result;

        private Completed(long generation, VoidDisplaceSearch.Sample sample, VoidDisplacePlanner.Result result) {
            this.generation = generation;
            this.sample = sample;
            this.result = result;
        }
    }

    private static final class Sample {
        private final VoidDisplacePlanner.Input input;
        private final int lead;
        private final Float previousYaw;
        private final boolean debug;
        private final long time = System.nanoTime();

        private Sample(VoidDisplacePlanner.Input input, int lead, Float previousYaw, boolean debug) {
            this.input = input;
            this.lead = lead;
            this.previousYaw = previousYaw;
            this.debug = debug;
        }

        private boolean matches(VoidDisplaceSearch.Sample other) {
            return this.lead == other.lead
                    && this.debug == other.debug
                    && Objects.equals(this.previousYaw, other.previousYaw)
                    && this.input.matches(other.input);
        }

        private boolean canReuse(VoidDisplaceSearch.Sample other) {
            if (this.debug != other.debug
                    || this.lead == 0 != (other.lead == 0)
                    || this.input.radius != other.input.radius
                    || this.input.ignoreLowWall != other.input.ignoreLowWall
                    || this.input.yawOffset != other.input.yawOffset
                    || this.input.knockback != other.input.knockback) {
                return false;
            } else {
                return this.input.matches(other.input)
                        ? true
                        : other.time - this.time <= 150000000L
                                && Math.abs(this.input.box.minX - other.input.box.minX) <= 0.75
                                && Math.abs(this.input.box.minY - other.input.box.minY) <= 0.75
                                && Math.abs(this.input.box.minZ - other.input.box.minZ) <= 0.75
                                && Math.abs(this.input.attackerX - other.input.attackerX) <= 1.0
                                && Math.abs(this.input.attackerZ - other.input.attackerZ) <= 1.0
                                && Math.abs(this.input.motionX - other.input.motionX) <= 0.35
                                && Math.abs(this.input.motionY - other.input.motionY) <= 0.35
                                && Math.abs(this.input.motionZ - other.input.motionZ) <= 0.35;
            }
        }
    }

    private static final class Validation {
        private final long tick;
        private final VoidDisplacePlanner.Input input;
        private final int lead;
        private final VoidDisplacePlanner.Result result;
        private final boolean usable;

        private Validation(
                long tick,
                VoidDisplacePlanner.Input input,
                int lead,
                VoidDisplacePlanner.Result result,
                boolean usable) {
            this.tick = tick;
            this.input = input;
            this.lead = lead;
            this.result = result;
            this.usable = usable;
        }
    }
}
