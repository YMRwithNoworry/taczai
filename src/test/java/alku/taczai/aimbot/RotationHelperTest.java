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
    void predictionUsesRelativeShooterAndTargetVelocity() {
        Vec3 point = new Vec3(0.0, 1.6, 20.0);
        Vec3 relativeVelocity = new Vec3(0.2, 0.0, -0.1);

        assertEquals(new Vec3(1.0, 1.6, 19.5), RotationHelper.leadTarget(point, relativeVelocity, 5.0));
    }

    @Test
    void aimStrengthCorrectsMoreThanTheOriginalLinearSmoothing() {
        float strengthened = RotationHelper.smoothAngle(0.0F, 90.0F, 0.3F);
        float original = 90.0F * (1.0F - 0.3F);

        assertTrue(strengthened > original);
        assertTrue(strengthened <= 90.0F);
    }
}
