package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RotationHelperTest {
    @Test
    void proneAimPointsStayInsideCurrentBoundingBox() {
        AABB proneTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6);

        var bodyPoint = RotationHelper.targetPoint(proneTarget, false);
        var headPoint = RotationHelper.targetPoint(proneTarget, true);

        assertEquals(proneTarget.getCenter(), bodyPoint);
        assertTrue(proneTarget.contains(headPoint));
        assertTrue(headPoint.y > bodyPoint.y);
    }

    @Test
    void aThreeTickTurnEasesIntoTheAim() {
        // 90 degrees over three ticks: 50 up front, then 30, then the last 10 to arrive.
        assertEquals(50.0F, RotationHelper.stepAngle(0.0F, 90.0F, 3), 1.0e-3);
        assertEquals(80.0F, RotationHelper.stepAngle(50.0F, 90.0F, 2), 1.0e-3);
        assertEquals(90.0F, RotationHelper.stepAngle(80.0F, 90.0F, 1), 1.0e-3);
    }

    @Test
    void everyTickOfATurnMovesLessThanTheOneBeforeIt() {
        float angle = 0.0F;
        float previousStep = Float.MAX_VALUE;
        for (int remaining = 3; remaining >= 1; remaining--) {
            float next = RotationHelper.stepAngle(angle, 90.0F, remaining);
            float step = Math.abs(next - angle);

            assertTrue(step < previousStep);
            previousStep = step;
            angle = next;
        }
    }

    @Test
    void theLastTickOfATurnLandsExactlyOnTheAim() {
        float angle = 0.0F;
        for (int remaining = 3; remaining >= 1; remaining--) {
            angle = RotationHelper.stepAngle(angle, 90.0F, remaining);
        }

        assertEquals(90.0F, angle, 1.0e-3);
    }

    @Test
    void aTurnNeverSnapsAndTakesTheShortWayAcrossTheYawSeam() {
        float angle = RotationHelper.stepAngle(-176.0F, 176.0F, 2);
        assertTrue(Math.abs(angle + 176.0F) > 1.0e-3);

        angle = RotationHelper.stepAngle(angle, 176.0F, 1);
        assertEquals(176.0F, angle, 1.0e-3);
    }

    @Test
    void directionConversionUsesMinecraftYawAndPitchConventions() {
        float[] forward = RotationHelper.directionToRotation(null, new Vec3(0.0, 0.0, 1.0));
        assertEquals(0.0F, forward[0], 1.0e-4);
        assertEquals(0.0F, forward[1], 1.0e-4);

        float[] up = RotationHelper.directionToRotation(null, new Vec3(0.0, 1.0, 0.0));
        assertEquals(-90.0F, up[1], 1.0e-4);
    }
}
