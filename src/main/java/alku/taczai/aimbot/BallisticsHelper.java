package alku.taczai.aimbot;

import com.tacz.guns.api.GunProperties;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class BallisticsHelper {
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double DEFAULT_SPEED_PER_TICK = 4.0;
    private static final double DEFAULT_LIFE_TICKS = 40.0;
    private static final double SOLUTION_STEP = 0.05;
    private static final double VANILLA_INACCURACY_SCALE = 0.0172275;

    private BallisticsHelper() {
    }

    static Parameters getParameters(Player player) {
        ItemStack stack = player.getMainHandItem();
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) return Parameters.fallback();

        try {
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null);
            if (index == null) return Parameters.fallback();

            BulletData bullet = index.getBulletData();
            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            AttachmentCacheProperty cache = operator == null ? null : operator.getCacheProperty();

            double rawSpeed = bullet.getSpeed();
            InaccuracyType inaccuracyType = InaccuracyType.getInaccuracyType(player);
            double inaccuracy = index.getGunData().getInaccuracy(inaccuracyType);
            if (cache != null) {
                Float cachedSpeed = cache.getCache(GunProperties.AMMO_SPEED);
                if (cachedSpeed != null) rawSpeed = cachedSpeed;

                Map<InaccuracyType, Float> values = cache.getCache(GunProperties.INACCURACY);
                if (values != null) {
                    Float value = values.get(inaccuracyType);
                    if (value != null) inaccuracy = value;
                }
            }

            double globalSpeed = AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER == null
                    ? 1.0
                    : AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get();
            double speedPerTick = speedPerTick(rawSpeed, globalSpeed);
            double lifeTicks = Math.max(1.0, Math.floor(bullet.getLifeSecond() * TICKS_PER_SECOND));
            return new Parameters(
                    speedPerTick,
                    bullet.getGravity(),
                    bullet.getFriction(),
                    lifeTicks,
                    inaccuracy,
                    true
            ).sanitized();
        } catch (RuntimeException ignored) {
            return Parameters.fallback();
        }
    }

    static AimSolution solveAim(
            Vec3 shooterPosition,
            Vec3 shooterVelocity,
            Vec3 targetPosition,
            Vec3 targetVelocity,
            Parameters parameters
    ) {
        Parameters safe = parameters;
        double bestTime = SOLUTION_STEP;
        double bestError = Double.POSITIVE_INFINITY;
        double previousTime = 0.0;
        double previousError = interceptError(
                shooterPosition, shooterVelocity, targetPosition, targetVelocity, safe, previousTime
        );

        for (double time = SOLUTION_STEP; time <= safe.lifeTicks() + 1.0e-9; time += SOLUTION_STEP) {
            double error = interceptError(shooterPosition, shooterVelocity, targetPosition, targetVelocity, safe, time);
            double absoluteError = Math.abs(error);
            if (absoluteError < bestError) {
                bestError = absoluteError;
                bestTime = time;
            }

            if (previousError > 0.0 && error <= 0.0) {
                bestTime = refineInterceptTime(
                        shooterPosition,
                        shooterVelocity,
                        targetPosition,
                        targetVelocity,
                        safe,
                        previousTime,
                        time
                );
                break;
            }
            previousTime = time;
            previousError = error;
        }

        Vec3 required = requiredGunDisplacement(
                shooterPosition, shooterVelocity, targetPosition, targetVelocity, safe, bestTime
        );
        Vec3 direction = required.lengthSqr() < 1.0e-12 ? new Vec3(0.0, 0.0, 1.0) : required.normalize();
        return new AimSolution(direction, bestTime);
    }

    static double speedPerTick(double configuredSpeed, double globalModifier) {
        return configuredSpeed * globalModifier / TICKS_PER_SECOND;
    }

    static Vec3 positionAtTime(
            Vec3 start,
            Vec3 direction,
            Vec3 shooterVelocity,
            Parameters parameters,
            double time
    ) {
        Parameters safe = parameters.sanitized();
        double travel = travelFactor(time, safe.friction());
        Vec3 initial = direction.normalize().scale(safe.speedPerTick()).add(shooterVelocity);
        return start.add(initial.scale(travel)).add(0.0, gravityDisplacement(time, safe.gravity(), safe.friction()), 0.0);
    }

    static Vec3 initialVelocity(Vec3 direction, Vec3 shooterVelocity, Parameters parameters) {
        return direction.normalize().scale(parameters.speedPerTick()).add(shooterVelocity);
    }

    static Vec3 advanceVelocity(Vec3 velocity, Parameters parameters) {
        return velocity.scale(1.0 - parameters.friction()).add(0.0, -parameters.gravity(), 0.0);
    }

    static List<Vec3> spreadDirections(Vec3 direction, double inaccuracy) {
        Vec3 base = direction.normalize();
        double deviation = Math.max(0.0, inaccuracy) * VANILLA_INACCURACY_SCALE;
        if (deviation < 1.0e-9) return List.of(base);

        List<Vec3> directions = new ArrayList<>(27);
        directions.add(base);
        double[] offsets = {-deviation, 0.0, deviation};
        for (double x : offsets) {
            for (double y : offsets) {
                for (double z : offsets) {
                    if (x == 0.0 && y == 0.0 && z == 0.0) continue;
                    directions.add(base.add(x, y, z).normalize());
                }
            }
        }
        return directions;
    }

    private static double refineInterceptTime(
            Vec3 shooterPosition,
            Vec3 shooterVelocity,
            Vec3 targetPosition,
            Vec3 targetVelocity,
            Parameters parameters,
            double lower,
            double upper
    ) {
        for (int iteration = 0; iteration < 24; iteration++) {
            double middle = (lower + upper) * 0.5;
            double error = interceptError(
                    shooterPosition, shooterVelocity, targetPosition, targetVelocity, parameters, middle
            );
            if (error > 0.0) lower = middle;
            else upper = middle;
        }
        return (lower + upper) * 0.5;
    }

    private static double interceptError(
            Vec3 shooterPosition,
            Vec3 shooterVelocity,
            Vec3 targetPosition,
            Vec3 targetVelocity,
            Parameters parameters,
            double time
    ) {
        Vec3 required = requiredGunDisplacement(
                shooterPosition, shooterVelocity, targetPosition, targetVelocity, parameters, time
        );
        return required.length() - parameters.speedPerTick() * travelFactor(time, parameters.friction());
    }

    private static Vec3 requiredGunDisplacement(
            Vec3 shooterPosition,
            Vec3 shooterVelocity,
            Vec3 targetPosition,
            Vec3 targetVelocity,
            Parameters parameters,
            double time
    ) {
        double travel = travelFactor(time, parameters.friction());
        double gravity = gravityDisplacement(time, parameters.gravity(), parameters.friction());
        Vec3 futureTarget = targetPosition.add(targetVelocity.scale(time));
        return futureTarget
                .subtract(shooterPosition)
                .subtract(shooterVelocity.scale(travel))
                .subtract(0.0, gravity, 0.0);
    }

    private static double travelFactor(double time, double friction) {
        int fullTicks = Math.max(0, (int) Math.floor(time));
        double partialTick = Math.max(0.0, time - fullTicks);
        double drag = 1.0 - friction;
        if (friction < 1.0e-9) return fullTicks + partialTick;

        double fullTravel = (1.0 - Math.pow(drag, fullTicks)) / friction;
        return fullTravel + partialTick * Math.pow(drag, fullTicks);
    }

    private static double gravityDisplacement(double time, double gravity, double friction) {
        int fullTicks = Math.max(0, (int) Math.floor(time));
        double partialTick = Math.max(0.0, time - fullTicks);
        if (friction < 1.0e-9) {
            return -gravity * (fullTicks * (fullTicks - 1.0) * 0.5 + partialTick * fullTicks);
        }

        double fullTravel = travelFactor(fullTicks, friction);
        double fullDrop = -gravity * (fullTicks - fullTravel) / friction;
        double verticalVelocity = -gravity * fullTravel;
        return fullDrop + partialTick * verticalVelocity;
    }

    record Parameters(
            double speedPerTick,
            double gravity,
            double friction,
            double lifeTicks,
            double inaccuracy,
            boolean reliable
    ) {
        Parameters(double speedPerTick, double gravity, double friction, double lifeTicks, double inaccuracy) {
            this(speedPerTick, gravity, friction, lifeTicks, inaccuracy, true);
        }

        static Parameters fallback() {
            return new Parameters(DEFAULT_SPEED_PER_TICK, 0.0, 0.0, DEFAULT_LIFE_TICKS, 0.0, false);
        }

        Parameters sanitized() {
            return new Parameters(
                    Math.max(1.0e-3, speedPerTick),
                    Math.max(0.0, gravity),
                    Math.max(0.0, Math.min(0.999, friction)),
                    Math.max(1.0, lifeTicks),
                    Math.max(0.0, inaccuracy),
                    reliable
            );
        }
    }

    record AimSolution(Vec3 direction, double flightTicks) {
    }
}
