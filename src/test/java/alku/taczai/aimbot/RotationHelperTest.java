package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void closeTargetsUseDirectAimWhenWithinOneBulletTick() {
        Vec3 spawn = new Vec3(0.0, 1.6, 0.0);
        AABB closeTarget = new AABB(-0.3, 0.0, 3.7, 0.3, 1.8, 4.3);
        AABB distantTarget = new AABB(-0.3, 0.0, 7.7, 0.3, 1.8, 8.3);

        assertTrue(RotationHelper.shouldUseDirectCloseAim(spawn, closeTarget, 8.0));
        assertFalse(RotationHelper.shouldUseDirectCloseAim(spawn, distantTarget, 8.0));
        assertFalse(RotationHelper.shouldUseDirectCloseAim(spawn, closeTarget, 2.0));
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
