package alku.taczai.aimbot;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetSelectorTest {
    @Test
    void closeTargetIntersectingCrosshairHasPerfectAimScore() {
        Vec3 eyePosition = new Vec3(0.0, 1.62, 0.0);
        Vec3 lookDirection = new Vec3(0.0, 0.0, 1.0);
        AABB closeTarget = new AABB(-0.3, 0.0, 0.4, 0.3, 1.8, 1.0);

        assertEquals(0.0, TargetSelector.aimScore(eyePosition, lookDirection, closeTarget), 1.0e-9);
    }
}
