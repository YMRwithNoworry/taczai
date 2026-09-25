package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

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
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 25L));
        assertTrue(AimbotHandler.shouldAutoFire(false, true, 49L));
    }
}
