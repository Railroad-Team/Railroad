package dev.railroadide.railroad.utility;

public final class ClassNameValidator {
    private ClassNameValidator() {

    }
    public static boolean isValid(String className) {
        return className.matches("^[a-zA-Z_$][a-zA-Z\\d_$]*$");
    }
}
