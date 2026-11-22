package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void targetVisibilitySamplesStayInsideTaczCollisionBox() {
        AABB proneTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        var regions = TaczHitboxCompat.sampleAimRegions(proneTarget, null, targetPosition, 0.42);

        assertTrue(regions.allPoints().stream().allMatch(proneTarget::contains));
        assertTrue(regions.headPoints().stream().allMatch(point -> point.y < 0.7));
    }

    @Test
    void headTargetsUseTaczDefaultEyeHeightBand() {
        AABB standingTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        var regions = TaczHitboxCompat.sampleAimRegions(standingTarget, null, targetPosition, 1.62);

        assertTrue(regions.headPoints().stream().allMatch(point -> point.y > 1.37 && point.y < 1.87));
        assertTrue(regions.bodyPoints().stream().allMatch(point -> point.y <= 1.37 || point.y >= 1.87));
    }

    @Test
    void fallbackHeadshotBandFollowsLowProfileEyeHeight() {
        AABB proneTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        var regions = TaczHitboxCompat.sampleAimRegions(proneTarget, null, targetPosition, 0.42);

        assertFalse(regions.headPoints().isEmpty());
        assertTrue(regions.headPoints().stream().allMatch(point -> point.y > 0.17 && point.y < 0.67));
        assertTrue(regions.bodyPoints().stream().noneMatch(point -> point.y > 0.17 && point.y < 0.67));
    }

    @Test
    void visibleAimPrefersTheRegionCenterAtTheRenderedPosition() {
        AABB standingTarget = new AABB(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        var regions = TaczHitboxCompat.sampleAimRegions(standingTarget, null, targetPosition, 1.62);
        Vec3 renderOffset = new Vec3(-0.5, 0.0, 0.25);

        Vec3 head = TargetSelector.firstVisibleAimPoint(regions, true, renderOffset, point -> true);
        assertEquals(-0.5, head.x, 1.0e-9);
        assertEquals(1.585, head.y, 1.0e-9);
        assertEquals(2.55, head.z, 1.0e-9);
        assertEquals(standingTarget.getCenter().add(renderOffset),
                TargetSelector.firstVisibleAimPoint(regions, false, renderOffset, point -> true));
    }

    @Test
    void blockedCenterUsesAnotherVisiblePointBeforeSwitchingRegions() {
        Vec3 head = new Vec3(0.0, 1.6, 2.0);
        Vec3 exposedHead = new Vec3(0.1, 1.6, 2.0);
        Vec3 body = new Vec3(0.0, 0.9, 2.0);
        var regions = new TaczHitboxCompat.AimRegions(List.of(head, exposedHead), List.of(body));

        assertEquals(exposedHead,
                TargetSelector.firstVisibleAimPoint(regions, true, Vec3.ZERO, point -> !point.equals(head)));
        assertEquals(body, TargetSelector.firstVisibleAimPoint(regions, true, Vec3.ZERO, body::equals));
        assertNull(TargetSelector.firstVisibleAimPoint(regions, true, Vec3.ZERO, point -> false));
    }

    @Test
    void fovUsesMaximumAngularOffsetFromCrosshair() {
        Vec3 look = new Vec3(0.0, 0.0, 1.0);
        assertTrue(TargetSelector.isWithinFov(look, directionAtDegrees(0.0), 20.0));
        assertTrue(TargetSelector.isWithinFov(look, directionAtDegrees(20.0), 20.0));
        assertFalse(TargetSelector.isWithinFov(look, directionAtDegrees(20.1), 20.0));
        assertFalse(TargetSelector.isWithinFov(look, directionAtDegrees(180.0), 20.0));
    }

    @Test
    void closeTargetIntersectingCrosshairIsInsideFovEvenWhenAllSamplesAreOutside() {
        Vec3 eyePosition = new Vec3(0.29, 1.62, 0.0);
        Vec3 lookDirection = new Vec3(0.0, 0.0, 1.0);
        AABB closeTarget = new AABB(-0.3, 0.0, 0.1, 0.3, 1.8, 0.7);

        var points = TaczHitboxCompat.sampleAabb(closeTarget);
        assertFalse(points.stream().anyMatch(point ->
                TargetSelector.isWithinFov(lookDirection, point.subtract(eyePosition), 1.0)));
        assertTrue(TargetSelector.isTargetWithinFov(eyePosition, lookDirection, closeTarget, points, 1.0));
    }

    private static Vec3 directionAtDegrees(double degrees) {
        double radians = Math.toRadians(degrees);
        return new Vec3(Math.sin(radians), 0.0, Math.cos(radians));
    }
}
