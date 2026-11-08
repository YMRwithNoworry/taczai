package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotHandlerTest {
    @Test
    void autoFireStopsWhileReloading() {
        assertFalse(AimbotHandler.shouldAutoFire(true, 0L));
    }

    @Test
    void autoFireDoesNotWaitForAdsProgressOrPlayerMovement() {
        assertTrue(AimbotHandler.shouldAutoFire(false, 0L));
    }

    @Test
    void autoFireStopsWhenTheGunIndexIsUnknown() {
        assertFalse(AimbotHandler.shouldAutoFire(false, -1L));
    }

    @Test
    void autoFireWaitsUntilTaczWillAcceptTheShot() {
        assertFalse(AimbotHandler.shouldAutoFire(false, 50L));
        assertFalse(AimbotHandler.shouldAutoFire(false, 120L));
    }

    @Test
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(false, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 25L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 49L));
    }
}
