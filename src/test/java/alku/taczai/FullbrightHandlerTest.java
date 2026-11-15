package alku.taczai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullbrightHandlerTest {
    @Test
    void refreshesNightVisionBeforeTheFadeWindow() {
        assertTrue(FullbrightHandler.shouldRefreshNightVision(0));
        assertTrue(FullbrightHandler.shouldRefreshNightVision(240));
        assertFalse(FullbrightHandler.shouldRefreshNightVision(241));
        assertFalse(FullbrightHandler.shouldRefreshNightVision(-1));
    }
}
