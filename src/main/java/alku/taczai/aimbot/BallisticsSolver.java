package alku.taczai.aimbot;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Solves the aim direction from configured bullet speed, friction, and gravity.
 *
 * <p>The solver leads moving targets and compensates for bullet drop, but keeps the
 * launch direction independent of the shooter's movement.</p>
 */
final class BallisticsSolver {
    static final double TICKS_PER_SECOND = 20.0;
    static final double DEFAULT_SPEED_PER_TICK = 4.0;
    static final double DEFAULT_LIFE_TICKS = 40.0;

    private static final double SEARCH_STEP_TICKS = 0.05;
    private static final double MIN_TRAVEL_FACTOR = 1.0e-9;
    private static final int REFINE_ITERATIONS = 48;
    private static final double DIRECTION_EPSILON_SQR = 1.0e-12;

    private BallisticsSolver() {
    }

    record Parameters(
            double speedPerTick,
            double gravity,
            double friction,
            double lifeTicks,
            boolean reliable
    ) {
        Parameters {
            speedPerTick = Math.max(1.0e-3, speedPerTick);
            gravity = Math.max(0.0, gravity);
            friction = Math.max(0.0, Math.min(0.999, friction));
            lifeTicks = Math.max(1.0, lifeTicks);
        }

        static Parameters fallback() {
            return new Parameters(DEFAULT_SPEED_PER_TICK, 0.0, 0.0, DEFAULT_LIFE_TICKS, false);
        }
    }

    record AimSolution(Vec3 direction, double flightTicks, boolean reliable) {
        boolean hasDirection() {
            return direction.lengthSqr() > DIRECTION_EPSILON_SQR;
        }
    }

    /** Bullet spawn position used by TACZ: current player position and eye height minus 0.1 blocks. */
    static Vec3 spawnPosition(LivingEntity shooter) {
        return spawnPosition(shooter.getX(), shooter.getEyeY(), shooter.getZ());
    }

    static Vec3 spawnPosition(double x, double eyeY, double z) {
        return new Vec3(x, eyeY - 0.1, z);
    }

    /**
     * Last observed per-tick displacement of the target. TACZ's own hitbox
     * compensation uses the same difference, which makes it more reliable for
     * remote players than {@link net.minecraft.world.entity.Entity#getDeltaMovement()}.
     */
    static Vec3 targetVelocity(LivingEntity target) {
        return new Vec3(
                target.getX() - target.xo,
                target.getY() - target.yo,
                target.getZ() - target.zo
        );
    }

    static AimSolution solve(
            Vec3 spawn,
            Vec3 targetPoint,
            Vec3 targetVelocity,
            Parameters parameters
    ) {
        Vec3 toTarget = targetPoint.subtract(spawn);
        double distance = toTarget.length();
        if (distance < 1.0e-6) {
            return directSolution(toTarget);
        }

        double maxFlightTicks = Math.max(0.0, parameters.lifeTicks() - 1.0);
        if (maxFlightTicks <= 0.0) {
            return directSolution(toTarget);
        }

        double previousTime = 0.0;
        double previousError = interceptError(
                spawn, targetPoint, targetVelocity, parameters, previousTime
        );
        double bestTime = 0.0;
        double bestError = Double.POSITIVE_INFINITY;

        for (double time = SEARCH_STEP_TICKS; time <= maxFlightTicks + 1.0e-9; time += SEARCH_STEP_TICKS) {
            double error = interceptError(
                    spawn, targetPoint, targetVelocity, parameters, time
            );
            if (Double.isFinite(error)) {
                double absoluteError = Math.abs(error);
                if (absoluteError < bestError) {
                    bestError = absoluteError;
                    bestTime = time;
                }

                // The required launch speed falls from "impossible" towards the
                // configured bullet speed, so the first + to - crossing is the
                // earliest valid intercept.
                if (previousError > 0.0 && error <= 0.0) {
                    double root = refineRoot(
                            spawn, targetPoint, targetVelocity, parameters, previousTime, time
                    );
                    return solutionAt(
                            spawn, targetPoint, targetVelocity, parameters, root
                    );
                }
            }

            previousTime = time;
            previousError = error;
        }

        // No sign change means the bullet cannot reach the target inside its
        // lifetime. Keep a best-effort direct aim instead of breaking target lock.
        if (Double.isFinite(bestError) && bestError <= parameters.speedPerTick() * 1.0e-3) {
            return solutionAt(spawn, targetPoint, targetVelocity, parameters, bestTime);
        }
        return directSolution(toTarget);
    }

    /**
     * Position of a bullet at {@code ticks} after spawn. Used by the solver
     * itself and by tests; exposed for verification.
     */
    static Vec3 positionAtTime(
            Vec3 spawn,
            Vec3 direction,
            Parameters parameters,
            double ticks
    ) {
        double travel = travelFactor(ticks, parameters.friction());
        Vec3 initialVelocity = direction.normalize().scale(parameters.speedPerTick());
        return spawn.add(initialVelocity.scale(travel))
                .add(0.0, -gravityDisplacement(ticks, parameters.gravity(), parameters.friction()), 0.0);
    }

    static double travelFactor(double ticks, double friction) {
        if (ticks <= 0.0) {
            return 0.0;
        }

        int fullTicks = (int) Math.floor(ticks);
        double partialTick = Math.max(0.0, ticks - fullTicks);
        double drag = 1.0 - friction;
        double fullTravel = friction < MIN_TRAVEL_FACTOR
                ? fullTicks
                : (1.0 - Math.pow(drag, fullTicks)) / friction;
        return fullTravel + partialTick * Math.pow(drag, fullTicks);
    }

    /** Positive downward displacement caused by gravity over {@code ticks}. */
    static double gravityDisplacement(double ticks, double gravity, double friction) {
        if (ticks <= 0.0 || gravity <= 0.0) {
            return 0.0;
        }

        double travel = travelFactor(ticks, friction);
        if (friction < MIN_TRAVEL_FACTOR) {
            int fullTicks = (int) Math.floor(ticks);
            double partialTick = Math.max(0.0, ticks - fullTicks);
            return gravity * (fullTicks * (fullTicks - 1.0) * 0.5 + partialTick * fullTicks);
        }
        return gravity * (ticks - travel) / friction;
    }

    private static double interceptError(
            Vec3 spawn,
            Vec3 targetPoint,
            Vec3 targetVelocity,
            Parameters parameters,
            double time
    ) {
        double travel = travelFactor(time, parameters.friction());
        if (travel < MIN_TRAVEL_FACTOR) {
            return Double.POSITIVE_INFINITY;
        }

        double drop = gravityDisplacement(time, parameters.gravity(), parameters.friction());
        Vec3 futureTarget = targetPoint.add(targetVelocity.scale(time));
        Vec3 requiredInitialVelocity = futureTarget
                .subtract(spawn)
                .add(0.0, drop, 0.0)
                .scale(1.0 / travel);
        double requiredSpeed = requiredInitialVelocity.length();
        if (!Double.isFinite(requiredSpeed)) {
            return Double.POSITIVE_INFINITY;
        }
        return requiredSpeed - parameters.speedPerTick();
    }

    private static AimSolution solutionAt(
            Vec3 spawn,
            Vec3 targetPoint,
            Vec3 targetVelocity,
            Parameters parameters,
            double time
    ) {
        double travel = travelFactor(time, parameters.friction());
        if (travel < MIN_TRAVEL_FACTOR) {
            return directSolution(targetPoint.subtract(spawn));
        }

        double drop = gravityDisplacement(time, parameters.gravity(), parameters.friction());
        Vec3 futureTarget = targetPoint.add(targetVelocity.scale(time));
        Vec3 requiredInitialVelocity = futureTarget
                .subtract(spawn)
                .add(0.0, drop, 0.0)
                .scale(1.0 / travel);
        if (requiredInitialVelocity.lengthSqr() < DIRECTION_EPSILON_SQR) {
            requiredInitialVelocity = targetPoint.subtract(spawn);
        }
        return new AimSolution(normalizeOrZero(requiredInitialVelocity), time, true);
    }

    private static double refineRoot(
            Vec3 spawn,
            Vec3 targetPoint,
            Vec3 targetVelocity,
            Parameters parameters,
            double lower,
            double upper
    ) {
        double low = lower;
        double high = upper;
        for (int iteration = 0; iteration < REFINE_ITERATIONS; iteration++) {
            double middle = (low + high) * 0.5;
            double error = interceptError(
                    spawn, targetPoint, targetVelocity, parameters, middle
            );
            if (error > 0.0 || !Double.isFinite(error)) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return (low + high) * 0.5;
    }

    private static AimSolution directSolution(Vec3 toTarget) {
        return new AimSolution(normalizeOrZero(toTarget), 0.0, false);
    }

    private static Vec3 normalizeOrZero(Vec3 vector) {
        return vector.lengthSqr() < DIRECTION_EPSILON_SQR ? Vec3.ZERO : vector.normalize();
    }
}
