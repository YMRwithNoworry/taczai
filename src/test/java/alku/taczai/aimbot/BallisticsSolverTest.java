package alku.taczai.aimbot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticsSolverTest {
    @Test
    void sniperAimCompensatesForTaczGravityAndFriction() {
        BallisticsSolver.Parameters parameters = new BallisticsSolver.Parameters(
                20.0, 0.15, 0.015, 20.0, true
        );
        Vec3 spawn = Vec3.ZERO;
        Vec3 target = new Vec3(0.0, 0.0, 100.0);

        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                spawn, Vec3.ZERO, target, Vec3.ZERO, parameters
        );

        assertTrue(solution.reliable());
        assertTrue(solution.direction().y > 0.0);

        Vec3 impact = BallisticsSolver.positionAtTime(
                spawn, solution.direction(), Vec3.ZERO, parameters, solution.flightTicks()
        );
        assertTrue(impact.distanceTo(target) < 1.0e-4);
    }

    @Test
    void movingTargetReceivesHorizontalLead() {
        BallisticsSolver.Parameters parameters = new BallisticsSolver.Parameters(
                20.0, 0.0, 0.0, 20.0, true
        );
        Vec3 spawn = Vec3.ZERO;
        Vec3 target = new Vec3(0.0, 0.0, 100.0);
        Vec3 targetVelocity = new Vec3(0.4, 0.0, 0.0);

        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                spawn, Vec3.ZERO, target, targetVelocity, parameters
        );

        assertTrue(solution.reliable());
        assertTrue(solution.direction().x > 0.0);

        Vec3 futureTarget = target.add(targetVelocity.scale(solution.flightTicks()));
        Vec3 impact = BallisticsSolver.positionAtTime(
                spawn, solution.direction(), Vec3.ZERO, parameters, solution.flightTicks()
        );
        assertTrue(impact.distanceTo(futureTarget) < 1.0e-4);
    }

    @Test
    void shooterVelocityIsCompensated() {
        BallisticsSolver.Parameters parameters = new BallisticsSolver.Parameters(
                20.0, 0.0, 0.0, 20.0, true
        );
        Vec3 shooterVelocity = new Vec3(0.5, 0.0, 0.0);

        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                Vec3.ZERO,
                shooterVelocity,
                new Vec3(0.0, 0.0, 100.0),
                Vec3.ZERO,
                parameters
        );

        assertTrue(solution.reliable());
        assertTrue(solution.direction().x < 0.0);

        Vec3 impact = BallisticsSolver.positionAtTime(
                Vec3.ZERO, solution.direction(), shooterVelocity, parameters, solution.flightTicks()
        );
        assertTrue(impact.distanceTo(new Vec3(0.0, 0.0, 100.0)) < 1.0e-4);
    }

    @Test
    void unreachableTargetFallsBackToDirectAimWithoutBreakingTheLock() {
        BallisticsSolver.Parameters parameters = new BallisticsSolver.Parameters(
                1.0, 0.0, 0.0, 1.0, true
        );

        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                Vec3.ZERO, Vec3.ZERO, new Vec3(0.0, 0.0, 100.0), Vec3.ZERO, parameters
        );

        assertFalse(solution.reliable());
        assertTrue(solution.hasDirection());
        assertTrue(solution.direction().z > 0.99);
    }
}
