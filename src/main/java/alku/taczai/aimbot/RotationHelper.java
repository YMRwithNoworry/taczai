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
        if (shouldUseDirectCloseAim(
                spawn,
                TaczHitboxCompat.getHitboxBox(target, player),
                parameters.speedPerTick()
        )) {
            // At point-blank ranges, avoid extrapolating beyond the visible TACZ aim region.
            Vec3 closeAimPoint = TargetSelector.visibleAimPoint(player, target, decision.headshot());
            return directionToRotation(player, closeAimPoint.subtract(spawn));
        }

        Vec3 targetVelocity = BallisticsSolver.targetVelocity(target);
        Vec3 aimPoint = TargetSelector.visibleAimPoint(player, target, decision.headshot());

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

    public static void applySnapRotation(Player player, float targetYaw, float targetPitch) {
        player.setYRot(Mth.wrapDegrees(targetYaw));
        player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
    }
}
