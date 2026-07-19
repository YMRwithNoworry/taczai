package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotHandlerTest {
    @Test
    void autoFireStopsWhileReloading() {
        assertFalse(AimbotHandler.shouldAutoFire(true, false, true));
    }

    @Test
    void autoFireStopsWhileGunStateIsLocked() {
        assertFalse(AimbotHandler.shouldAutoFire(true, true, false));
    }

    @Test
    void autoFireNeedsCrosshairOnTarget() {
        assertFalse(AimbotHandler.shouldAutoFire(false, false, false));
        assertTrue(AimbotHandler.shouldAutoFire(true, false, false));
    }
}
