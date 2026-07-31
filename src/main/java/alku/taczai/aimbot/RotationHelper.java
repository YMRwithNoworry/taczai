package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double AIM_STRENGTH_MULTIPLIER = 1.35;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        Vec3 direction = aimSolution(player, target).direction();
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

    private static BallisticsHelper.AimSolution aimSolution(Player player, LivingEntity target, Vec3 targetPoint) {
        return BallisticsHelper.solveAim(
                player.getEyePosition(),
                player.getDeltaMovement(),
                targetPoint,
                target.getDeltaMovement(),
                BallisticsHelper.getParameters(player)
        );
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
