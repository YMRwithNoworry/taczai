package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimRotationControllerTest {
    @Test
    void largeTurnMovesTowardTargetWithoutSnappingAndEventuallySettles() {
        AimRotationController controller = new AimRotationController();
        controller.reset(0.0F, 0.0F);

        float[] firstFrame = controller.update(90.0F, 30.0F, 1.0 / 60.0);

        assertTrue(firstFrame[0] > 0.0F && firstFrame[0] < 5.0F);
        assertTrue(firstFrame[1] > 0.0F && firstFrame[1] < 5.0F);

        float[] rotation = firstFrame;
        for (int frame = 0; frame < 240; frame++) {
            rotation = controller.update(90.0F, 30.0F, 1.0 / 60.0);
        }

        assertEquals(90.0F, rotation[0], 0.1F);
        assertEquals(30.0F, rotation[1], 0.1F);
    }

    @Test
    void yawUsesTheShortPathAcrossTheWrapBoundary() {
        AimRotationController controller = new AimRotationController();
        controller.reset(179.0F, 0.0F);

        float[] rotation = controller.update(-179.0F, 0.0F, 1.0 / 60.0);

        assertTrue(rotation[0] > 179.0F || rotation[0] < -179.0F);
    }

    @Test
    void zeroElapsedTimeDoesNotMoveTheCamera() {
        AimRotationController controller = new AimRotationController();
        controller.reset(12.0F, -8.0F);

        float[] rotation = controller.update(90.0F, 30.0F, 0.0);

        assertEquals(12.0F, rotation[0], 1.0e-6F);
        assertEquals(-8.0F, rotation[1], 1.0e-6F);
    }
}
