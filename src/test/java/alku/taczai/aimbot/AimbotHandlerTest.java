package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotHandlerTest {
    @Test
    void autoFireStopsWhileReloading() {
        assertFalse(AimbotHandler.shouldAutoFire(true, true, 0L));
    }

    @Test
    void autoFireWaitsUntilAdsProgressIsCompleteForGuaranteedAccuracy() {
        assertFalse(AimbotHandler.shouldAutoFire(false, false, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 0L));
    }

    @Test
    void autoFireStopsWhenTheGunIndexIsUnknown() {
        assertFalse(AimbotHandler.shouldAutoFire(false, true, -1L));
    }

    @Test
    void autoFireWaitsUntilTaczWillAcceptTheShot() {
        assertFalse(AimbotHandler.shouldAutoFire(false, true, 50L));
        assertFalse(AimbotHandler.shouldAutoFire(false, true, 120L));
    }

    @Test
    void theTurnRateGrowsWithTargetsThatKeepMovingFaster() {
        assertEquals(6.0F, AimbotHandler.effectiveStep(6.0, 0.0F), 1.0e-4);
        assertEquals(6.0F, AimbotHandler.effectiveStep(6.0, 4.0F), 1.0e-4);
        assertEquals(9.0F, AimbotHandler.effectiveStep(6.0, 9.0F), 1.0e-4);
    }

    @Test
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 25L));
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 49L));
    }
}
