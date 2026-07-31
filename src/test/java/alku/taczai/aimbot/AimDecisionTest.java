package alku.taczai.aimbot;

import java.util.Random;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimDecisionTest {
    @Test
    void headshotRateBoundariesAreDeterministic() {
        AimDecision alwaysHead = AimDecision.sample(true, 100.0, 0.0, new Random(1L));
        AimDecision neverHead = AimDecision.sample(true, 0.0, 0.0, new Random(1L));
        AimDecision disabledHeadAim = AimDecision.sample(false, 100.0, 0.0, new Random(1L));

        assertTrue(alwaysHead.headshot());
        assertFalse(neverHead.headshot());
        assertFalse(disabledHeadAim.headshot());
        assertFalse(alwaysHead.intentionalMiss());
    }

    @Test
    void missRateBoundariesAreDeterministic() {
        AimDecision neverMiss = AimDecision.sample(true, 100.0, 0.0, new Random(1L));
        AimDecision alwaysMiss = AimDecision.sample(true, 100.0, 100.0, new Random(1L));

        assertFalse(neverMiss.intentionalMiss());
        assertTrue(alwaysMiss.intentionalMiss());
        assertTrue(Math.abs(alwaysMiss.missYawDegrees()) >= 18.0);
    }

    @Test
    void intentionalMissChangesTheAimDirection() {
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        AimDecision decision = new AimDecision(true, true, 20.0, 0.0, true, 100.0, 100.0);

        Vec3 offset = RotationHelper.applyAimOffset(direction, decision);

        assertNotEquals(direction, offset);
        assertTrue(Math.abs(offset.length() - 1.0) < 1.0e-9);
    }
}
