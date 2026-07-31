package alku.taczai.aimbot;

import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimDecisionTest {
    @Test
    void headshotRateBoundariesAreDeterministic() {
        AimDecision alwaysHead = AimDecision.sample(true, 100.0, new Random(1L));
        AimDecision neverHead = AimDecision.sample(true, 0.0, new Random(1L));
        AimDecision disabledHeadAim = AimDecision.sample(false, 100.0, new Random(1L));

        assertTrue(alwaysHead.headshot());
        assertFalse(neverHead.headshot());
        assertFalse(disabledHeadAim.headshot());
    }

    @Test
    void decisionMatchesCurrentHeadshotConfiguration() {
        AimDecision decision = AimDecision.sample(true, 50.0, new Random(1L));

        assertTrue(decision.matches(true, 50.0));
        assertFalse(decision.matches(false, 50.0));
        assertFalse(decision.matches(true, 75.0));
    }
}
