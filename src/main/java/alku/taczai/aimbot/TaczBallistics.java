package alku.taczai.aimbot;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Reads the TACZ gun data used by the server when it creates a bullet.
 * Keeping this in a separate class also makes it possible to unit test the
 * physics without a running TACZ instance.
 */
final class TaczBallistics {
    private static final double DEFAULT_GLOBAL_SPEED_MULTIPLIER = 1.0;

    private TaczBallistics() {
    }

    static BallisticsSolver.Parameters getParameters(Player player) {
        if (player == null) {
            return BallisticsSolver.Parameters.fallback();
        }

        ItemStack gunStack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(gunStack);
        if (gun == null) {
            return BallisticsSolver.Parameters.fallback();
        }

        try {
            CommonGunIndex gunIndex = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack)).orElse(null);
            if (gunIndex == null) {
                return BallisticsSolver.Parameters.fallback();
            }

            BulletData bulletData = gunIndex.getBulletData();
            double configuredSpeed = bulletData.getSpeed();

            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            AttachmentCacheProperty cache = operator == null ? null : operator.getCacheProperty();
            if (cache != null) {
                Float cachedSpeed = cache.getCache(GunProperties.AMMO_SPEED);
                if (cachedSpeed != null && cachedSpeed > 0.0F) {
                    configuredSpeed = cachedSpeed;
                }
            }
            if (!Double.isFinite(configuredSpeed) || configuredSpeed <= 0.0) {
                return BallisticsSolver.Parameters.fallback();
            }

            double globalMultiplier = DEFAULT_GLOBAL_SPEED_MULTIPLIER;
            if (AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER != null) {
                globalMultiplier = AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get();
            }
            if (!Double.isFinite(globalMultiplier) || globalMultiplier <= 0.0) {
                globalMultiplier = DEFAULT_GLOBAL_SPEED_MULTIPLIER;
            }

            double speedPerTick = configuredSpeed * globalMultiplier / BallisticsSolver.TICKS_PER_SECOND;
            double gravity = bulletData.getGravity();
            if (!Double.isFinite(gravity) || gravity < 0.0) {
                gravity = 0.0;
            }
            double friction = bulletData.getFriction();
            if (!Double.isFinite(friction) || friction < 0.0) {
                friction = 0.0;
            }
            double lifeTicks = Math.floor(bulletData.getLifeSecond() * BallisticsSolver.TICKS_PER_SECOND);
            if (!Double.isFinite(lifeTicks) || lifeTicks <= 0.0) {
                return BallisticsSolver.Parameters.fallback();
            }

            return new BallisticsSolver.Parameters(
                    speedPerTick,
                    gravity,
                    friction,
                    lifeTicks,
                    true
            );
        } catch (RuntimeException | LinkageError ignored) {
            return BallisticsSolver.Parameters.fallback();
        }
    }

    /**
     * TACZ does not test bullets against the plain entity box. Its server-side
     * hitbox is expanded in the direction of movement and then moved backwards
     * to compensate for latency. Aiming at the middle of the plain box misses
     * moving targets, so this returns the center offset of the hitbox TACZ
     * actually uses.
     */
    static Vec3 getTargetHitboxCenterOffset(LivingEntity target, Vec3 velocity) {
        if (velocity.lengthSqr() < 1.0e-8) {
            return Vec3.ZERO;
        }

        double hitboxOffset = 3.0;
        try {
            if (OtherConfig.SERVER_HITBOX_OFFSET != null) {
                hitboxOffset = OtherConfig.SERVER_HITBOX_OFFSET.get();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep TACZ's default.
        }
        if (!Double.isFinite(hitboxOffset)) {
            hitboxOffset = 3.0;
        }

        double centerShift = target instanceof Player
                ? hitboxOffset - 4.5
                : -4.5;
        return velocity.scale(centerShift);
    }
}
