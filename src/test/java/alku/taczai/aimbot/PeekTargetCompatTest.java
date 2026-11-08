package alku.taczai.aimbot;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeekTargetCompatTest {
    @Test
    void samplesStayInsideTheRotatedPeekHitbox() {
        Vec3 center = new Vec3(4.0, 2.0, -3.0);
        Vec3 right = new Vec3(0.6, 0.0, 0.8);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 forward = new Vec3(-0.8, 0.0, 0.6);
        double halfRight = 0.3;
        double halfUp = 0.68;
        double halfForward = 0.2;

        List<Vec3> points = PeekTargetCompat.sampleOrientedBox(
                center, right, halfRight, up, halfUp, forward, halfForward
        );

        assertEquals(15, points.size());
        assertEquals(center, points.get(0));
        assertTrue(points.stream().allMatch(point -> inside(point, center, right, halfRight, up, halfUp, forward, halfForward)));
    }

    @Test
    void invalidPeekGeometryProducesNoAimPoints() {
        List<Vec3> points = PeekTargetCompat.sampleOrientedBox(
                Vec3.ZERO,
                Vec3.ZERO,
                0.3,
                new Vec3(0.0, 1.0, 0.0),
                0.68,
                new Vec3(0.0, 0.0, 1.0),
                0.2
        );

        assertTrue(points.isEmpty());
    }

    private static boolean inside(
            Vec3 point,
            Vec3 center,
            Vec3 right,
            double halfRight,
            Vec3 up,
            double halfUp,
            Vec3 forward,
            double halfForward
    ) {
        Vec3 relative = point.subtract(center);
        return Math.abs(relative.dot(right)) <= halfRight + 1.0e-9
                && Math.abs(relative.dot(up)) <= halfUp + 1.0e-9
                && Math.abs(relative.dot(forward)) <= halfForward + 1.0e-9;
    }
}
