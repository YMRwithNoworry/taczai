package alku.taczai.aimbot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BallisticsHelperTest {
    @Test
    void taczConfiguredSpeedIsConvertedFromSecondsToTicks() {
        assertEquals(20.0, BallisticsHelper.speedPerTick(400.0, 1.0), 1.0e-9);
    }

    @Test
    void sniperAimCompensatesForTaczGravityAndFriction() {
        BallisticsHelper.Parameters parameters = new BallisticsHelper.Parameters(20.0, 0.15, 0.015, 20.0, 0.0);
        Vec3 start = Vec3.ZERO;
        Vec3 target = new Vec3(0.0, 0.0, 100.0);

        BallisticsHelper.AimSolution solution = BallisticsHelper.solveAim(
                start, Vec3.ZERO, target, Vec3.ZERO, parameters
        );
        Vec3 impact = BallisticsHelper.positionAtTime(
                start, solution.direction(), Vec3.ZERO, parameters, solution.flightTicks()
        );

        assertTrue(solution.direction().y > 0.0);
        assertTrue(solution.flightTicks() > 5.0 && solution.flightTicks() < 6.0);
        assertTrue(impact.distanceTo(target) < 1.0e-5);
    }

    @Test
    void movingTargetReceivesHorizontalLead() {
        BallisticsHelper.Parameters parameters = new BallisticsHelper.Parameters(20.0, 0.0, 0.0, 20.0, 0.0);

        BallisticsHelper.AimSolution solution = BallisticsHelper.solveAim(
                Vec3.ZERO,
                Vec3.ZERO,
                new Vec3(0.0, 0.0, 100.0),
                new Vec3(0.4, 0.0, 0.0),
                parameters
        );

        assertTrue(solution.direction().x > 0.0);
    }

    @Test
    void spreadValidationIncludesEveryExtremeDirection() {
        var directions = BallisticsHelper.spreadDirections(new Vec3(0.0, 0.0, 1.0), 0.05);

        assertEquals(27, directions.size());
        assertTrue(directions.stream().allMatch(direction -> Math.abs(direction.length() - 1.0) < 1.0e-9));
    }
}
