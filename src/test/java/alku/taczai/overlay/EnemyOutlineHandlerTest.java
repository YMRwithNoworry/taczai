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

}
