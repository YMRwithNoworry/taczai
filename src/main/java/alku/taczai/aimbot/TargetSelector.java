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
        double rayLength = eyePos.distanceTo(targetBox.getCenter()) + targetBox.getSize() + 1.0;

        if (targetBox.contains(eyePos)
                || targetBox.clip(eyePos, eyePos.add(direction.scale(rayLength))).isPresent()) {
            return 0.0;
        }

        Vec3 toTarget = targetBox.getCenter().subtract(eyePos).normalize();
        return 1.0 - direction.dot(toTarget);
    }

    static double aimScore(Player shooter, Vec3 eyePos, Vec3 lookVec, LivingEntity target) {
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

    static boolean isBoxWithinFov(Vec3 eyePos, Vec3 lookDirection, AABB targetBox, double maxDegrees) {
        if (targetBox.contains(eyePos)) return true;
        if (lookDirection.lengthSqr() < 1.0e-12) return false;

        Vec3 direction = lookDirection.normalize();
        double rayLength = eyePos.distanceTo(targetBox.getCenter()) + targetBox.getSize() + 1.0;
        if (targetBox.clip(eyePos, eyePos.add(direction.scale(rayLength))).isPresent()) {
            return true;
        }

        List<Vec3> points = TaczHitboxCompat.sampleAabb(targetBox);
        return points.stream().anyMatch(point ->
                isWithinFov(lookDirection, point.subtract(eyePos), maxDegrees));
    }

    static boolean isTargetWithinFov(
            Player shooter,
            Vec3 eyePos,
            Vec3 lookDirection,
            LivingEntity target,
            double maxDegrees
    ) {
        return TaczHitboxCompat.getAimRegions(target, shooter).allPoints().stream()
                .anyMatch(point -> isWithinFov(lookDirection, point.subtract(eyePos), maxDegrees));
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

            AABB entityBox = TaczHitboxCompat.getHitboxBox(living, player);
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
        if (player == null || target == null || player.level() == null || !target.isAlive()) return false;
        double range = Math.max(0.0, Config.aimbotRange);
        if (target.distanceToSqr(player) > range * range) return false;
        if (target instanceof Player candidate && TeammateManager.isEffectiveTeammate(player, candidate)) return false;
        return hasLineOfSight(player, target);
    }

    public static boolean hasLineOfSight(Player player, LivingEntity target) {
        for (Vec3 point : visibilityPoints(target, player)) {
            if (isPointVisible(player, point)) return true;
        }
        return false;
    }

    static Vec3 visibleAimPoint(Player player, LivingEntity target, boolean preferHead) {
        return visibleAimPoint(player, target, preferHead, 1.0F);
    }

    static Vec3 visibleAimPoint(Player player, LivingEntity target, boolean preferHead, float partialTick) {
        Vec3 targetVelocity = BallisticsSolver.targetVelocity(target);
        TaczHitboxCompat.AimRegions regions = TaczHitboxCompat.getAimRegions(target, player);
        List<Vec3> preferred = regions.preferredPoints(preferHead).stream()
                .map(point -> interpolateAimPoint(point, targetVelocity, partialTick))
                .toList();
        for (Vec3 point : preferred) {
            if (isPointVisible(player, point, partialTick)) return point;
        }

        List<Vec3> fallback = regions.fallbackPoints(preferHead).stream()
                .map(point -> interpolateAimPoint(point, targetVelocity, partialTick))
                .toList();
        for (Vec3 point : fallback) {
            if (isPointVisible(player, point, partialTick)) return point;
        }
        if (!preferred.isEmpty()) return preferred.get(0);
        if (!fallback.isEmpty()) return fallback.get(0);
        return TaczHitboxCompat.getHitboxBox(target, player).getCenter();
    }

    static Vec3 interpolateAimPoint(Vec3 currentPoint, Vec3 targetVelocity, float partialTick) {
        double partial = Math.max(0.0, Math.min(1.0, partialTick));
        return currentPoint.add(targetVelocity.scale(partial - 1.0));
    }

    static boolean isPointVisible(Player player, Vec3 to) {
        return isPointVisible(player, to, 1.0F);
    }

    static boolean isPointVisible(Player player, Vec3 to, float partialTick) {
        Vec3 from = player.getEyePosition(partialTick);
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        HitResult result = player.level().clip(context);
        if (result.getType() != HitResult.Type.BLOCK) return true;

        double blockDist = result.getLocation().distanceToSqr(from);
        double targetDist = to.distanceToSqr(from);
        return blockDist >= targetDist - 1.0;
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
        return TaczHitboxCompat.sampleAabb(TaczHitboxCompat.getHitboxBox(target, shooter));
    }

    public static void resetIfTarget(LivingEntity target) {
        if (confirmedTarget == target) confirmedTarget = null;
    }
}
