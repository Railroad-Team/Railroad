package dev.railroadide.railroad.ide.signature;

import java.util.List;

/**
 * Immutable snapshot describing a method or constructor signature along with
 * information about the active parameter at the caret.
 *
 * @param ownerQualified fully qualified name of the declaring type
 * @param ownerDisplay declaring type name formatted for display
 * @param methodName method or constructor name
 * @param parameters formal parameters in declaration order
 * @param activeParameter zero-based index of the argument at the caret
 * @param constructor whether the signature describes a constructor
 * @param returnType return type formatted for display
 * @param varargs whether variable-arity arguments are accepted
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
     *
     * @param type type represented in the signature or binding
     * @param name parameter name
     * @param varargs whether variable-arity arguments are accepted
     */
    public record ParameterInfo(String type, String name, boolean varargs) {
    }
}
