package io.github.railroad.utility;

public class ClassNameValidator {
    public static boolean isValid(String className) {
        return className.matches("^[a-zA-Z_$][a-zA-Z\\d_$]*$");
    }
}
