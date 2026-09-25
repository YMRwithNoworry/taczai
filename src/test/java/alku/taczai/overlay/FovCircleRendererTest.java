package alku.taczai.overlay;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FovCircleRendererTest {

    @Test
    void radiusFollowsThePerspectiveProjectionOfTheCone() {
        // The half angle of the cone maps to tan(angle) / tan(fov / 2) of the half screen height.
        Matrix4f projection = new Matrix4f()
                .setPerspective((float) Math.toRadians(70.0), 16.0F / 9.0F, 0.05F, 1000.0F);
        double radius = FovCircleRenderer.computeRadiusPixels(480.0, projection.m11(), 20.0);
        double expected = 240.0 * Math.tan(Math.toRadians(20.0)) / Math.tan(Math.toRadians(35.0));
        assertEquals(expected, radius, 1.0e-3);
    }

    @Test
    void radiusIsZeroWhenTheInputsAreUnusable() {
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(0.0, 1.4281, 20.0));
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(-10.0, 1.4281, 20.0));
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(Double.NaN, 1.4281, 20.0));
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(480.0, 0.0, 20.0));
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(480.0, Double.NaN, 20.0));
        assertEquals(0.0, FovCircleRenderer.computeRadiusPixels(480.0, 1.4281, Double.NaN));
    }

    @Test
    void radiusGrowsWithTheConeAngle() {
        double previous = -1.0;
        for (double angle = 1.0; angle <= 80.0; angle += 1.0) {
            double radius = FovCircleRenderer.computeRadiusPixels(480.0, 1.4281, angle);
            assertTrue(radius > previous, "radius must grow with the cone angle at " + angle);
            previous = radius;
        }
    }

    @Test
    void wideConesAreCappedSoTheRadiusStaysFinite() {
        assertEquals(FovCircleRenderer.MAX_DRAWABLE_ANGLE, FovCircleRenderer.clampAngle(180.0), 1.0e-9);
        assertEquals(0.0, FovCircleRenderer.clampAngle(-5.0), 1.0e-9);
        assertTrue(Double.isFinite(FovCircleRenderer.computeRadiusPixels(480.0, 1.4281, 180.0)));
    }

    @Test
    void ringSpansWrapTheRequestedRadius() {
        assertEquals(100.5, FovCircleRenderer.ringOuterHalfWidth(100.0, 0.5, 0), 1.0e-6);
        assertEquals(99.5, FovCircleRenderer.ringInnerHalfWidth(100.0, 0.5, 0), 1.0e-6);
    }

    @Test
    void ringIsEmptyOutsideItsOuterEdge() {
        assertEquals(0.0, FovCircleRenderer.ringOuterHalfWidth(100.0, 0.5, 101), 1.0e-9);
        assertEquals(0.0, FovCircleRenderer.ringInnerHalfWidth(100.0, 0.5, 101), 1.0e-9);
    }

    @Test
    void ringIsSymmetricAroundTheCentreRow() {
        for (int dy = 0; dy <= 100; dy++) {
            assertEquals(
                    FovCircleRenderer.ringOuterHalfWidth(100.0, 1.5, dy),
                    FovCircleRenderer.ringOuterHalfWidth(100.0, 1.5, -dy),
                    1.0e-9);
        }
    }

    @Test
    void innerEdgeStaysInsideTheOuterEdgeOnEveryRow() {
        for (int dy = -101; dy <= 101; dy++) {
            assertTrue(FovCircleRenderer.ringInnerHalfWidth(100.0, 1.5, dy)
                    <= FovCircleRenderer.ringOuterHalfWidth(100.0, 1.5, dy) + 1.0e-9);
        }
    }

    @Test
    void viewportFovIgnoresUnusableValues() {
        double original = FovCircleRenderer.viewportFov();
        try {
            FovCircleRenderer.updateViewportFov(85.0);
            assertEquals(85.0, FovCircleRenderer.viewportFov(), 1.0e-9);
            FovCircleRenderer.updateViewportFov(Double.NaN);
            assertEquals(85.0, FovCircleRenderer.viewportFov(), 1.0e-9);
            FovCircleRenderer.updateViewportFov(360.0);
            assertEquals(85.0, FovCircleRenderer.viewportFov(), 1.0e-9);
            FovCircleRenderer.updateViewportFov(0.0);
            assertEquals(85.0, FovCircleRenderer.viewportFov(), 1.0e-9);
        } finally {
            FovCircleRenderer.updateViewportFov(original);
        }
    }
}
