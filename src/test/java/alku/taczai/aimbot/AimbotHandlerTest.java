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
    void semiAutomaticFirePulsesTheControllerAfterEachRecordedShot() {
        assertTrue(TaczAutoFireCompat.shouldReleaseAfterRecordedShot(false, 100L, 200L));
        assertFalse(TaczAutoFireCompat.shouldReleaseAfterRecordedShot(false, 200L, 200L));
    }

    @Test
    void continuousFireKeepsTheControllerHeldBetweenRecordedShots() {
        assertFalse(TaczAutoFireCompat.shouldReleaseAfterRecordedShot(true, 100L, 200L));
    }

    @Test
    void autoFireUsesTaczControllerStateInsteadOfSubmittingItsOwnShot() {
        assertTrue(TaczAutoFireCompat.findControllerShootTick(ControllerShootKey.class) != null);
        assertTrue(TaczAutoFireCompat.findControllerShootTick(OldShootKey.class) == null);
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
    void autoFireWaitsUntilTheSmoothedCameraIsAligned() {
        assertFalse(AimbotHandler.isAimAligned(0.0F, 0.0F, 2.0F, 0.0F, 1.0F));
        assertFalse(AimbotHandler.isAimAligned(0.0F, 0.0F, 0.0F, -2.0F, 1.0F));
        assertTrue(AimbotHandler.isAimAligned(179.5F, 2.0F, -180.0F, 2.5F, 1.0F));
    }

    @Test
    void autoFireRunsAsSoonAsTaczCooldownAllowsIt() {
        assertTrue(AimbotHandler.shouldAutoFire(false, 0L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 25L));
        assertTrue(AimbotHandler.shouldAutoFire(false, 49L));
    }
}
