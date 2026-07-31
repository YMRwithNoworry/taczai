package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotHandlerTest {
    @Test
    void autoFireStopsWhileReloading() {
        assertFalse(AimbotHandler.shouldAutoFire(true, false, true, 0));
    }

    @Test
    void autoFireStopsWhileNonShootingGunStateIsLocked() {
        assertFalse(AimbotHandler.shouldAutoFire(true, true, false, 0));
    }

    @Test
    void autoFireLetsTaczHandleItsOwnShootCooldown() {
        assertTrue(AimbotHandler.shouldAutoFire(true, true, false, 25));
    }

    @Test
    void autoFireRequiresTheCrosshairToBeOnTheTarget() {
        assertFalse(AimbotHandler.shouldAutoFire(false, false, false, 0));
        assertTrue(AimbotHandler.shouldAutoFire(true, false, false, 0));
    }
}
