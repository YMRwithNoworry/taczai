package alku.taczai.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyOutlineHandlerTest {
    @Test
    void outlinesOnlyEnabledLivingNonAlliedRemotePlayers() {
        assertTrue(EnemyOutlineHandler.shouldOutline(true, false, false, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(false, false, false, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(true, true, false, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(true, false, true, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(true, false, false, false));
    }

    @Test
    void clearsOnlyGlowOwnedAndIntroducedByThisHandler() {
        assertTrue(EnemyOutlineHandler.shouldClearOwnedGlow(true, true, false));
        assertFalse(EnemyOutlineHandler.shouldClearOwnedGlow(false, true, false));
        assertFalse(EnemyOutlineHandler.shouldClearOwnedGlow(true, false, false));
        assertFalse(EnemyOutlineHandler.shouldClearOwnedGlow(true, true, true));
    }
}
