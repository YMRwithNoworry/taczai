package alku.taczai.aimbot;

import alku.taczai.Config;
import alku.taczai.keybind.KeyMappings;
import net.minecraft.client.Minecraft;
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

    public static void confirmTarget(Player player) {
        if (player == null || player.level() == null) return;
        LivingEntity target = raytraceEntity(player);
        if (target == null) return;
        confirmedTarget = target;
    }

    public static LivingEntity findAutoTarget(Player player) {
        if (player == null || player.level() == null) return null;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        double range = Config.aimbotRange;

        AABB searchBox = player.getBoundingBox().inflate(range);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player && e.isAlive() && e.distanceToSqr(player) <= range * range
        );

        if (entities.isEmpty()) return null;

        LivingEntity best = entities.stream()
                .min(Comparator.comparingDouble(e -> {
                    Vec3 toEntity = e.getEyePosition().subtract(eyePos).normalize();
                    return 1.0 - lookVec.dot(toEntity);
                }))
                .orElse(null);

        if (best != null && hasLineOfSight(player, best)) {
            return best;
        }
        return null;
    }

    private static LivingEntity raytraceEntity(Player player) {
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
        if (KeyMappings.aimMode == KeyMappings.AimMode.AUTO) {
            LivingEntity autoTarget = findAutoTarget(player);
            confirmedTarget = autoTarget;
            return autoTarget;
        }

        if (confirmedTarget == null) return null;
        if (!confirmedTarget.isAlive()) {
            confirmedTarget = null;
            return null;
        }
        if (confirmedTarget.distanceToSqr(player) > Config.aimbotRange * Config.aimbotRange) {
            confirmedTarget = null;
            return null;
        }
        if (!hasLineOfSight(player, confirmedTarget)) {
            return null;
        }
        return confirmedTarget;
    }

    public static boolean hasLineOfSight(Player player, LivingEntity target) {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getEyePosition();
        ClipContext context = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        HitResult result = player.level().clip(context);
        if (result.getType() == HitResult.Type.BLOCK) {
            double blockDist = result.getLocation().distanceToSqr(from);
            double targetDist = to.distanceToSqr(from);
            return blockDist >= targetDist - 1.0;
        }
        return true;
    }

    public static void resetTarget() {
        confirmedTarget = null;
    }
}
