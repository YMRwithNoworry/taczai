package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double AIM_STRENGTH_MULTIPLIER = 1.35;
    private static final double MISS_CLEARANCE = 0.05;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        AimDecision decision = AimDecision.sample(Config.aimAtHead, Config.headshotRate, Config.missRate);
        return getTargetRotation(player, target, decision);
    }

    static float[] getTargetRotation(Player player, LivingEntity target, AimDecision decision) {
        Vec3 direction = applyAimOffset(
                aimSolution(player, target, decision).direction(),
                player.getEyePosition(),
                target.getBoundingBox(),
                decision
        );
        double dx = direction.x;
        double dy = direction.y;
        double dz = direction.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) {
            return new float[]{player.getYRot(), player.getXRot()};
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(dy, horizontalDist) * 180.0F / Math.PI);

        return new float[]{targetYaw, targetPitch};
    }

    static Vec3 predictTargetPoint(Player player, LivingEntity target) {
        Vec3 targetPoint = TargetSelector.visibleAimPoint(player, target, Config.aimAtHead);
        double flightTicks = aimSolution(player, target, targetPoint).flightTicks();
        return targetPoint.add(target.getDeltaMovement().scale(flightTicks));
    }

    static AABB predictTargetBox(Player player, LivingEntity target) {
        double flightTicks = aimSolution(player, target).flightTicks();
        return moveTargetBox(target.getBoundingBox(), target.getDeltaMovement().scale(flightTicks));
    }

    static AABB moveTargetBox(AABB targetBox, Vec3 predictionOffset) {
        return targetBox.move(predictionOffset);
    }

    static BallisticsHelper.AimSolution aimSolution(Player player, LivingEntity target) {
        Vec3 targetPoint = TargetSelector.visibleAimPoint(player, target, Config.aimAtHead);
        return aimSolution(player, target, targetPoint);
    }

    static BallisticsHelper.AimSolution aimSolution(Player player, LivingEntity target, AimDecision decision) {
        Vec3 targetPoint = TargetSelector.visibleAimPoint(player, target, decision.headshot());
        return aimSolution(player, target, targetPoint);
    }

    private static BallisticsHelper.AimSolution aimSolution(Player player, LivingEntity target, Vec3 targetPoint) {
        return BallisticsHelper.solveAim(
                player.getEyePosition(),
                player.getDeltaMovement(),
                targetPoint,
                target.getDeltaMovement(),
                BallisticsHelper.getParameters(player)
        );
    }

    static Vec3 applyAimOffset(Vec3 direction, Vec3 shooterPosition, AABB targetBox, AimDecision decision) {
        if (!decision.intentionalMiss()
                || (Math.abs(decision.missYawFactor()) < 1.0e-9 && Math.abs(decision.missPitchFactor()) < 1.0e-9)) {
            return direction.normalize();
        }

        Vec3 normalized = direction.normalize();
        double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        if (horizontal < 1.0e-9) {
            return normalized;
        }

        double yaw = Math.toDegrees(Math.atan2(normalized.z, normalized.x)) - 90.0;
        double pitch = -Math.toDegrees(Math.atan2(normalized.y, horizontal));
        yaw += missYawOffsetDegrees(shooterPosition, targetBox, decision.missYawFactor());
        pitch += missPitchOffsetDegrees(shooterPosition, targetBox, decision.missPitchFactor());

        double yawRadians = Math.toRadians(yaw + 90.0);
        double pitchRadians = -Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        return new Vec3(
                Math.cos(yawRadians) * cosPitch,
                Math.sin(pitchRadians),
                Math.sin(yawRadians) * cosPitch
        ).normalize();
    }

    static double missYawOffsetDegrees(Vec3 shooterPosition, AABB targetBox, double factor) {
        double distance = Math.max(1.0e-6, shooterPosition.distanceTo(targetBox.getCenter()));
        double halfWidth = Math.hypot(targetBox.getXsize(), targetBox.getZsize()) * 0.5;
        double missRadius = halfWidth * Math.abs(factor) + MISS_CLEARANCE;
        double magnitude = Math.toDegrees(Math.atan2(missRadius, distance));
        return Math.copySign(magnitude, factor);
    }

    static double missPitchOffsetDegrees(Vec3 shooterPosition, AABB targetBox, double factor) {
        double distance = Math.max(1.0e-6, shooterPosition.distanceTo(targetBox.getCenter()));
        double halfAngle = Math.toDegrees(Math.atan2(targetBox.getYsize() * 0.5, distance));
        return factor * Math.min(1.0, halfAngle * 0.25);
    }

    static boolean isAligned(Player player, float[] targetRotation, float toleranceDegrees) {
        if (player == null || targetRotation == null || targetRotation.length < 2) return false;
        float yawError = Math.abs(Mth.degreesDifference(player.getYRot(), targetRotation[0]));
        float pitchError = Math.abs(player.getXRot() - targetRotation[1]);
        return yawError <= toleranceDegrees && pitchError <= toleranceDegrees;
    }

    static Vec3 leadTarget(Vec3 targetPoint, Vec3 relativeVelocity, double flightTicks) {
        return targetPoint.add(relativeVelocity.scale(Math.max(0.0, flightTicks)));
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
        if (Math.abs(delta) < 0.1f) {
            return target;
        }
        float correction = (float) Math.min(1.0, Math.max(0.0, (1.0 - speed) * AIM_STRENGTH_MULTIPLIER));
        return current + delta * correction;
    }

    public static void applySmoothRotation(Player player, float targetYaw, float targetPitch) {
        float speed = (float) Config.aimSpeed;

        float newYaw = smoothAngle(player.getYRot(), targetYaw, speed);
        float newPitch = smoothAngle(player.getXRot(), targetPitch, speed);

        player.setYRot(newYaw);
        player.setXRot(newPitch);
    }
}
