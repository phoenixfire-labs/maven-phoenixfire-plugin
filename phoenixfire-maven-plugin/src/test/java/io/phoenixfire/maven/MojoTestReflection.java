package io.phoenixfire.maven;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Reflection helpers shared by mojo unit tests. */
final class MojoTestReflection {

    private MojoTestReflection() {
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field f = findField(target.getClass(), name);
        f.setAccessible(true);
        f.set(target, value);
    }

    static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void invokeHandleResult(PhoenixfireTestMojo mojo, Object summary) throws Exception {
        invokeProtected(mojo, "handleResult", summary);
    }

    static void invokeHandleResult(PhoenixfireIntegrationTestMojo mojo, Object summary) throws Exception {
        invokeProtected(mojo, "handleResult", summary);
    }

    static Object invokeProtected(Object target, String method, Object arg) throws Exception {
        Method m = findMethod(target.getClass(), method, arg.getClass());
        m.setAccessible(true);
        try {
            return m.invoke(target, arg);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    static Object invokeProtected(Object target, String method) throws Exception {
        Method m = findMethod(target.getClass(), method);
        m.setAccessible(true);
        try {
            return m.invoke(target);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw e;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        Class<?> c = type;
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}
