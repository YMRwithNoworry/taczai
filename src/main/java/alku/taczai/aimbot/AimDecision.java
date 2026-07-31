package alku.taczai.aimbot;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * One target-lock shot decision. It is kept stable until the shot is fired so
 * that smoothing does not redraw the configured probabilities every tick.
 */
record AimDecision(
        boolean headshot,
        boolean aimAtHeadEnabled,
        double headshotRate
) {
    static AimDecision sample(boolean aimAtHeadEnabled, double headshotRate) {
        return sample(aimAtHeadEnabled, headshotRate, ThreadLocalRandom.current());
    }

    static AimDecision sample(
            boolean aimAtHeadEnabled,
            double headshotRate,
            RandomGenerator random
    ) {
        double safeHeadshotRate = clampPercentage(headshotRate);
        boolean headshot = aimAtHeadEnabled && random.nextDouble() * 100.0 < safeHeadshotRate;
        return new AimDecision(headshot, aimAtHeadEnabled, safeHeadshotRate);
    }

    boolean matches(boolean configuredAimAtHead, double configuredHeadshotRate) {
        return aimAtHeadEnabled == configuredAimAtHead
                && Double.compare(headshotRate, clampPercentage(configuredHeadshotRate)) == 0;
    }

    private static double clampPercentage(double value) {
        if (!Double.isFinite(value)) return 0.0;
        return Math.max(0.0, Math.min(100.0, value));
    }
}
