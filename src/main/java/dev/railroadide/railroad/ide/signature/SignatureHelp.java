package dev.railroadide.railroad.ide.signature;

import java.util.List;

/**
 * Immutable snapshot describing a method or constructor signature along with
 * information about the active parameter at the caret.
 */
public record SignatureHelp(
    String ownerQualified,
    String ownerDisplay,
    String methodName,
    List<ParameterInfo> parameters,
    int activeParameter,
    boolean constructor,
    String returnType,
    boolean varargs
) {
    /**
     * Immutable descriptor for a single parameter in a signature.
     */
    public record ParameterInfo(String type, String name, boolean varargs) {
    }
}
