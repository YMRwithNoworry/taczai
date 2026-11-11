package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double CLOSE_AIM_DISTANCE = 6.0;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        AimDecision decision = AimDecision.sample(Config.aimAtHead, Config.headshotRate);
        return getTargetRotation(player, target, decision);
    }

    static float[] getTargetRotation(Player player, LivingEntity target, AimDecision decision) {
        Vec3 spawn = BallisticsSolver.spawnPosition(player);
        BallisticsSolver.Parameters parameters = TaczBallistics.getParameters(player);
        if (shouldUseDirectCloseAim(spawn, target.getBoundingBox(), parameters.speedPerTick())) {
            // At point-blank ranges prioritize the large, visible body area.
            // Bullet travel, gravity and latency compensation are all smaller
            // than the collision/pose uncertainty that dominates this distance.
            Vec3 closeAimPoint = TargetSelector.visibleAimPoint(player, target, false);
            return directionToRotation(player, closeAimPoint.subtract(spawn));
        }

        Vec3 targetVelocity = BallisticsSolver.targetVelocity(target);
        Vec3 baseAimPoint = TargetSelector.visibleAimPoint(player, target, decision.headshot());
        Vec3 aimPoint = baseAimPoint.add(TaczBallistics.getTargetHitboxCenterOffset(target, targetVelocity));
        if (!aimPoint.equals(baseAimPoint) && !TargetSelector.isPointVisible(player, aimPoint)) {
            // If the historical server hitbox would be behind hard cover, keep
            // the visible point rather than turning the shot into a wall hit.
            aimPoint = baseAimPoint;
        }

        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                spawn, aimPoint, targetVelocity, parameters
        );

        Vec3 direction = solution.hasDirection()
                ? solution.direction()
                : aimPoint.subtract(spawn);
        return directionToRotation(player, direction);
    }

    static boolean shouldUseDirectCloseAim(Vec3 spawn, AABB targetBox, double speedPerTick) {
        double directAimRange = Math.min(CLOSE_AIM_DISTANCE, Math.max(0.0, speedPerTick));
        return targetBox.getCenter().distanceTo(spawn) <= directAimRange;
    }

    static float[] directionToRotation(Player player, Vec3 direction) {
        double dx = direction.x;
        double dy = direction.y;
        double dz = direction.z;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        if (horizontalDistance < 1.0e-6) {
            float currentYaw = player == null ? 0.0F : player.getYRot();
            float verticalPitch = dy >= 0.0 ? -90.0F : 90.0F;
            return new float[]{currentYaw, verticalPitch};
        }

        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
        return new float[]{
                Mth.wrapDegrees(targetYaw),
                Mth.clamp(targetPitch, -90.0F, 90.0F)
        };
    }

    static Vec3 targetPoint(AABB box, boolean aimAtHead) {
        if (!aimAtHead) {
            return box.getCenter();
        }

        double height = box.getYsize();
        double epsilon = Math.min(1.0e-4, height * 0.1);
        double y = Math.min(box.maxY - epsilon, box.minY + height * 0.9);
        return new Vec3(
                (box.minX + box.maxX) * 0.5,
                y,
                (box.minZ + box.maxZ) * 0.5
        );
    }

    public static void applySnapRotation(Player player, float targetYaw, float targetPitch) {
        player.setYRot(Mth.wrapDegrees(targetYaw));
        player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
    }
}
