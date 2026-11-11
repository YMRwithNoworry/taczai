package alku.taczai.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyOutlineHandlerTest {
    @Test
    void outlinesOnlyEnabledLivingRemoteEnemies() {
        assertTrue(EnemyOutlineHandler.shouldOutline(false, false, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(true, false, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(false, true, true));
        assertFalse(EnemyOutlineHandler.shouldOutline(false, false, false));
    }
}
