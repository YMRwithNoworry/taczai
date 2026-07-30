package alku.taczai.aimbot;

import alku.taczai.Config;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    private static final double DEFAULT_BULLET_SPEED = 4.0;
    private static final double MAX_PREDICTION_TICKS = 8.0;
    private static final double AIM_STRENGTH_MULTIPLIER = 1.35;

    public static float[] getTargetRotation(Player player, LivingEntity target) {
        Vec3 playerPos = player.getEyePosition();
        Vec3 targetPos = predictTargetPoint(player, target);

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

    static Vec3 predictTargetPoint(Player player, LivingEntity target) {
        Vec3 targetPoint = TargetSelector.visibleAimPoint(player, target, Config.aimAtHead);
        return targetPoint.add(predictionOffset(player, target, targetPoint));
    }

    static AABB predictTargetBox(Player player, LivingEntity target) {
        Vec3 targetPoint = TargetSelector.visibleAimPoint(player, target, Config.aimAtHead);
        return moveTargetBox(target.getBoundingBox(), predictionOffset(player, target, targetPoint));
    }

    static AABB moveTargetBox(AABB targetBox, Vec3 predictionOffset) {
        return targetBox.move(predictionOffset);
    }

    private static Vec3 predictionOffset(Player player, LivingEntity target, Vec3 targetPoint) {
        double distance = player.getEyePosition().distanceTo(targetPoint);
        double bulletSpeed = getBulletSpeed(player);
        double flightTicks = Math.min(MAX_PREDICTION_TICKS, distance / bulletSpeed);
        Vec3 relativeVelocity = target.getDeltaMovement().subtract(player.getDeltaMovement());
        return relativeVelocity.scale(Math.max(0.0, flightTicks));
    }

    static Vec3 leadTarget(Vec3 targetPoint, Vec3 relativeVelocity, double flightTicks) {
        return targetPoint.add(relativeVelocity.scale(Math.max(0.0, flightTicks)));
    }

    private static double getBulletSpeed(Player player) {
        IGun gun = IGun.getIGunOrNull(player.getMainHandItem());
        if (gun == null) return DEFAULT_BULLET_SPEED;

        try {
            return TimelessAPI.getCommonGunIndex(gun.getGunId(player.getMainHandItem()))
                    .map(CommonGunIndex::getGunData)
                    .map(GunData::getBulletData)
                    .map(BulletData::getSpeed)
                    .filter(speed -> speed > 0.0F)
                    .map(Float::doubleValue)
                    .orElse(DEFAULT_BULLET_SPEED);
        } catch (RuntimeException ignored) {
            return DEFAULT_BULLET_SPEED;
        }
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
