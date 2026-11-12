package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaczHitboxCompatTest {
    @Test
    void defaultHeadshotBandUsesPoseSpecificEyeHeightAndBodyPointsExcludeIt() {
        AABB collisionBox = new AABB(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        TaczHitboxCompat.AimRegions regions = TaczHitboxCompat.sampleAimRegions(
                collisionBox, null, targetPosition, 1.62
        );

        assertFalse(regions.headPoints().isEmpty());
        assertFalse(regions.bodyPoints().isEmpty());
        assertTrue(regions.headPoints().stream().allMatch(point ->
                TaczHitboxCompat.isHeadshotPoint(point, targetPosition, null, 1.62)));
        assertTrue(regions.bodyPoints().stream().noneMatch(point ->
                TaczHitboxCompat.isHeadshotPoint(point, targetPosition, null, 1.62)));

        TaczHitboxCompat.AimRegions proneRegions = TaczHitboxCompat.sampleAimRegions(
                new AABB(-0.3, 0.0, 2.0, 0.3, 0.6, 2.6), null, targetPosition, 0.42
        );
        assertTrue(proneRegions.headPoints().stream().allMatch(point -> point.y < 0.7));
    }

    @Test
    void configuredHeadshotBoxOverridesEyeHeightHeuristic() {
        AABB collisionBox = new AABB(-0.3, 0.0, 2.0, 0.3, 1.8, 2.6);
        AABB configuredHead = new AABB(-0.2, 0.7, -0.2, 0.2, 0.95, 0.2);
        Vec3 targetPosition = new Vec3(0.0, 0.0, 2.3);
        TaczHitboxCompat.AimRegions regions = TaczHitboxCompat.sampleAimRegions(
                collisionBox, configuredHead, targetPosition, 1.62
        );

        assertFalse(regions.headPoints().isEmpty());
        assertTrue(regions.headPoints().stream().allMatch(point ->
                TaczHitboxCompat.isHeadshotPoint(point, targetPosition, configuredHead, 1.62)));
        assertTrue(regions.bodyPoints().stream().noneMatch(point ->
                TaczHitboxCompat.isHeadshotPoint(point, targetPosition, configuredHead, 1.62)));
        assertTrue(regions.headPoints().stream().allMatch(point -> point.y < 1.0));
    }

    @Test
    void shotPreferenceUsesTheRequestedTaczRegionAndFallsBackOnlyWhenEmpty() {
        Vec3 head = new Vec3(0.0, 1.6, 2.0);
        Vec3 body = new Vec3(0.0, 0.8, 2.0);
        TaczHitboxCompat.AimRegions both = new TaczHitboxCompat.AimRegions(
                java.util.List.of(head), java.util.List.of(body)
        );
        TaczHitboxCompat.AimRegions bodyOnly = new TaczHitboxCompat.AimRegions(
                java.util.List.of(), java.util.List.of(body)
        );

        assertTrue(both.preferredPoints(true).contains(head));
        assertTrue(both.preferredPoints(false).contains(body));
        assertTrue(bodyOnly.preferredPoints(true).contains(body));
    }
}
