package alku.taczai.aimbot;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class TaczAutoFireCompat {
    private static final Method CONTROLLER_SHOOT_TICK = findRuntimeControllerShootTick();

    private TaczAutoFireCompat() {
    }

    static boolean shouldReleaseAfterRecordedShot(
            boolean continuousFireMode,
            long previousTimestamp,
            long currentTimestamp
    ) {
        return !continuousFireMode && currentTimestamp != previousTimestamp;
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
