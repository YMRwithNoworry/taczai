package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void theTurnDurationIsConvertedToWholeTicksIncludingZeroDelay() {
        assertEquals(0, AimbotHandler.turnTicks(0.0));
        assertEquals(2, AimbotHandler.turnTicks(100.0));
        assertEquals(2, AimbotHandler.turnTicks(120.0));
        assertEquals(3, AimbotHandler.turnTicks(125.0));
        assertEquals(3, AimbotHandler.turnTicks(150.0));
    }

    @Test
    void theTurnDurationStaysInsideTheConfiguredWindow() {
        assertEquals(0, AimbotHandler.turnTicks(-1.0));
        assertEquals(3, AimbotHandler.turnTicks(1000.0));
    }

    @Test
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(false, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 25L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 49L));
    }
}
