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
    void turnStepIsLimitedToTheConfiguredRate() {
        assertEquals(6.0F, RotationHelper.stepAngle(0.0F, 90.0F, 6.0F), 1.0e-4);
        assertEquals(-6.0F, RotationHelper.stepAngle(0.0F, -90.0F, 6.0F), 1.0e-4);
    }

    @Test
    void turnStepLandsExactlyOnTheAimWhenTheRestFitsIntoTheStep() {
        assertEquals(4.0F, RotationHelper.stepAngle(0.0F, 4.0F, 6.0F), 1.0e-4);
        assertEquals(90.0F, RotationHelper.stepAngle(86.0F, 90.0F, 6.0F), 1.0e-4);
    }

    @Test
    void slowTurnRateStillConvergesInsteadOfFreezing() {
        float angle = 0.0F;
        for (int tick = 0; tick < 200 && angle != 90.0F; tick++) {
            angle = RotationHelper.stepAngle(angle, 90.0F, 1.0F);
        }

        assertEquals(90.0F, angle, 1.0e-3);
    }

    @Test
    void turnStepTakesTheShortWayAcrossTheYawSeam() {
        float angle = -176.0F;
        for (int tick = 0; tick < 10 && angle != 176.0F; tick++) {
            angle = RotationHelper.stepAngle(angle, 176.0F, 6.0F);
        }

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
