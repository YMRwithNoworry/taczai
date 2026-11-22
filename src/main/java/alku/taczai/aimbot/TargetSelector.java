package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.teammate.TeammateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class TargetSelector {
    private static LivingEntity confirmedTarget = null;

    public static LivingEntity getConfirmedTarget() {
        return confirmedTarget;
    }

    public static LivingEntity findAutoTarget(Player player) {
        if (player == null || player.level() == null) return null;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double range = Config.aimbotRange;

        AABB searchBox = player.getBoundingBox().inflate(range);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player
                        && e.isAlive()
                        && e.distanceToSqr(player) <= range * range
                        && !(e instanceof Player candidate && TeammateManager.isEffectiveTeammate(player, candidate))
                        && isTargetWithinFov(player, eyePos, lookVec, e, Config.aimbotFov)
                        && hasLineOfSight(player, e)
        );

        if (entities.isEmpty()) return null;

        return entities.stream()
                .min(Comparator
                        .comparingDouble((LivingEntity e) -> aimScore(player, eyePos, lookVec, e))
                        .thenComparingDouble(e -> e.distanceToSqr(player)))
                .orElse(null);
    }

    static double aimScore(Vec3 eyePos, Vec3 lookVec, AABB targetBox) {
        Vec3 direction = lookVec.normalize();
        if (crosshairIntersects(eyePos, direction, targetBox)) {
            return 0.0;
        }

        Vec3 toTarget = targetBox.getCenter().subtract(eyePos).normalize();
        return 1.0 - direction.dot(toTarget);
    }

    static double aimScore(Player shooter, Vec3 eyePos, Vec3 lookVec, LivingEntity target) {
        if (crosshairIntersects(eyePos, lookVec, TaczHitboxCompat.getCurrentAimHitboxBox(target))) {
            return 0.0;
        }
        return aimScore(eyePos, lookVec, TaczHitboxCompat.getAimRegions(target, shooter).allPoints());
    }

    static double aimScore(Vec3 eyePos, Vec3 lookVec, List<Vec3> targetPoints) {
        if (targetPoints.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (lookVec.lengthSqr() < 1.0e-12) {
            return Double.POSITIVE_INFINITY;
        }

        Vec3 direction = lookVec.normalize();
        return targetPoints.stream()
                .mapToDouble(point -> {
                    Vec3 toPoint = point.subtract(eyePos);
                    if (toPoint.lengthSqr() < 1.0e-12) return 0.0;
                    return 1.0 - direction.dot(toPoint.normalize());
                })
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    static boolean isWithinFov(Vec3 lookDirection, Vec3 targetDirection, double maxDegrees) {
        if (targetDirection.lengthSqr() < 1.0e-12) return true;
        if (lookDirection.lengthSqr() < 1.0e-12) return false;

        double dot = lookDirection.normalize().dot(targetDirection.normalize());
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, dot))));
        return angle <= maxDegrees + 1.0e-7;
    }

    private static boolean crosshairIntersects(Vec3 eyePos, Vec3 lookDirection, AABB targetBox) {
        if (targetBox.contains(eyePos)) return true;
        if (lookDirection.lengthSqr() < 1.0e-12) return false;

        Vec3 direction = lookDirection.normalize();
        double rayLength = eyePos.distanceTo(targetBox.getCenter()) + targetBox.getSize() + 1.0;
        return targetBox.clip(eyePos, eyePos.add(direction.scale(rayLength))).isPresent();
    }

    static boolean isTargetWithinFov(
            Vec3 eyePos,
            Vec3 lookDirection,
            AABB targetBox,
            List<Vec3> aimPoints,
            double maxDegrees
    ) {
        return crosshairIntersects(eyePos, lookDirection, targetBox)
                || aimPoints.stream().anyMatch(point ->
                        isWithinFov(lookDirection, point.subtract(eyePos), maxDegrees));
    }

    private static boolean isTargetWithinFov(
            Player shooter,
            Vec3 eyePos,
            Vec3 lookDirection,
            LivingEntity target,
            double maxDegrees
    ) {
        return isTargetWithinFov(eyePos, lookDirection, TaczHitboxCompat.getCurrentAimHitboxBox(target),
                TaczHitboxCompat.getAimRegions(target, shooter).allPoints(), maxDegrees);
    }

    private static LivingEntity raytraceEntity(Player player, boolean excludeTeammates) {
        double range = Config.aimbotRange;
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        ClipContext blockContext = new ClipContext(eyePos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        HitResult blockHit = player.level().clip(blockContext);

        double maxDist = range;
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxDist = Math.min(maxDist, blockHit.getLocation().distanceTo(eyePos));
        }

        Vec3 finalEndPos = eyePos.add(lookVec.scale(maxDist));
        AABB searchBox = new AABB(eyePos, finalEndPos).inflate(1.0);

        List<Entity> entities = player.level().getEntities(player, searchBox, e -> e instanceof LivingEntity);

        LivingEntity bestTarget = null;
        double bestDist = Double.MAX_VALUE;

        for (Entity entity : entities) {
            LivingEntity living = (LivingEntity) entity;
            if (!living.isAlive()) continue;
            if (living == player) continue;
            if (excludeTeammates && living instanceof Player candidate && TeammateManager.isEffectiveTeammate(player, candidate)) continue;

            AABB entityBox = TaczHitboxCompat.getCurrentAimHitboxBox(living);
            Optional<Vec3> hitOpt = entityBox.clip(eyePos, finalEndPos);
            if (hitOpt.isEmpty()) continue;

            double dist = hitOpt.get().distanceTo(eyePos);
            if (dist < bestDist) {
                bestDist = dist;
                bestTarget = living;
            }
        }

        return bestTarget;
    }

    public static LivingEntity getActiveTarget(Player player) {
        confirmedTarget = findAutoTarget(player);
        return confirmedTarget;
    }

    /** Keep the current target locked while it remains visible and valid. */
    public static boolean isTrackableTarget(Player player, LivingEntity target) {
        if (player == null || target == null || player.level() != target.level() || !target.isAlive()) return false;
        double range = Math.max(0.0, Config.aimbotRange);
        if (target.distanceToSqr(player) > range * range) return false;
        if (target instanceof Player candidate && TeammateManager.isEffectiveTeammate(player, candidate)) return false;
        return hasLineOfSight(player, target);
    }

    public static boolean hasLineOfSight(Player player, LivingEntity target) {
        Vec3 from = RotationHelper.shotOrigin(player, 1.0F);
        for (Vec3 point : visibilityPoints(target, player)) {
            if (isPointVisible(player, from, point)) return true;
        }
        return false;
    }

    static Vec3 visibleAimPoint(
            Player player, LivingEntity target, Vec3 from, boolean preferHead, float partialTick
    ) {
        return firstVisibleAimPoint(
                TaczHitboxCompat.getAimRegions(target, player),
                preferHead,
                TaczHitboxCompat.renderPositionOffset(target, partialTick),
                point -> isPointVisible(player, from, point)
        );
    }

    static Vec3 firstVisibleAimPoint(
            TaczHitboxCompat.AimRegions regions, boolean preferHead, Vec3 renderOffset, Predicate<Vec3> visible
    ) {
        List<Vec3> preferred = preferHead ? regions.headPoints() : regions.bodyPoints();
        List<Vec3> fallback = preferHead ? regions.bodyPoints() : regions.headPoints();
        Vec3 point = firstVisiblePoint(preferred, renderOffset, visible);
        return point != null ? point : firstVisiblePoint(fallback, renderOffset, visible);
    }

    private static Vec3 firstVisiblePoint(List<Vec3> points, Vec3 renderOffset, Predicate<Vec3> visible) {
        for (Vec3 point : points) {
            Vec3 renderedPoint = point.add(renderOffset);
            if (visible.test(renderedPoint)) return renderedPoint;
        }
        return null;
    }

    private static boolean isPointVisible(Player player, Vec3 from, Vec3 to) {
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        return player.level().clip(context).getType() == HitResult.Type.MISS;
    }

    public static Player raytracePlayer(Player player) {
        LivingEntity target = raytraceEntity(player, false);
        return target instanceof Player candidate ? candidate : null;
    }

    static List<Vec3> visibilityPoints(LivingEntity target, Entity shooter) {
        TaczHitboxCompat.AimRegions regions = TaczHitboxCompat.getAimRegions(target, shooter);
        if (!regions.allPoints().isEmpty()) {
            return regions.allPoints();
        }
        return TaczHitboxCompat.sampleAabb(TaczHitboxCompat.getCurrentAimHitboxBox(target));
    }

    public static void resetIfTarget(LivingEntity target) {
        if (confirmedTarget == target) confirmedTarget = null;
    }
}
