package alku.taczai.aimbot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * One target-lock shot decision. It is kept stable until the shot is fired so
 * that smoothing does not redraw the configured probabilities every tick.
 */
record AimDecision(
        boolean headshot,
        boolean intentionalMiss,
        double missYawFactor,
        double missPitchFactor,
        boolean aimAtHeadEnabled,
        double headshotRate,
        double missRate
) {
    private static final double MIN_MISS_YAW_FACTOR = 1.15;
    private static final double MAX_MISS_YAW_FACTOR = 1.5;

    static AimDecision sample(boolean aimAtHeadEnabled, double headshotRate, double missRate) {
        return sample(aimAtHeadEnabled, headshotRate, missRate, ThreadLocalRandom.current());
    }

    static AimDecision sample(
            boolean aimAtHeadEnabled,
            double headshotRate,
            double missRate,
            RandomGenerator random
    ) {
        double safeHeadshotRate = clampPercentage(headshotRate);
        double safeMissRate = clampPercentage(missRate);
        boolean headshot = aimAtHeadEnabled && random.nextDouble() * 100.0 < safeHeadshotRate;
        boolean intentionalMiss = safeMissRate > 0.0 && random.nextDouble() * 100.0 < safeMissRate;

        if (!intentionalMiss) {
            return new AimDecision(
                    headshot,
                    false,
                    0.0,
                    0.0,
                    aimAtHeadEnabled,
                    safeHeadshotRate,
                    safeMissRate
            );
        }

        double yawSign = random.nextBoolean() ? 1.0 : -1.0;
        double yawFactor = yawSign * (
                MIN_MISS_YAW_FACTOR + random.nextDouble() * (MAX_MISS_YAW_FACTOR - MIN_MISS_YAW_FACTOR)
        );
        double pitchFactor = random.nextDouble() * 2.0 - 1.0;
        return new AimDecision(
                headshot,
                true,
                yawFactor,
                pitchFactor,
                aimAtHeadEnabled,
                safeHeadshotRate,
                safeMissRate
        );
    }

    boolean matches(boolean configuredAimAtHead, double configuredHeadshotRate, double configuredMissRate) {
        return aimAtHeadEnabled == configuredAimAtHead
                && Double.compare(headshotRate, clampPercentage(configuredHeadshotRate)) == 0
                && Double.compare(missRate, clampPercentage(configuredMissRate)) == 0;
    }

    private static double clampPercentage(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
