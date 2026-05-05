package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 targetPos;

        if (Config.aimAtHead) {
            targetPos = target.getEyePosition();
        } else {
            targetPos = target.getBoundingBox().getCenter();
        }

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

    public static float smoothAngle(float current, float target, float speed) {
        float delta = Mth.degreesDifference(current, target);
        if (Math.abs(delta) < 0.1f) {
            return target;
        }
        return current + delta * (1.0f - speed);
    }

    public static void applySmoothRotation(Player player, float targetYaw, float targetPitch) {
        float speed = (float) Config.aimSpeed;

        float newYaw = smoothAngle(player.getYRot(), targetYaw, speed);
        float newPitch = smoothAngle(player.getXRot(), targetPitch, speed);

        player.setYRot(newYaw);
        player.setXRot(newPitch);
    }
}
