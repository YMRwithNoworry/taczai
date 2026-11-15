package alku.taczai.aimbot;

import net.minecraft.util.Mth;

/** Fast, inertial camera turns for aim tracking without instant rotation snaps. */
final class AimRotationController {
    private static final double MAX_ANGULAR_SPEED = 720.0;
    private static final double ANGULAR_ACCELERATION = 5400.0;
    private static final double MAX_FRAME_SECONDS = 0.05;
    private static final double SETTLE_ANGLE = 0.03;
    private static final double SETTLE_SPEED = 0.15;

    private double yaw;
    private double pitch;
    private double yawVelocity;
    private double pitchVelocity;
    private boolean initialized;

    void reset(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        yawVelocity = 0.0;
        pitchVelocity = 0.0;
        initialized = true;
    }

    float[] update(float targetYaw, float targetPitch, double frameSeconds) {
        if (!initialized) {
            reset(targetYaw, targetPitch);
            return new float[]{(float) yaw, (float) pitch};
        }

        double deltaTime = Math.max(0.0, Math.min(MAX_FRAME_SECONDS, frameSeconds));
        AxisState nextYaw = updateAxis(yaw, yawVelocity, targetYaw, deltaTime, true);
        AxisState nextPitch = updateAxis(pitch, pitchVelocity, Mth.clamp(targetPitch, -90.0F, 90.0F), deltaTime, false);
        yaw = nextYaw.position();
        yawVelocity = nextYaw.velocity();
        pitch = nextPitch.position();
        pitchVelocity = nextPitch.velocity();
        return new float[]{Mth.wrapDegrees((float) yaw), (float) pitch};
    }

    private static AxisState updateAxis(
            double position,
            double velocity,
            double target,
            double deltaTime,
            boolean wrapTarget
    ) {
        double error = wrapTarget ? Mth.wrapDegrees((float) (target - position)) : target - position;
        if (deltaTime == 0.0) {
            return new AxisState(position, velocity);
        }
        if (Math.abs(error) <= SETTLE_ANGLE && Math.abs(velocity) <= SETTLE_SPEED) {
            return new AxisState(wrapTarget ? Mth.wrapDegrees((float) target) : target, 0.0);
        }

        double brakingSpeed = Math.sqrt(2.0 * ANGULAR_ACCELERATION * Math.abs(error));
        double desiredVelocity = Math.copySign(Math.min(MAX_ANGULAR_SPEED, brakingSpeed), error);
        double maxVelocityChange = ANGULAR_ACCELERATION * deltaTime;
        double nextVelocity = approach(velocity, desiredVelocity, maxVelocityChange);
        double movement = (velocity + nextVelocity) * 0.5 * deltaTime;

        if (Math.abs(movement) >= Math.abs(error) && movement * error > 0.0) {
            return new AxisState(wrapTarget ? Mth.wrapDegrees((float) target) : target, 0.0);
        }
        return new AxisState(position + movement, nextVelocity);
    }

    private static double approach(double current, double target, double maxChange) {
        if (current < target) return Math.min(target, current + maxChange);
        return Math.max(target, current - maxChange);
    }

    private record AxisState(double position, double velocity) {
    }
}
