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
                        && isBoxWithinFov(eyePos, lookVec, e.getBoundingBox(), Config.aimbotFov)
                        && hasLineOfSight(player, e)
        );

        if (entities.isEmpty()) return null;

        LivingEntity best = entities.stream()
                .min(Comparator.comparingDouble(e -> aimScore(eyePos, lookVec, e.getBoundingBox())))
                .orElse(null);

        return best;
    }

    static double aimScore(Vec3 eyePos, Vec3 lookVec, AABB targetBox) {
        Vec3 direction = lookVec.normalize();
        Vec3 targetCenter = targetBox.getCenter();
        double rayLength = eyePos.distanceTo(targetCenter) + targetBox.getSize() + 1.0;

        if (targetBox.contains(eyePos)
                || targetBox.clip(eyePos, eyePos.add(direction.scale(rayLength))).isPresent()) {
            return 0.0;
        }

        Vec3 toTarget = targetCenter.subtract(eyePos).normalize();
        return 1.0 - direction.dot(toTarget);
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

        return visibilityPoints(targetBox).stream()
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

            AABB entityBox = living.getBoundingBox().inflate(0.3);
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
        for (Vec3 to : visibilityPoints(target.getBoundingBox())) {
            if (isPointVisible(player, to)) return true;
        }
        return false;
    }

    static Vec3 visibleAimPoint(Player player, LivingEntity target, boolean preferHead) {
        List<Vec3> points = visibilityPoints(target.getBoundingBox());
        int[] order = preferHead ? new int[]{0, 1, 2} : new int[]{1, 0, 2};
        for (int index : order) {
            Vec3 point = points.get(index);
            if (isPointVisible(player, point)) return point;
        }
        return preferHead ? points.get(0) : points.get(1);
    }

    private static boolean isPointVisible(Player player, Vec3 to) {
        Vec3 from = player.getEyePosition();
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

    static List<Vec3> visibilityPoints(AABB box) {
        double height = box.getYsize();
        double epsilon = Math.min(1.0e-4, height * 0.1);
        double lowerY = Math.max(box.minY + epsilon, box.minY + height * 0.2);
        double headY = Math.min(box.maxY - epsilon, box.minY + height * 0.9);
        double centerX = (box.minX + box.maxX) * 0.5;
        double centerZ = (box.minZ + box.maxZ) * 0.5;

        return List.of(
                new Vec3(centerX, headY, centerZ),
                box.getCenter(),
                new Vec3(centerX, lowerY, centerZ)
        );
    }

    public static void resetIfTarget(LivingEntity target) {
        if (confirmedTarget == target) confirmedTarget = null;
    }
}
