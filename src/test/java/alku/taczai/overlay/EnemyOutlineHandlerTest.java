package alku.taczai.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnemyOutlineHandlerTest {
    @Test
    void rendersOnlyLivingPickableEnemyTargets() {
        assertTrue(EnemyOutlineHandler.shouldRenderTargetable(false, false, true, true, false));
        assertFalse(EnemyOutlineHandler.shouldRenderTargetable(true, false, true, true, false));
        assertFalse(EnemyOutlineHandler.shouldRenderTargetable(false, true, true, true, false));
        assertFalse(EnemyOutlineHandler.shouldRenderTargetable(false, false, false, true, false));
        assertFalse(EnemyOutlineHandler.shouldRenderTargetable(false, false, true, false, false));
        assertFalse(EnemyOutlineHandler.shouldRenderTargetable(false, false, true, true, true));
    }
}
