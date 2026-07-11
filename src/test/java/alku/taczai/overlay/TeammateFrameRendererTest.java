package alku.taczai.overlay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeammateFrameRendererTest {
    @Test
    void framesOnlyEnabledLivingRemoteTeammates() {
        assertTrue(TeammateFrameRenderer.shouldRender(true, false, true, true));
        assertFalse(TeammateFrameRenderer.shouldRender(false, false, true, true));
        assertFalse(TeammateFrameRenderer.shouldRender(true, true, true, true));
        assertFalse(TeammateFrameRenderer.shouldRender(true, false, false, true));
        assertFalse(TeammateFrameRenderer.shouldRender(true, false, true, false));
    }
}
