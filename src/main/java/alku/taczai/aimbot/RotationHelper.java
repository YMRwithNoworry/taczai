package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double AIM_STRENGTH_MULTIPLIER = 2.0;
    private static final float MIN_SMOOTH_CORRECTION = 0.05F;
    private static final float SNAP_ANGLE_THRESHOLD = 0.1F;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        AimDecision decision = AimDecision.sample(Config.aimAtHead, Config.headshotRate);
        return getTargetRotation(player, target, decision);
    }

    static float[] getTargetRotation(Player player, LivingEntity target, AimDecision decision) {
        Vec3 spawn = BallisticsSolver.spawnPosition(player);
        Vec3 shooterVelocity = BallisticsSolver.shooterVelocity(player);
        Vec3 targetVelocity = BallisticsSolver.targetVelocity(target);
        Vec3 hitboxOffset = TaczBallistics.getTargetHitboxCenterOffset(target, targetVelocity);
        Vec3 baseAimPoint = TargetSelector.visibleAimPoint(player, target, decision.headshot());
        Vec3 shiftedAimPoint = baseAimPoint.add(hitboxOffset);
        Vec3 aimPoint = shiftedAimPoint;
        if (hitboxOffset.lengthSqr() > 1.0e-8
                && !TargetSelector.isPointVisible(player, shiftedAimPoint)) {
            // Moving behind hard cover: the latency-compensated hitbox is not
            // reachable, so keep the visible point instead of shooting a wall.
            aimPoint = baseAimPoint;
        }

        BallisticsSolver.Parameters parameters = TaczBallistics.getParameters(player);
        BallisticsSolver.AimSolution solution = BallisticsSolver.solve(
                spawn, shooterVelocity, aimPoint, targetVelocity, parameters
        );

        Vec3 direction = solution.hasDirection()
                ? solution.direction()
                : aimPoint.subtract(spawn);
        return directionToRotation(player, direction);
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

    public static float smoothAngle(float current, float target, float speed) {
        float delta = Mth.degreesDifference(current, target);
        if (Math.abs(delta) < SNAP_ANGLE_THRESHOLD) {
            return target;
        }

        float correction = (float) Math.min(1.0, Math.max(0.0, (1.0 - speed) * AIM_STRENGTH_MULTIPLIER));
        // Even at the slowest configured speed the aim must keep approaching the
        // target; otherwise a 1.0 speed value would freeze the crosshair.
        correction = Math.max(correction, MIN_SMOOTH_CORRECTION);
        return current + delta * correction;
    }

    public static void applySmoothRotation(Player player, float targetYaw, float targetPitch) {
        float speed = (float) Mth.clamp(Config.aimSpeed, 0.0, 1.0);

        float newYaw = smoothAngle(player.getYRot(), targetYaw, speed);
        float newPitch = smoothAngle(player.getXRot(), targetPitch, speed);

        player.setYRot(Mth.wrapDegrees(newYaw));
        player.setXRot(Mth.clamp(newPitch, -90.0F, 90.0F));
    }

    public static void applySnapRotation(Player player, float targetYaw, float targetPitch) {
        player.setYRot(Mth.wrapDegrees(targetYaw));
        player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
    }

    static boolean isAligned(Player player, float[] targetRotation, float toleranceDegrees) {
        if (player == null || targetRotation == null || targetRotation.length < 2) return false;
        float yawError = Math.abs(Mth.degreesDifference(player.getYRot(), targetRotation[0]));
        float pitchError = Math.abs(player.getXRot() - targetRotation[1]);
        return yawError <= toleranceDegrees && pitchError <= toleranceDegrees;
    }
}
