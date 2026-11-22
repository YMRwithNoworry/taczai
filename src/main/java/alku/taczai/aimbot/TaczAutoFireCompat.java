package alku.taczai.aimbot;

import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class TaczAutoFireCompat {
    private static final Method CONTROLLER_SHOOT_TICK = findRuntimeControllerShootTick();

    private TaczAutoFireCompat() {
    }

    static boolean requiresCharge(GunData gunData, FireMode mode) {
        Method getter = ChargeApi.GET_CHARGE_DATA;
        if (getter == null) return false;

        try {
            return getter.invoke(gunData, mode) != null;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            // Use the native trigger when the gun's charge requirements are unknown.
            return true;
        }
    }

    private static final class ChargeApi {
        private static final Method GET_CHARGE_DATA = findChargeDataGetter();

        private static Method findChargeDataGetter() {
            try {
                return GunData.class.getMethod("getChargeData", FireMode.class);
            } catch (NoSuchMethodException | LinkageError | SecurityException exception) {
                return null;
            }
        }
    }

    static boolean setControllerShootDown(boolean down) {
        if (CONTROLLER_SHOOT_TICK == null) return false;

        try {
            CONTROLLER_SHOOT_TICK.invoke(null, down);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
            return false;
        }
    }

    static Method findControllerShootTick(Class<?> shootKeyClass) {
        try {
            Method method = shootKeyClass.getMethod("shootControllerTick", boolean.class);
            return Modifier.isStatic(method.getModifiers()) && method.getReturnType() == boolean.class
                    ? method
                    : null;
        } catch (NoSuchMethodException | SecurityException exception) {
            return null;
        }
    }

    private static Method findRuntimeControllerShootTick() {
        try {
            Class<?> shootKeyClass = Class.forName(
                    "com.tacz.guns.client.input.ShootKey",
                    false,
                    TaczAutoFireCompat.class.getClassLoader()
            );
            return findControllerShootTick(shootKeyClass);
        } catch (ClassNotFoundException | LinkageError | SecurityException exception) {
            return null;
        }
    }
}
