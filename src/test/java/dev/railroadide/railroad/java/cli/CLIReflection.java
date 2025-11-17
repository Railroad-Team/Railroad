package dev.railroadide.railroad.java.cli;

import java.lang.reflect.Field;

/**
 * Utility class for accessing private fields of CLI builder classes using reflection for testing purposes.
 */
final class CLIReflection {
    private CLIReflection() {
    }

    static <T> T readField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Failed to access field " + fieldName, exception);
        }
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        throw new NoSuchFieldException(fieldName);
    }
}
