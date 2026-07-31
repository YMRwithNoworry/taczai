package alku.taczai.aimbot;

import java.util.Random;

import net.minecraft.world.phys.AABB;
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
        assertTrue(Math.abs(alwaysMiss.missYawFactor()) >= 1.15);
    }

    @Test
    void intentionalMissChangesTheAimDirection() {
        Vec3 direction = new Vec3(0.0, 0.0, 1.0);
        AimDecision decision = new AimDecision(true, true, 1.2, 0.0, true, 100.0, 100.0);
        AABB targetBox = new AABB(-0.3, 0.0, 99.7, 0.3, 1.8, 100.3);

        Vec3 offset = RotationHelper.applyAimOffset(direction, new Vec3(0.0, 1.62, 0.0), targetBox, decision);

        assertNotEquals(direction, offset);
        assertTrue(Math.abs(offset.length() - 1.0) < 1.0e-9);
    }

    @Test
    void missOffsetShrinksAtLongRangeInsteadOfThrowingAimFarAway() {
        AABB nearTarget = new AABB(-0.3, 0.0, 4.7, 0.3, 1.8, 5.3);
        AABB farTarget = new AABB(-0.3, 0.0, 99.7, 0.3, 1.8, 100.3);
        Vec3 shooter = new Vec3(0.0, 1.62, 0.0);

        double nearOffset = RotationHelper.missYawOffsetDegrees(shooter, nearTarget, 1.2);
        double farOffset = RotationHelper.missYawOffsetDegrees(shooter, farTarget, 1.2);

        assertTrue(nearOffset > farOffset);
        assertTrue(farOffset < 0.5);
        assertTrue(farOffset > 0.25);
    }
}
