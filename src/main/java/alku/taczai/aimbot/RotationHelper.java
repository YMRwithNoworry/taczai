package alku.taczai.aimbot;

import alku.taczai.Config;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RotationHelper {
    /** Below this remaining difference the aim counts as reached. */
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

    /**
     * Moves {@code current} toward {@code target} by at most {@code maxStepDegrees}.
     * The exact target is returned once the remaining difference fits into the step,
     * so the aim always reaches the target instead of asymptotically approaching it.
     */
    public static float stepAngle(float current, float target, float maxStepDegrees) {
        float delta = Mth.degreesDifference(current, target);
        float step = Math.max(maxStepDegrees, SNAP_ANGLE_THRESHOLD);
        if (Math.abs(delta) <= step) {
            return Mth.wrapDegrees(target);
        }
        return Mth.wrapDegrees(current + Math.copySign(maxStepDegrees, delta));
    }

    /**
     * Turns the player's own rotation - the one the client reports to the server and
     * the one other players see on the model - toward the aim at a limited rate, so
     * the turn has a visible process instead of a single snap.
     *
     * @return true when the rotation reached the aim on this tick, i.e. the turn was
     *         not rate limited. TACZ reads the shot direction from the server side
     *         rotation, so auto fire only shoots while this holds.
     */
    public static boolean turnTowards(Player player, float targetYaw, float targetPitch, float maxStepDegrees) {
        float allowed = Math.max(maxStepDegrees, SNAP_ANGLE_THRESHOLD);
        boolean reached = Math.abs(Mth.degreesDifference(player.getYRot(), targetYaw)) <= allowed
                && Math.abs(Mth.degreesDifference(player.getXRot(), targetPitch)) <= allowed;

        player.setYRot(stepAngle(player.getYRot(), targetYaw, maxStepDegrees));
        player.setXRot(Mth.clamp(stepAngle(player.getXRot(), targetPitch, maxStepDegrees), -90.0F, 90.0F));
        return reached;
    }

    public static void applySnapRotation(Player player, float targetYaw, float targetPitch) {
        player.setYRot(Mth.wrapDegrees(targetYaw));
        player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
    }
}
