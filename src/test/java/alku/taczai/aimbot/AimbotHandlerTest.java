package alku.taczai.aimbot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimbotHandlerTest {
    @Test
    void autoFireDoesNotWaitForAdsProgressOrPlayerMovement() {
        assertTrue(AimbotHandler.shouldAutoFire(0L));
    }

    @Test
    void autoFireStopsWhenTheGunIndexIsUnknown() {
        assertFalse(AimbotHandler.shouldAutoFire(-1L));
    }

    @Test
    void autoFireWaitsUntilTaczWillAcceptTheShot() {
        assertFalse(AimbotHandler.shouldAutoFire(50L));
        assertFalse(AimbotHandler.shouldAutoFire(120L));
    }

    @Test
    void chargedWeaponsCanUseTheNativeControllerWhenTaczProvidesIt() {
        assertNotNull(TaczAutoFireCompat.findControllerShootTick(ControllerShootKey.class));
        assertNull(TaczAutoFireCompat.findControllerShootTick(OldShootKey.class));
    }

    public static final class ControllerShootKey {
        public static boolean shootControllerTick(boolean down) {
            return false;
        }
    }

    public static final class OldShootKey {
        public static void shootControllerTick(boolean down) {
        }
    }

    @Test
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(0L));
        assertTrue(AimbotHandler.shouldAutoFire(25L));
        assertTrue(AimbotHandler.shouldAutoFire(49L));
    }
}
