package alku.taczai.aimbot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

final class PeekTargetCompat {
    private static final float CURRENT_STATE_PARTIAL_TICK = 1.0F;
    private static final double SAMPLE_INSET = 0.8;

    private static Api api;
    private static boolean apiResolved;

    private PeekTargetCompat() {
    }

    static List<Vec3> headAimPoints(Player player) {
        PeekHitboxes hitboxes = getActiveHitboxes(player);
        if (hitboxes == null) {
            return List.of();
        }
        return constrainAimPointsToPose(hitboxes.aimPoints(), player.getBoundingBox());
    }

    static List<List<Vec3>> activeHitboxCorners(Player player) {
        PeekHitboxes hitboxes = getActiveHitboxes(player);
        if (hitboxes == null) {
            return List.of();
        }
        return List.of(hitboxes.lowerBodyCorners(), hitboxes.upperBodyCorners());
    }

    private static PeekHitboxes getActiveHitboxes(Player player) {
        if (player == null) {
            return null;
        }

        Api compat = getApi();
        if (compat == null) {
            return null;
        }

        try {
            if (!Boolean.TRUE.equals(compat.betterPeekEnabled.invoke(null))) {
                return null;
            }

            float angle = ((Number) compat.renderAngle.invoke(null, player, CURRENT_STATE_PARTIAL_TICK)).floatValue();
            float offset = ((Number) compat.renderOffset.invoke(null, player, CURRENT_STATE_PARTIAL_TICK)).floatValue();
            if (!Boolean.TRUE.equals(compat.hasActiveHitboxes.invoke(null, angle, offset))) {
                return null;
            }

            Object hitboxes = compat.buildHitboxes.invoke(null, player, angle, offset);
            AABB lowerBody = (AABB) compat.lowerBody.invoke(hitboxes);
            Object upperBody = compat.upperBody.invoke(hitboxes);
            Vec3 center = (Vec3) compat.center.invoke(upperBody);
            Vec3 axisRight = (Vec3) compat.axisRight.invoke(upperBody);
            double halfRight = ((Number) compat.halfRight.invoke(upperBody)).doubleValue();
            Vec3 axisUp = (Vec3) compat.axisUp.invoke(upperBody);
            double halfUp = ((Number) compat.halfUp.invoke(upperBody)).doubleValue();
            Vec3 axisForward = (Vec3) compat.axisForward.invoke(upperBody);
            double halfForward = ((Number) compat.halfForward.invoke(upperBody)).doubleValue();
            return new PeekHitboxes(
                    boxCorners(lowerBody),
                    orientedBoxCorners(center, axisRight, halfRight, axisUp, halfUp, axisForward, halfForward),
                    sampleOrientedBox(center, axisRight, halfRight, axisUp, halfUp, axisForward, halfForward)
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    static List<Vec3> boxCorners(AABB box) {
        return List.of(
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ)
        );
    }

    static List<Vec3> orientedBoxCorners(
            Vec3 center,
            Vec3 axisRight,
            double halfRight,
            Vec3 axisUp,
            double halfUp,
            Vec3 axisForward,
            double halfForward
    ) {
        if (center == null || axisRight == null || axisUp == null || axisForward == null
                || !validHalfExtent(halfRight) || !validHalfExtent(halfUp) || !validHalfExtent(halfForward)) {
            return List.of();
        }

        Vec3 right = axisRight.normalize().scale(halfRight);
        Vec3 up = axisUp.normalize().scale(halfUp);
        Vec3 forward = axisForward.normalize().scale(halfForward);
        if (right.lengthSqr() < 1.0e-8 || up.lengthSqr() < 1.0e-8 || forward.lengthSqr() < 1.0e-8) {
            return List.of();
        }

        return List.of(
                center.subtract(right).subtract(up).subtract(forward),
                center.add(right).subtract(up).subtract(forward),
                center.add(right).add(up).subtract(forward),
                center.subtract(right).add(up).subtract(forward),
                center.subtract(right).subtract(up).add(forward),
                center.add(right).subtract(up).add(forward),
                center.add(right).add(up).add(forward),
                center.subtract(right).add(up).add(forward)
        );
    }

    private record PeekHitboxes(List<Vec3> lowerBodyCorners, List<Vec3> upperBodyCorners, List<Vec3> aimPoints) {
    }

    static List<Vec3> constrainAimPointsToPose(List<Vec3> points, AABB poseBox) {
        if (points.isEmpty() || poseBox == null || poseBox.getYsize() <= 0.0) {
            return List.of();
        }

        double inset = Math.min(1.0e-4, poseBox.getYsize() * 0.1);
        double minY = poseBox.minY + inset;
        double maxY = poseBox.maxY - inset;
        return points.stream()
                .map(point -> new Vec3(point.x, Math.max(minY, Math.min(maxY, point.y)), point.z))
                .toList();
    }

    static List<Vec3> sampleOrientedBox(
            Vec3 center,
            Vec3 axisRight,
            double halfRight,
            Vec3 axisUp,
            double halfUp,
            Vec3 axisForward,
            double halfForward
    ) {
        if (center == null || axisRight == null || axisUp == null || axisForward == null
                || !validHalfExtent(halfRight) || !validHalfExtent(halfUp) || !validHalfExtent(halfForward)) {
            return List.of();
        }

        Vec3 right = axisRight.normalize();
        Vec3 up = axisUp.normalize();
        Vec3 forward = axisForward.normalize();
        if (right.lengthSqr() < 1.0e-8 || up.lengthSqr() < 1.0e-8 || forward.lengthSqr() < 1.0e-8) {
            return List.of();
        }

        double rightOffset = halfRight * SAMPLE_INSET;
        double upOffset = halfUp * SAMPLE_INSET;
        double forwardOffset = halfForward * SAMPLE_INSET;
        List<Vec3> points = new ArrayList<>(15);
        points.add(center);
        points.add(center.add(up.scale(upOffset)));
        points.add(center.add(up.scale(-upOffset)));
        points.add(center.add(right.scale(rightOffset)));
        points.add(center.add(right.scale(-rightOffset)));
        points.add(center.add(forward.scale(forwardOffset)));
        points.add(center.add(forward.scale(-forwardOffset)));

        for (int rightSign : new int[]{-1, 1}) {
            for (int upSign : new int[]{-1, 1}) {
                for (int forwardSign : new int[]{-1, 1}) {
                    points.add(center
                            .add(right.scale(rightOffset * rightSign))
                            .add(up.scale(upOffset * upSign))
                            .add(forward.scale(forwardOffset * forwardSign)));
                }
            }
        }
        return List.copyOf(points);
    }

    private static boolean validHalfExtent(double value) {
        return Double.isFinite(value) && value > 0.0;
    }

    private static Api getApi() {
        if (apiResolved) {
            return api;
        }

        apiResolved = true;
        try {
            ClassLoader loader = PeekTargetCompat.class.getClassLoader();
            Class<?> peekHandler = Class.forName("org.mods.gd656peek.client.PeekHandler", false, loader);
            Class<?> geometry = Class.forName("org.mods.gd656peek.PeekHitboxGeometry", false, loader);
            Class<?> hitboxSet = Class.forName("org.mods.gd656peek.PeekHitboxGeometry$HitboxSet", false, loader);
            Class<?> orientedBox = Class.forName("org.mods.gd656peek.PeekHitboxGeometry$OrientedBox", false, loader);

            api = new Api(
                    peekHandler.getMethod("isBetterPeekModelEnabled"),
                    peekHandler.getMethod("getRenderAngleForPlayer", Player.class, float.class),
                    peekHandler.getMethod("getRenderOffsetForPlayer", Player.class, float.class),
                    geometry.getMethod("hasActiveHitboxes", float.class, float.class),
                    geometry.getMethod("build", Player.class, float.class, float.class),
                    hitboxSet.getMethod("lowerBody"),
                    hitboxSet.getMethod("upperBody"),
                    orientedBox.getMethod("center"),
                    orientedBox.getMethod("axisRight"),
                    orientedBox.getMethod("halfRight"),
                    orientedBox.getMethod("axisUp"),
                    orientedBox.getMethod("halfUp"),
                    orientedBox.getMethod("axisForward"),
                    orientedBox.getMethod("halfForward")
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            api = null;
        }
        return api;
    }

    private record Api(
            Method betterPeekEnabled,
            Method renderAngle,
            Method renderOffset,
            Method hasActiveHitboxes,
            Method buildHitboxes,
            Method lowerBody,
            Method upperBody,
            Method center,
            Method axisRight,
            Method halfRight,
            Method axisUp,
            Method halfUp,
            Method axisForward,
            Method halfForward
    ) {
    }
}
