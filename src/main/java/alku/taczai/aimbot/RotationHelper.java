package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double AIM_STRENGTH_MULTIPLIER = 2.0;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        AimDecision decision = AimDecision.sample(Config.aimAtHead, Config.headshotRate);
        return getTargetRotation(player, target, decision);
    }

    static float[] getTargetRotation(Player player, LivingEntity target, AimDecision decision) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 targetPos = TargetSelector.visibleAimPoint(player, target, decision.headshot());
        double dx = targetPos.x - playerPos.x;
        double dy = targetPos.y - playerPos.y;
        double dz = targetPos.z - playerPos.z;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDist < 0.001) {
            return new float[]{player.getYRot(), player.getXRot()};
        }

        float targetYaw = (float) (Mth.atan2(dz, dx) * 180.0F / Math.PI) - 90.0F;
        float targetPitch = (float) -(Mth.atan2(dy, horizontalDist) * 180.0F / Math.PI);

        return new float[]{targetYaw, targetPitch};
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

    static boolean isAligned(Player player, float[] targetRotation, float toleranceDegrees) {
        if (player == null || targetRotation == null || targetRotation.length < 2) return false;
        float yawError = Math.abs(Mth.degreesDifference(player.getYRot(), targetRotation[0]));
        float pitchError = Math.abs(player.getXRot() - targetRotation[1]);
        return yawError <= toleranceDegrees && pitchError <= toleranceDegrees;
    }
}
