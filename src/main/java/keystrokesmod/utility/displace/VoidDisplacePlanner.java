package keystrokesmod.utility.displace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class VoidDisplacePlanner {
    public static final int TICKS = 36;
    public static final double CLUTCH_REACH = 4.5;
    private static final VoidDisplacePlanner.Result NO_DIRECTION =
            new VoidDisplacePlanner.Result(null, Collections.emptyList());

    static VoidDisplacePlanner.Input input(
            PlayerEntity target,
            PlayerEntity attacker,
            float referenceYaw,
            float yawOffset,
            double radius,
            boolean ignoreLowWall,
            boolean predictMovement) {
        VoidDisplacePlanner.Input input = new VoidDisplacePlanner.Input(target.getShape());
        input.eyeHeight = target.getEyeHeight();
        input.stepHeight = target.stepHeight;
        input.onGround = target.onGround;
        double observedX = target.x - target.prevX;
        double observedZ = target.z - target.prevZ;
        input.motionX = target.velocityX;
        input.motionZ = target.velocityZ;
        if ((predictMovement || Math.abs(input.motionX) + Math.abs(input.motionZ) < 0.001)
                && Math.hypot(observedX, observedZ) < 1.0) {
            input.motionX = observedX;
            input.motionZ = observedZ;
        }

        input.motionY = target.velocityY;
        if (predictMovement) {
            double observedY = target.y - target.prevY;
            if (input.onGround) {
                input.motionY = 0.0;
            } else if (Math.abs(observedY) >= 0.001 && Math.abs(observedY) < 1.0) {
                input.motionY = (observedY - 0.08) * 0.98;
            }
        }

        input.attackerX = attacker.x;
        input.attackerZ = attacker.z;
        input.knockback = EnchantmentHelper.getKnockbackLevel(attacker) + (attacker.isSprinting() ? 1 : 0);
        input.referenceYaw = referenceYaw;
        input.yawOffset = yawOffset;
        input.radius = radius;
        input.ignoreLowWall = ignoreLowWall;
        return input;
    }

    static VoidDisplacePlanner.Input predictLaunch(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input observed, int ticksUntilHit) {
        if (ticksUntilHit == 0) {
            return observed;
        }

        VoidDisplacePlanner.Input input = observed.copy();

        for (int tick = 0; tick < ticksUntilHit; tick++) {
            double y = input.onGround && input.motionY <= 0.0 ? -0.08 : input.motionY;
            if (!terrain.loaded(input.box.expanded(input.motionX, y, input.motionZ))) {
                break;
            }

            VoidDisplacePlanner.Move move =
                    moveWithStep(terrain, input, input.box, input.onGround, input.motionX, y, input.motionZ);
            input.box = move.box;
            input.onGround = y < 0.0 && move.y != y;
            if (move.x != input.motionX) {
                input.motionX = 0.0;
            }

            if (move.z != input.motionZ) {
                input.motionZ = 0.0;
            }

            input.motionY = input.onGround ? 0.0 : ((move.y == y ? y : 0.0) - 0.08) * 0.98;
            if (!input.onGround) {
                input.motionX *= 0.91;
                input.motionZ *= 0.91;
            }
        }

        return input;
    }

    public static VoidDisplacePlanner.Result plan(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input input, Float previousYaw) {
        return plan(terrain, input, previousYaw, true);
    }

    static boolean validate(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input input, float yaw, long deadline) {
        VoidDisplacePlanner.Trace trace = trace(terrain, input, impulse(input, yaw), 0, null, false, deadline);
        return trace.valid && trace.escaped && System.nanoTime() - deadline < 0L;
    }

    static VoidDisplacePlanner.Result plan(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input input, Float previousYaw, boolean debug) {
        if (!debug
                && terrain instanceof VoidGeometry
                && !((VoidGeometry) terrain).hasVoidBelow(input.box.minY - 12.0)) {
            return NO_DIRECTION;
        }

        VoidDisplacePlanner.TracePath path = new VoidDisplacePlanner.TracePath();
        List<VoidDisplacePlanner.Direction> directions = new ArrayList<>(48);

        for (int index = 0; index < 48; index++) {
            float yaw = input.referenceYaw + input.yawOffset + index * 7.5F;
            directions.add(new VoidDisplacePlanner.Direction(yaw, openVoidScore(terrain, input, yaw)));
        }

        directions.sort(Comparator.<VoidDisplacePlanner.Direction>comparingDouble(direction -> direction.score)
                .reversed());
        List<VoidDisplacePlanner.Candidate> nominal = new ArrayList<>();

        for (int index = 0; index < 4; index++) {
            addCandidate(nominal, terrain, input, directions.get(index).yaw, null, path, false, debug);
        }

        boolean[] sectorAdded = new boolean[8];

        for (VoidDisplacePlanner.Direction direction : directions) {
            int sector = Math.min(7, (int) ((direction.yaw + 180.0F) / 45.0F));
            if (!sectorAdded[sector]) {
                sectorAdded[sector] = true;
                addCandidate(nominal, terrain, input, direction.yaw, null, path, false, debug);
            }
        }

        if (previousYaw != null) {
            addCandidate(nominal, terrain, input, previousYaw, null, path, false, debug);
        }

        sort(nominal);
        if (!nominal.get(0).valid) {
            for (int index = 0; index < 8; index++) {
                float yaw = directions.get(index).yaw;
                addCandidate(nominal, terrain, input, yaw, null, path, false, debug);
                addCandidate(nominal, terrain, input, yaw + 3.75F, null, path, false, debug);
            }

            sort(nominal);
        }

        if (!nominal.get(0).valid) {
            return debug ? new VoidDisplacePlanner.Result(null, nominal) : NO_DIRECTION;
        }

        List<VoidDisplacePlanner.Candidate> refinements = new ArrayList<>(3);

        for (VoidDisplacePlanner.Candidate candidate : nominal) {
            if (candidate.valid && isDistinct(refinements, candidate.yaw, 15.0F)) {
                refinements.add(candidate);
                if (refinements.size() == 3) {
                    break;
                }
            }
        }

        for (VoidDisplacePlanner.Candidate candidate : refinements) {
            addCandidate(nominal, terrain, input, candidate.yaw - 3.75F, null, path, false, debug);
            addCandidate(nominal, terrain, input, candidate.yaw + 3.75F, null, path, false, debug);
        }

        sort(nominal);
        List<VoidDisplacePlanner.Candidate> finalists = new ArrayList<>(6);

        for (VoidDisplacePlanner.Candidate candidate : nominal) {
            if (!candidate.valid || finalists.size() == 6) {
                break;
            }

            if (finalists.size() < 3 || isDistinct(finalists, candidate.yaw, 15.0F)) {
                addCandidate(finalists, terrain, input, candidate.yaw, candidate.nominal, path, true, debug);
            }
        }

        sort(finalists);
        VoidDisplacePlanner.Candidate best = finalists.get(0);
        if (previousYaw != null) {
            for (VoidDisplacePlanner.Candidate candidate : finalists) {
                if (angle(candidate.yaw, previousYaw) < 0.01 && candidate.valid && candidate.compareTo(best) == 0) {
                    best = candidate;
                    break;
                }
            }
        }

        return new VoidDisplacePlanner.Result(best, debug ? finalists : Collections.emptyList());
    }

    private static double openVoidScore(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input input, float yaw) {
        double[] velocity = impulse(input, yaw);
        double originX = centerX(input.box);
        double originZ = centerZ(input.box);
        double x = originX;
        double y = input.box.minY;
        double z = originZ;
        double score = 0.0;
        boolean overVoid = false;

        for (int tick = 0; tick < 36; tick++) {
            x += velocity[0];
            y += velocity[1];
            z += velocity[2];
            overVoid = terrain.voidBelow(x, y, z);
            if (overVoid) {
                score += 2.0 - tick / 36.0;
            }

            if (input.box.minY - y >= 8.0 || Math.hypot(x - originX, z - originZ) >= input.radius) {
                break;
            }

            double friction =
                    tick == 0 && input.onGround ? terrain.friction(originX, input.box.minY - 1.0, originZ) : 0.91;
            velocity[0] *= friction;
            velocity[1] = (velocity[1] - 0.08) * 0.98;
            velocity[2] *= friction;
        }

        return score + (overVoid ? 4.0 : 0.0);
    }

    private static boolean isDistinct(List<VoidDisplacePlanner.Candidate> candidates, float yaw, float separation) {
        for (VoidDisplacePlanner.Candidate candidate : candidates) {
            if (angle(candidate.yaw, yaw) < separation) {
                return false;
            }
        }

        return true;
    }

    private static void addCandidate(
            List<VoidDisplacePlanner.Candidate> candidates,
            VoidDisplacePlanner.Terrain terrain,
            VoidDisplacePlanner.Input input,
            float yaw,
            VoidDisplacePlanner.Trace nominal,
            VoidDisplacePlanner.TracePath path,
            boolean recovery,
            boolean debug) {
        if (isDistinct(candidates, yaw, 0.01F)) {
            candidates.add(candidate(terrain, input, yaw, nominal, path, recovery, debug));
        }
    }

    public static VoidDisplacePlanner.Candidate candidate(
            VoidDisplacePlanner.Terrain terrain, VoidDisplacePlanner.Input input, float yaw, boolean recoveryChecks) {
        return candidate(terrain, input, yaw, null, new VoidDisplacePlanner.TracePath(), recoveryChecks, true);
    }

    private static VoidDisplacePlanner.Candidate candidate(
            VoidDisplacePlanner.Terrain terrain,
            VoidDisplacePlanner.Input input,
            float yaw,
            VoidDisplacePlanner.Trace nominal,
            VoidDisplacePlanner.TracePath path,
            boolean recoveryChecks,
            boolean debug) {
        yaw = MathHelper.wrapDegrees(yaw);
        double[] velocity = impulse(input, yaw);
        if (nominal == null) {
            nominal = trace(terrain, input, velocity, 0, path, debug, 0L);
        }

        boolean valid = nominal.valid && nominal.enteredVoid && nominal.escaped;
        List<VoidDisplacePlanner.Trace> responses =
                debug ? Collections.singletonList(nominal) : Collections.emptyList();
        int escapes = nominal.escaped ? 1 : 0;
        int clearEscapes = clearsClutch(nominal) ? 1 : 0;
        double outsideClutchTime = nominal.outsideClutchTime;
        double safety = nominal.safety;
        double clearance = nominal.clearance;
        if (recoveryChecks && (valid || debug && nominal.valid && nominal.enteredVoid)) {
            VoidDisplacePlanner.Trace towardLand = trace(terrain, input, velocity, 1, path, debug, 0L);
            VoidDisplacePlanner.Trace towardStart = trace(terrain, input, velocity, 2, path, debug, 0L);
            if (debug) {
                responses = Arrays.asList(nominal, towardLand, towardStart);
            }

            escapes += (towardLand.escaped ? 1 : 0) + (towardStart.escaped ? 1 : 0);
            clearEscapes += (clearsClutch(towardLand) ? 1 : 0) + (clearsClutch(towardStart) ? 1 : 0);
            outsideClutchTime =
                    Math.min(outsideClutchTime, Math.min(towardLand.outsideClutchTime, towardStart.outsideClutchTime));
            safety = Math.min(safety, Math.min(towardLand.safety, towardStart.safety));
            clearance = Math.min(clearance, Math.min(towardLand.clearance, towardStart.clearance));
        }

        float pushYaw = (float) Math.toDegrees(Math.atan2(-velocity[0], velocity[2]));
        double preference = Math.min(
                angle(yaw, input.referenceYaw + input.yawOffset), angle(yaw, input.referenceYaw - input.yawOffset));
        return new VoidDisplacePlanner.Candidate(
                yaw,
                pushYaw,
                nominal,
                responses,
                escapes,
                clearEscapes,
                outsideClutchTime,
                safety,
                clearance,
                preference,
                valid);
    }

    private static boolean clearsClutch(VoidDisplacePlanner.Trace trace) {
        return trace.escaped && trace.longestOutsideClutch >= 1.0;
    }

    public static double[] impulse(VoidDisplacePlanner.Input input, float yaw) {
        double dx = centerX(input.box) - input.attackerX;
        double dz = centerZ(input.box) - input.attackerZ;
        double distance = Math.hypot(dx, dz);
        if (distance < 1.0E-4) {
            dx = -Math.sin(Math.toRadians(input.referenceYaw));
            dz = Math.cos(Math.toRadians(input.referenceYaw));
            distance = 1.0;
        }

        float radians = yaw * (float) Math.PI / 180.0F;
        double x = input.motionX * 0.5 + dx / distance * 0.4 - MathHelper.sin(radians) * input.knockback * 0.5F;
        double y = Math.min(0.4, input.motionY * 0.5 + 0.4) + (input.knockback > 0 ? 0.1 : 0.0);
        double z = input.motionZ * 0.5 + dz / distance * 0.4 + MathHelper.cos(radians) * input.knockback * 0.5F;
        return new double[] {packetMotion(x), packetMotion(y), packetMotion(z)};
    }

    private static double packetMotion(double motion) {
        return (int) (Math.max(-3.9, Math.min(3.9, motion)) * 8000.0) / 8000.0;
    }

    private static VoidDisplacePlanner.Trace trace(
            VoidDisplacePlanner.Terrain terrain,
            VoidDisplacePlanner.Input input,
            double[] velocity,
            int control,
            VoidDisplacePlanner.TracePath path,
            boolean debug,
            long deadline) {
        VoidDisplacePlanner.Trace trace = new VoidDisplacePlanner.Trace(debug);
        Box box = input.box;
        double originX = centerX(box);
        double originY = box.minY;
        double originZ = centerZ(box);
        double motionX = velocity[0];
        double motionY = velocity[1];
        double motionZ = velocity[2];
        boolean onGround = input.onGround;
        boolean landed = false;
        int steps = 0;
        if (debug) {
            trace.points.add(new Vec3d(originX, originY, originZ));
        }

        for (int tick = 0; tick < 36; tick++) {
            if (deadline != 0L && System.nanoTime() - deadline >= 0L) {
                trace.valid = false;
                break;
            }

            double friction = onGround ? terrain.friction(centerX(box), box.minY - 1.0, centerZ(box)) : 0.91;
            if (control != 0) {
                double destinationX = originX;
                double destinationZ = originZ;
                if (control == 1) {
                    VoidDisplacePlanner.Surface nearest =
                            terrain.nearest(new Vec3d(centerX(box), box.minY + input.eyeHeight, centerZ(box)), 6.5);
                    if (nearest.point != null) {
                        destinationX = nearest.point.x;
                        destinationZ = nearest.point.z;
                    }
                }

                double dx = destinationX - centerX(box);
                double dz = destinationZ - centerZ(box);
                double distance = Math.hypot(dx, dz);
                if (distance > 0.01) {
                    double acceleration = onGround ? 0.021160276800000002 / (friction * friction * friction) : 0.026;
                    motionX += dx / distance * acceleration;
                    motionZ += dz / distance * acceleration;
                }
            }

            Box swept = box.expanded(motionX, motionY, motionZ);
            if (!terrain.loaded(swept)) {
                trace.valid = false;
                trace.reason = "Unloaded terrain";
                break;
            }

            Box projected = box.moved(0.0, originY - box.minY, 0.0)
                    .contract(0.03, 0.001, 0.03)
                    .expanded(motionX, 0.0, motionZ);

            for (Box wall : terrain.collisions(projected)) {
                if (wall.maxY > originY + 0.001 && (!input.ignoreLowWall || wall.maxY > originY + 1.0001)) {
                    trace.valid = false;
                    trace.reason = "Wall at launch height";
                    break;
                }
            }

            if (!trace.valid) {
                break;
            }

            VoidDisplacePlanner.Move move = moveWithStep(terrain, input, box, onGround, motionX, motionY, motionZ);
            onGround = motionY < 0.0 && move.y != motionY;
            trace.collisionLoss = trace.collisionLoss + (Math.abs(move.x - motionX) + Math.abs(move.z - motionZ));
            if (move.x != motionX) {
                motionX = 0.0;
            }

            if (move.y != motionY) {
                motionY = 0.0;
            }

            if (move.z != motionZ) {
                motionZ = 0.0;
            }

            Box previousBox = box;
            box = move.box;
            if (debug) {
                trace.points.add(new Vec3d(centerX(box), box.minY, centerZ(box)));
            }

            boolean voidBelow = fullyOverVoid(terrain, box);
            int samples = Math.max(2, (int) Math.ceil(Math.hypot(move.x, move.z) / 0.5));
            int voidSamples = voidBelow ? 1 << samples - 1 : 0;

            for (int sample = 1; sample < samples; sample++) {
                double fraction = (double) sample / samples;
                Box sampleBox = previousBox.moved(move.x * fraction, move.y * fraction, move.z * fraction);
                if (fullyOverVoid(terrain, sampleBox)) {
                    voidSamples |= 1 << sample - 1;
                }
            }

            trace.enteredVoid |= voidSamples != 0;
            if (path != null) {
                path.moves[tick] = move;
                path.voidSamples[tick] = voidSamples;
                steps++;
            }

            if (trace.enteredVoid && onGround) {
                landed = true;
            }

            if (terrain.recoveryVolume(box)) {
                landed = true;
                trace.reason = "Recoverable surface";
                break;
            }

            motionY = (motionY - 0.08) * 0.98F;
            motionX *= friction;
            motionZ *= friction;
            trace.escaped = voidBelow && !landed;
            if (originY - box.minY >= 8.0
                    || Math.hypot(centerX(box) - originX, centerZ(box) - originZ) >= input.radius
                    || landed
                    || onGround && tick > 10 && Math.abs(motionX) + Math.abs(motionZ) < 0.01) {
                break;
            }
        }

        trace.escaped = trace.escaped & (trace.valid && !landed);
        if (trace.escaped) {
            trace.reason = "Reachable void";
        } else if (landed) {
            trace.reason = "Recoverable landing";
        }

        if (path != null && trace.enteredVoid && (trace.escaped || control != 0 || debug)) {
            scoreTrace(terrain, input, trace, path, steps, debug);
        }

        return trace;
    }

    private static void scoreTrace(
            VoidDisplacePlanner.Terrain terrain,
            VoidDisplacePlanner.Input input,
            VoidDisplacePlanner.Trace trace,
            VoidDisplacePlanner.TracePath path,
            int steps,
            boolean debug) {
        Box previousBox = input.box;
        double exposure = 0.0;
        double landward = 0.0;
        double peakClutchRisk = 0.0;
        double minimumClearance = 6.5;
        double consecutiveOutsideClutch = 0.0;
        boolean enteredVoid = false;

        for (int tick = 0; tick < steps; tick++) {
            VoidDisplacePlanner.Move move = path.moves[tick];
            int samples = Math.max(2, (int) Math.ceil(Math.hypot(move.x, move.z) / 0.5));

            for (int sample = 1; sample <= samples; sample++) {
                double fraction = (double) sample / samples;
                boolean sampleVoid = (path.voidSamples[tick] & 1 << sample - 1) != 0;
                enteredVoid |= sampleVoid;
                double x = move.x * fraction;
                double z = move.z * fraction;
                Vec3d eye = new Vec3d(
                        (previousBox.minX + x + (previousBox.maxX + x)) * 0.5,
                        previousBox.minY + move.y * fraction + input.eyeHeight,
                        (previousBox.minZ + z + (previousBox.maxZ + z)) * 0.5);
                VoidDisplacePlanner.Surface surface = terrain.nearest(eye, 6.5);
                double proximity = surface.point != null && surface.distance <= 4.5
                        ? 0.25 + 0.75 * Math.max(0.0, 1.0 - surface.distance / 4.5)
                        : 0.0;
                if (!sampleVoid) {
                    proximity = 1.0;
                } else {
                    minimumClearance = Math.min(minimumClearance, surface.distance);
                    peakClutchRisk = Math.max(peakClutchRisk, proximity);
                }

                double weight = 1.0 + Math.max(0.0, 8.0 - tick - fraction) * 0.125;
                if (sampleVoid && surface.distance > 4.5) {
                    consecutiveOutsideClutch += 1.0 / samples;
                    trace.longestOutsideClutch = Math.max(trace.longestOutsideClutch, consecutiveOutsideClutch);
                    trace.outsideClutchTime += weight / samples;
                } else {
                    consecutiveOutsideClutch = 0.0;
                }

                exposure += weight * proximity / samples;
                if (surface.point != null) {
                    double dx = surface.point.x - eye.x;
                    double dz = surface.point.z - eye.z;
                    double distance = Math.hypot(dx, dz);
                    if (distance > 0.001) {
                        double approach = Math.max(0.0, (move.x * dx + move.z * dz) / distance);
                        landward += weight * proximity * approach / samples;
                    }

                    if (debug && sample == samples && tick % 3 == 0 && enteredVoid) {
                        trace.surfaces.add(new VoidDisplacePlanner.SurfaceProbe(eye, surface.point));
                    }
                }
            }

            previousBox = move.box;
        }

        trace.clearance = minimumClearance;
        trace.safety = 1.0 / (1.0 + exposure + landward * 1.5 + peakClutchRisk * 2.0);
        if (!trace.escaped) {
            trace.safety *= 0.25;
            trace.outsideClutchTime = 0.0;
        }
    }

    private static boolean fullyOverVoid(VoidDisplacePlanner.Terrain terrain, Box box) {
        double inset = 0.03;
        return terrain.voidBelow(centerX(box), box.minY, centerZ(box))
                && terrain.voidBelow(box.minX + inset, box.minY, box.minZ + inset)
                && terrain.voidBelow(box.maxX - inset, box.minY, box.minZ + inset)
                && terrain.voidBelow(box.minX + inset, box.minY, box.maxZ - inset)
                && terrain.voidBelow(box.maxX - inset, box.minY, box.maxZ - inset);
    }

    private static VoidDisplacePlanner.Move moveWithStep(
            VoidDisplacePlanner.Terrain terrain,
            VoidDisplacePlanner.Input input,
            Box box,
            boolean onGround,
            double x,
            double y,
            double z) {
        VoidDisplacePlanner.Move move = move(terrain, box, x, y, z);
        if ((onGround || y < 0.0 && move.y != y) && (move.x != x || move.z != z) && input.stepHeight > 0.0) {
            VoidDisplacePlanner.Move up = move(terrain, box, 0.0, input.stepHeight, 0.0);
            VoidDisplacePlanner.Move across = move(terrain, up.box, x, 0.0, z);
            VoidDisplacePlanner.Move down = move(terrain, across.box, 0.0, -up.y, 0.0);
            if (across.x * across.x + across.z * across.z > move.x * move.x + move.z * move.z) {
                return new VoidDisplacePlanner.Move(down.box, across.x, up.y + down.y, across.z);
            }
        }

        return move;
    }

    private static VoidDisplacePlanner.Move move(
            VoidDisplacePlanner.Terrain terrain, Box box, double x, double y, double z) {
        List<Box> collisions = terrain.collisions(box.expanded(x, y, z));

        for (Box collision : collisions) {
            y = collision.intersectY(box, y);
        }

        box = box.moved(0.0, y, 0.0);

        for (Box collision : collisions) {
            x = collision.intersectX(box, x);
        }

        box = box.moved(x, 0.0, 0.0);

        for (Box collision : collisions) {
            z = collision.intersectZ(box, z);
        }

        return new VoidDisplacePlanner.Move(box.moved(0.0, 0.0, z), x, y, z);
    }

    private static void sort(List<VoidDisplacePlanner.Candidate> candidates) {
        Collections.sort(candidates, Comparator.reverseOrder());
    }

    private static double angle(float first, float second) {
        return Math.abs(MathHelper.wrapDegrees(first - second));
    }

    private static double centerX(Box box) {
        return (box.minX + box.maxX) * 0.5;
    }

    private static double centerZ(Box box) {
        return (box.minZ + box.maxZ) * 0.5;
    }

    public static final class Candidate implements Comparable<VoidDisplacePlanner.Candidate> {
        public final float yaw;
        public final float pushYaw;
        public final VoidDisplacePlanner.Trace nominal;
        public final List<VoidDisplacePlanner.Trace> responses;
        public final int escapes;
        private final int clearEscapes;
        private final double outsideClutchTime;
        public final double safety;
        public final double clearance;
        public final double preference;
        public final boolean valid;

        private Candidate(
                float yaw,
                float pushYaw,
                VoidDisplacePlanner.Trace nominal,
                List<VoidDisplacePlanner.Trace> responses,
                int escapes,
                int clearEscapes,
                double outsideClutchTime,
                double safety,
                double clearance,
                double preference,
                boolean valid) {
            this.yaw = yaw;
            this.pushYaw = pushYaw;
            this.nominal = nominal;
            this.responses = responses;
            this.escapes = escapes;
            this.clearEscapes = clearEscapes;
            this.outsideClutchTime = outsideClutchTime;
            this.safety = safety;
            this.clearance = clearance;
            this.preference = preference;
            this.valid = valid;
        }

        public int compareTo(VoidDisplacePlanner.Candidate other) {
            if (this.valid != other.valid) {
                return this.valid ? 1 : -1;
            }

            int clear = Integer.compare(this.clearEscapes, other.clearEscapes);
            if (clear != 0) {
                return clear;
            }

            int escaping = Integer.compare(this.escapes, other.escapes);
            if (escaping != 0) {
                return escaping;
            }

            int outside = Long.compare(
                    Math.round(this.outsideClutchTime * 100.0), Math.round(other.outsideClutchTime * 100.0));
            if (outside != 0) {
                return outside;
            }

            int recovery = Long.compare(Math.round(this.safety * 10000.0), Math.round(other.safety * 10000.0));
            if (recovery != 0) {
                return recovery;
            }

            int distance = Long.compare(Math.round(this.clearance * 10.0), Math.round(other.clearance * 10.0));
            if (distance != 0) {
                return distance;
            }

            int collision = Double.compare(other.nominal.collisionLoss, this.nominal.collisionLoss);
            return collision != 0 ? collision : Double.compare(other.preference, this.preference);
        }
    }

    private static final class Direction {
        private final float yaw;
        private final double score;

        private Direction(float yaw, double score) {
            this.yaw = MathHelper.wrapDegrees(yaw);
            this.score = score;
        }
    }

    public static final class Input {
        public Box box;
        public double eyeHeight = 1.62;
        public double stepHeight = 0.6;
        public double motionX;
        public double motionY;
        public double motionZ;
        public double attackerX;
        public double attackerZ = -2.0;
        public int knockback = 1;
        public boolean onGround = true;
        public float referenceYaw;
        public float yawOffset = 90.0F;
        public double radius = 6.0;
        public boolean ignoreLowWall;

        public Input(Box box) {
            this.box = box;
        }

        boolean matches(VoidDisplacePlanner.Input other) {
            return this.box.minX == other.box.minX
                    && this.box.minY == other.box.minY
                    && this.box.minZ == other.box.minZ
                    && this.box.maxX == other.box.maxX
                    && this.box.maxY == other.box.maxY
                    && this.box.maxZ == other.box.maxZ
                    && this.eyeHeight == other.eyeHeight
                    && this.stepHeight == other.stepHeight
                    && this.onGround == other.onGround
                    && this.motionX == other.motionX
                    && this.motionY == other.motionY
                    && this.motionZ == other.motionZ
                    && this.attackerX == other.attackerX
                    && this.attackerZ == other.attackerZ
                    && this.knockback == other.knockback
                    && this.referenceYaw == other.referenceYaw
                    && this.yawOffset == other.yawOffset
                    && this.radius == other.radius
                    && this.ignoreLowWall == other.ignoreLowWall;
        }

        VoidDisplacePlanner.Input copy() {
            VoidDisplacePlanner.Input input = new VoidDisplacePlanner.Input(this.box);
            input.eyeHeight = this.eyeHeight;
            input.stepHeight = this.stepHeight;
            input.motionX = this.motionX;
            input.motionY = this.motionY;
            input.motionZ = this.motionZ;
            input.attackerX = this.attackerX;
            input.attackerZ = this.attackerZ;
            input.knockback = this.knockback;
            input.onGround = this.onGround;
            input.referenceYaw = this.referenceYaw;
            input.yawOffset = this.yawOffset;
            input.radius = this.radius;
            input.ignoreLowWall = this.ignoreLowWall;
            return input;
        }
    }

    private static final class Move {
        private final Box box;
        private final double x;
        private final double y;
        private final double z;

        private Move(Box box, double x, double y, double z) {
            this.box = box;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static final class Result {
        public final VoidDisplacePlanner.Candidate best;
        public final List<VoidDisplacePlanner.Candidate> candidates;

        private Result(VoidDisplacePlanner.Candidate best, List<VoidDisplacePlanner.Candidate> candidates) {
            this.best = best;
            this.candidates = Collections.unmodifiableList(candidates);
        }
    }

    public static final class Surface {
        public final double distance;
        public final Vec3d point;

        public Surface(double distance, Vec3d point) {
            this.distance = distance;
            this.point = point;
        }
    }

    public static final class SurfaceProbe {
        public final Vec3d eye;
        public final Vec3d surface;

        private SurfaceProbe(Vec3d eye, Vec3d surface) {
            this.eye = eye;
            this.surface = surface;
        }
    }

    public interface Terrain {
        List<Box> collisions(Box var1);

        boolean loaded(Box var1);

        boolean voidBelow(double var1, double var3, double var5);

        double friction(double var1, double var3, double var5);

        VoidDisplacePlanner.Surface nearest(Vec3d var1, double var2);

        boolean recoveryVolume(Box var1);
    }

    public static final class Trace {
        public final List<Vec3d> points;
        public final List<VoidDisplacePlanner.SurfaceProbe> surfaces;
        public boolean valid = true;
        public boolean enteredVoid;
        public boolean escaped;
        public double safety;
        public double clearance;
        public double collisionLoss;
        private double outsideClutchTime;
        private double longestOutsideClutch;
        public String reason = "No reachable void";

        private Trace(boolean debug) {
            this.points = debug ? new ArrayList<>() : Collections.emptyList();
            this.surfaces = debug ? new ArrayList<>() : Collections.emptyList();
        }
    }

    private static final class TracePath {
        private final VoidDisplacePlanner.Move[] moves = new VoidDisplacePlanner.Move[36];
        private final int[] voidSamples = new int[36];

        private TracePath() {}
    }
}
