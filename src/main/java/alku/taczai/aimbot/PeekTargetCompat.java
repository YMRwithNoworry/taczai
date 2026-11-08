package alku.taczai.aimbot;

import net.minecraft.world.entity.player.Player;
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
        if (player == null) {
            return List.of();
        }

        Api compat = getApi();
        if (compat == null) {
            return List.of();
        }

        try {
            if (!Boolean.TRUE.equals(compat.betterPeekEnabled.invoke(null))) {
                return List.of();
            }

            float angle = ((Number) compat.renderAngle.invoke(null, player, CURRENT_STATE_PARTIAL_TICK)).floatValue();
            float offset = ((Number) compat.renderOffset.invoke(null, player, CURRENT_STATE_PARTIAL_TICK)).floatValue();
            if (!Boolean.TRUE.equals(compat.hasActiveHitboxes.invoke(null, angle, offset))) {
                return List.of();
            }

            Object hitboxes = compat.buildHitboxes.invoke(null, player, angle, offset);
            Object upperBody = compat.upperBody.invoke(hitboxes);
            return sampleOrientedBox(
                    (Vec3) compat.center.invoke(upperBody),
                    (Vec3) compat.axisRight.invoke(upperBody),
                    ((Number) compat.halfRight.invoke(upperBody)).doubleValue(),
                    (Vec3) compat.axisUp.invoke(upperBody),
                    ((Number) compat.halfUp.invoke(upperBody)).doubleValue(),
                    (Vec3) compat.axisForward.invoke(upperBody),
                    ((Number) compat.halfForward.invoke(upperBody)).doubleValue()
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
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
