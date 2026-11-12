package alku.taczai.aimbot;

import com.tacz.guns.config.util.HeadShotAABBConfigRead;
import com.tacz.guns.util.HitboxHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Resolves the same collision and headshot regions TACZ uses for projectiles. */
public final class TaczHitboxCompat {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private TaczHitboxCompat() {
    }

    public static int[][] boxEdges() {
        return Arrays.stream(BOX_EDGES).map(int[]::clone).toArray(int[][]::new);
    }

    public static List<List<Vec3>> getHitboxCorners(Entity target, Entity shooter) {
        if (target == null) {
            return List.of();
        }

        if (target instanceof Player player) {
            List<List<Vec3>> peekRegions = PeekTargetCompat.activeHitboxCorners(player);
            if (!peekRegions.isEmpty()) {
                return peekRegions;
            }
        }

        return List.of(PeekTargetCompat.boxCorners(getHitboxBox(target, shooter)));
    }

    static AimRegions getAimRegions(LivingEntity target, Entity shooter) {
        if (target == null) {
            return AimRegions.EMPTY;
        }

        if (target instanceof Player player) {
            AimRegions peekRegions = PeekTargetCompat.activeAimRegions(player);
            if (peekRegions != null) {
                return peekRegions;
            }
        }

        AABB hitbox = getHitboxBox(target, shooter);
        AABB headshotBox = getConfiguredHeadshotBox(target);
        return sampleAimRegions(hitbox, headshotBox, target.position(), target.getEyeY() - target.getY());
    }

    static AABB getHitboxBox(Entity target, Entity shooter) {
        if (target == null) {
            return new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }

        try {
            AABB hitbox = HitboxHelper.getFixedBoundingBox(target, shooter);
            if (hitbox != null && isValidBox(hitbox)) {
                return hitbox;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall back if TACZ's hitbox helper is unavailable during startup.
        }
        return target.getBoundingBox();
    }

    static AimRegions sampleAimRegions(AABB hitbox, AABB localHeadshotBox, Vec3 targetPosition, double eyeHeight) {
        if (hitbox == null || targetPosition == null || !isValidBox(hitbox)) {
            return AimRegions.EMPTY;
        }

        AABB worldHeadshotBox = localHeadshotBox == null
                ? new AABB(
                        hitbox.minX,
                        targetPosition.y + eyeHeight - 0.25,
                        hitbox.minZ,
                        hitbox.maxX,
                        targetPosition.y + eyeHeight + 0.25,
                        hitbox.maxZ
                )
                : localHeadshotBox.move(targetPosition);
        List<Vec3> headPoints = sampleAabb(intersect(hitbox, worldHeadshotBox));
        List<Vec3> bodyPoints = sampleAabb(hitbox).stream()
                .filter(point -> !isHeadshotPoint(point, targetPosition, localHeadshotBox, eyeHeight))
                .toList();
        return new AimRegions(headPoints, bodyPoints);
    }

    static boolean isHeadshotPoint(Vec3 worldPoint, Vec3 targetPosition, AABB localHeadshotBox, double eyeHeight) {
        Vec3 localPoint = worldPoint.subtract(targetPosition);
        if (localHeadshotBox != null) {
            return localHeadshotBox.contains(localPoint);
        }
        return localPoint.y > eyeHeight - 0.25 && localPoint.y < eyeHeight + 0.25;
    }

    static List<Vec3> sampleAabb(AABB box) {
        if (box == null || !isValidBox(box)) {
            return List.of();
        }

        double[][] fractions = {
                {0.50, 0.50, 0.50},
                {0.50, 0.30, 0.50},
                {0.50, 0.70, 0.50},
                {0.50, 0.10, 0.50},
                {0.50, 0.90, 0.50},
                {0.25, 0.50, 0.50},
                {0.75, 0.50, 0.50},
                {0.50, 0.50, 0.25},
                {0.50, 0.50, 0.75},
                {0.25, 0.30, 0.25},
                {0.75, 0.30, 0.75},
                {0.25, 0.70, 0.75},
                {0.75, 0.70, 0.25}
        };
        List<Vec3> points = new ArrayList<>(fractions.length);
        for (double[] fraction : fractions) {
            points.add(new Vec3(
                    box.minX + fraction[0] * box.getXsize(),
                    box.minY + fraction[1] * box.getYsize(),
                    box.minZ + fraction[2] * box.getZsize()
            ));
        }
        return List.copyOf(points);
    }

    private static AABB getConfiguredHeadshotBox(Entity target) {
        try {
            var entityTypeId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
            return entityTypeId == null ? null : HeadShotAABBConfigRead.getAABB(entityTypeId);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static AABB intersect(AABB first, AABB second) {
        double minX = Math.max(first.minX, second.minX);
        double minY = Math.max(first.minY, second.minY);
        double minZ = Math.max(first.minZ, second.minZ);
        double maxX = Math.min(first.maxX, second.maxX);
        double maxY = Math.min(first.maxY, second.maxY);
        double maxZ = Math.min(first.maxZ, second.maxZ);
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isValidBox(AABB box) {
        return box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ;
    }

    record AimRegions(List<Vec3> headPoints, List<Vec3> bodyPoints) {
        static final AimRegions EMPTY = new AimRegions(List.of(), List.of());

        AimRegions {
            headPoints = List.copyOf(headPoints);
            bodyPoints = List.copyOf(bodyPoints);
        }

        List<Vec3> preferredPoints(boolean preferHead) {
            List<Vec3> preferred = preferHead ? headPoints : bodyPoints;
            if (!preferred.isEmpty()) {
                return preferred;
            }
            return preferHead ? bodyPoints : headPoints;
        }

        List<Vec3> fallbackPoints(boolean preferHead) {
            return preferHead ? bodyPoints : headPoints;
        }

        List<Vec3> allPoints() {
            List<Vec3> points = new ArrayList<>(headPoints.size() + bodyPoints.size());
            points.addAll(headPoints);
            points.addAll(bodyPoints);
            return List.copyOf(points);
        }
    }
}
