package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetSelectorTest {
    @Test
    void closeTargetIntersectingCrosshairHasPerfectAimScore() {
        Vec3 eyePosition = new Vec3(0.0, 1.62, 0.0);
        Vec3 lookDirection = new Vec3(0.0, 0.0, 1.0);
        AABB closeTarget = new AABB(-0.3, 0.0, 0.4, 0.3, 1.8, 1.0);

        assertEquals(0.0, TargetSelector.aimScore(eyePosition, lookDirection, closeTarget), 1.0e-9);
    }

    @Test
    void proneTargetVisibilityPointsStayInsideCurrentBoundingBox() {
        AABB proneTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6);

        var points = TargetSelector.visibilityPoints(proneTarget);

        assertEquals(3, points.size());
        assertTrue(points.stream().allMatch(proneTarget::contains));
    }

    @Test
    void fovUsesMaximumAngularOffsetFromCrosshair() {
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        assertTrue(TargetSelector.isWithinFov(look, directionAtDegrees(0.0), 20.0));
        assertTrue(TargetSelector.isWithinFov(look, directionAtDegrees(20.0), 20.0));
        assertFalse(TargetSelector.isWithinFov(look, directionAtDegrees(20.1), 20.0));
        assertFalse(TargetSelector.isWithinFov(look, directionAtDegrees(180.0), 20.0));
    }

    private static Vec3 directionAtDegrees(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec3(Math.sin(radians), 0.0, Math.cos(radians));
    }
}
