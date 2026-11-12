package alku.taczai.aimbot;

import com.tacz.guns.util.HitboxHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Resolves the same collision regions TACZ tests for projectile hits. */
public final class TaczHitboxCompat {
    private static final int[][] BOX_EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    private TaczHitboxCompat() {
    }

    public static int[][] boxEdges() {
        return java.util.Arrays.stream(BOX_EDGES).map(int[]::clone).toArray(int[][]::new);
    }

    public static List<List<Vec3>> getHitboxCorners(Entity target, Entity shooter) {
        if (target == null) {
            return List.of();
        }

        if (target instanceof Player player) {
            List<List<Vec3>> peekRegions = PeekTargetCompat.activeHitboxCorners(player);
            if (!peekRegions.isEmpty()) {
                return peekRegions;
            }
        }

        AABB hitbox = target.getBoundingBox();
        try {
            AABB taczHitbox = HitboxHelper.getFixedBoundingBox(target, shooter);
            if (taczHitbox != null) {
                hitbox = taczHitbox;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Fall back to the entity's current collision box if TACZ is unavailable.
        }
        return List.of(PeekTargetCompat.boxCorners(hitbox));
    }
}
