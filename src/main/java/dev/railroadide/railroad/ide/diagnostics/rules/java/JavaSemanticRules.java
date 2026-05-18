package dev.railroadide.railroad.ide.diagnostics.rules.java;

import dev.railroadide.railroad.ide.sst.semantic.api.SemanticDiagnostic;

/**
 * Built-in Java semantic rule catalog.
 */
public final class JavaSemanticRules {
    public static final JavaSemanticRule DUPLICATE_DECLARATION = new JavaSemanticRule(
        "SEM_DUPLICATE_DECLARATION",
        SemanticDiagnostic.Severity.ERROR,
        "Duplicate declaration for '%s'"
    );
    public static final JavaSemanticRule UNRESOLVED_NAME = new JavaSemanticRule(
        "SEM_UNRESOLVED_NAME",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot resolve name '%s'"
    );
    public static final JavaSemanticRule UNRESOLVED_IMPORT = new JavaSemanticRule(
        "SEM_UNRESOLVED_IMPORT",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot resolve import '%s'"
    );
    public static final JavaSemanticRule DUPLICATE_IMPORT = new JavaSemanticRule(
        "SEM_DUPLICATE_IMPORT",
        SemanticDiagnostic.Severity.WARNING,
        "Duplicate import '%s'"
    );
    public static final JavaSemanticRule AMBIGUOUS_IMPORT = new JavaSemanticRule(
        "SEM_AMBIGUOUS_IMPORT",
        SemanticDiagnostic.Severity.ERROR,
        "Ambiguous import for '%s' between '%s' and '%s'"
    );
    public static final JavaSemanticRule AMBIGUOUS_NAME = new JavaSemanticRule(
        "SEM_AMBIGUOUS_NAME",
        SemanticDiagnostic.Severity.ERROR,
        "Ambiguous name '%s' resolved to multiple candidates"
    );
    public static final JavaSemanticRule UNRESOLVED_TYPE = new JavaSemanticRule(
        "SEM_UNRESOLVED_TYPE",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot resolve type '%s'"
    );
    public static final JavaSemanticRule UNRESOLVED_MEMBER = new JavaSemanticRule(
        "SEM_UNRESOLVED_MEMBER",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot resolve member '%s'"
    );
    public static final JavaSemanticRule UNRESOLVED_CALL = new JavaSemanticRule(
        "SEM_UNRESOLVED_CALL",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot resolve call '%s'"
    );
    public static final JavaSemanticRule INACCESSIBLE_TYPE = new JavaSemanticRule(
        "SEM_INACCESSIBLE_TYPE",
        SemanticDiagnostic.Severity.ERROR,
        "Type '%s' is not accessible from here"
    );
    public static final JavaSemanticRule INACCESSIBLE_MEMBER = new JavaSemanticRule(
        "SEM_INACCESSIBLE_MEMBER",
        SemanticDiagnostic.Severity.ERROR,
        "Member '%s' is not accessible from here"
    );
    public static final JavaSemanticRule INACCESSIBLE_CALL = new JavaSemanticRule(
        "SEM_INACCESSIBLE_CALL",
        SemanticDiagnostic.Severity.ERROR,
        "Call '%s' is not accessible from here"
    );
    public static final JavaSemanticRule INVALID_INHERITANCE = new JavaSemanticRule(
        "SEM_INVALID_INHERITANCE",
        SemanticDiagnostic.Severity.ERROR,
        "Invalid inheritance: %s"
    );
    public static final JavaSemanticRule MISSING_IMPLEMENTATION = new JavaSemanticRule(
        "SEM_MISSING_IMPLEMENTATION",
        SemanticDiagnostic.Severity.ERROR,
        "Type is missing implementation for '%s'"
    );
    public static final JavaSemanticRule INVALID_OVERRIDE = new JavaSemanticRule(
        "SEM_INVALID_OVERRIDE",
        SemanticDiagnostic.Severity.ERROR,
        "Invalid override for '%s'"
    );
    public static final JavaSemanticRule ILLEGAL_MODIFIER = new JavaSemanticRule(
        "SEM_ILLEGAL_MODIFIER",
        SemanticDiagnostic.Severity.ERROR,
        "Illegal modifier usage: %s"
    );
    public static final JavaSemanticRule INVALID_CONTROL_FLOW = new JavaSemanticRule(
        "SEM_INVALID_CONTROL_FLOW",
        SemanticDiagnostic.Severity.ERROR,
        "Invalid control flow: %s"
    );
    public static final JavaSemanticRule MISSING_RETURN = new JavaSemanticRule(
        "SEM_MISSING_RETURN",
        SemanticDiagnostic.Severity.ERROR,
        "Missing return: %s"
    );
    public static final JavaSemanticRule INCOMPATIBLE_ASSIGNMENT = new JavaSemanticRule(
        "SEM_INCOMPATIBLE_ASSIGNMENT",
        SemanticDiagnostic.Severity.ERROR,
        "Cannot assign '%s' to '%s'"
    );
    public static final JavaSemanticRule UNCAUGHT_CHECKED_EXCEPTION = new JavaSemanticRule(
        "SEM_UNCAUGHT_CHECKED_EXCEPTION",
        SemanticDiagnostic.Severity.ERROR,
        "Unhandled checked exception '%s'"
    );
    public static final JavaSemanticRule UNREACHABLE_CATCH = new JavaSemanticRule(
        "SEM_UNREACHABLE_CATCH",
        SemanticDiagnostic.Severity.ERROR,
        "Unreachable catch for '%s'"
    );
    public static final JavaSemanticRule INVALID_EXCEPTION_TYPE = new JavaSemanticRule(
        "SEM_INVALID_EXCEPTION_TYPE",
        SemanticDiagnostic.Severity.ERROR,
        "Invalid exception type '%s'"
    );
    public static final JavaSemanticRule UNASSIGNED_VARIABLE = new JavaSemanticRule(
        "SEM_UNASSIGNED_VARIABLE",
        SemanticDiagnostic.Severity.ERROR,
        "Variable '%s' might not have been initialized"
    );
    public static final JavaSemanticRule ILLEGAL_FINAL_ASSIGNMENT = new JavaSemanticRule(
        "SEM_ILLEGAL_FINAL_ASSIGNMENT",
        SemanticDiagnostic.Severity.ERROR,
        "Final variable '%s' cannot be assigned here"
    );
    public static final JavaSemanticRule UNINITIALIZED_FINAL_FIELD = new JavaSemanticRule(
        "SEM_UNINITIALIZED_FINAL_FIELD",
        SemanticDiagnostic.Severity.ERROR,
        "Final field '%s' might not be initialized"
    );
    public static final JavaSemanticRule WILDCARD_IMPORT = new JavaSemanticRule(
        "SEM_WILDCARD_IMPORT",
        SemanticDiagnostic.Severity.INFO,
        "Avoid wildcard import '%s'"
    );
    public static final JavaSemanticRule EMPTY_CATCH = new JavaSemanticRule(
        "SEM_EMPTY_CATCH",
        SemanticDiagnostic.Severity.WARNING,
        "Empty 'catch' block"
    );
    public static final JavaSemanticRule PUBLIC_CLASS_NOT_NAMED_AFTER_FILE = new JavaSemanticRule(
        "SEM_PUBLIC_CLASS_NOT_NAMED_AFTER_FILE",
        SemanticDiagnostic.Severity.ERROR,
        "Public class '%s' must be declared in a file named '%s.java'"
    );
    public static final JavaSemanticRule LOWERCASE_CLASS_NAME = new JavaSemanticRule(
        "SEM_LOWERCASE_CLASS_NAME",
        SemanticDiagnostic.Severity.INFO,
        "Class '%s' should be named in PascalCase"
    );
    public static final JavaSemanticRule METHOD_NAMED_TODO = new JavaSemanticRule(
        "SEM_METHOD_NAMED_TODO",
        SemanticDiagnostic.Severity.INFO,
        "Method is named 'TODO', consider giving it a meaningful name"
    );
    public static final JavaSemanticRule METHOD_NAMED_UNDERSCORE = new JavaSemanticRule(
        "SEM_METHOD_NAMED_UNDERSCORE",
        SemanticDiagnostic.Severity.ERROR,
        "Method is named '_', which is not allowed"
    );
    public static final JavaSemanticRule EMPTY_SYNCHRONIZED = new JavaSemanticRule(
        "SEM_EMPTY_SYNCHRONIZED",
        SemanticDiagnostic.Severity.WARNING,
        "Empty 'synchronized' block"
    );
    public static final JavaSemanticRule EMPTY_SWITCH = new JavaSemanticRule(
        "SEM_EMPTY_SWITCH",
        SemanticDiagnostic.Severity.WARNING,
        "Empty 'switch' statement"
    );
    public static final JavaSemanticRule USELESS_DEFAULT_IN_SWITCH = new JavaSemanticRule(
        "SEM_USELESS_DEFAULT_IN_SWITCH",
        SemanticDiagnostic.Severity.WARNING,
        "'switch' statement has only a 'default' case, making it redundant"
    );
    public static final JavaSemanticRule SINGLE_LETTER_FIELD_NAME = new JavaSemanticRule(
        "SEM_SINGLE_LETTER_FIELD_NAME",
        SemanticDiagnostic.Severity.INFO,
        "Field '%s' has a single-letter name, consider giving it a more descriptive name"
    );
    public static final JavaSemanticRule FIELD_NAME_SAME_AS_CLASS_NAME = new JavaSemanticRule(
        "SEM_FIELD_NAME_SAME_AS_CLASS_NAME",
        SemanticDiagnostic.Severity.WARNING,
        "Field '%s' has the same name as its containing class '%s', which can be confusing"
    );
    public static final JavaSemanticRule PARAMETER_NAME_UNDERSCORE = new JavaSemanticRule(
        "SEM_PARAMETER_NAME_UNDERSCORE",
        SemanticDiagnostic.Severity.ERROR,
        "Parameter is named '_', which is not allowed"
    );
    public static final JavaSemanticRule UNREACHABLE_CODE = new JavaSemanticRule(
        "SEM_UNREACHABLE_CODE",
        SemanticDiagnostic.Severity.ERROR,
        "Unreachable statement"
    );
    public static final JavaSemanticRule INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD = new JavaSemanticRule(
        "SEM_INTERFACE_METHOD_CLASHES_WITH_OBJECT_METHOD",
        SemanticDiagnostic.Severity.ERROR,
        "Interface method '%s' clashes with method in 'java.lang.Object'"
    );
    public static final JavaSemanticRule OVERLY_STRONG_TYPE_CAST = new JavaSemanticRule(
        "SEM_OVERLY_STRONG_TYPE_CAST",
        SemanticDiagnostic.Severity.WARNING,
        "Cast to '%s' is stronger than necessary; '%s' is sufficient"
    );
    public static final JavaSemanticRule PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE = new JavaSemanticRule(
        "SEM_PUBLIC_METHOD_NOT_EXPOSED_BY_INTERFACE",
        SemanticDiagnostic.Severity.WARNING,
        "Public method '%s' is not exposed through any implemented interface"
    );
    public static final JavaSemanticRule CAST_CONFLICTING_WITH_INSTANCEOF = new JavaSemanticRule(
        "SEM_CAST_CONFLICTING_WITH_INSTANCEOF",
        SemanticDiagnostic.Severity.WARNING,
        "Cast to '%s' conflicts with previous 'instanceof' check for '%s'"
    );
    public static final JavaSemanticRule ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT = new JavaSemanticRule(
        "SEM_ASSERTION_CAN_BE_REPLACED_WITH_IF_STATEMENT",
        SemanticDiagnostic.Severity.INFO,
        "Assertion can be replaced with an 'if' statement for better error handling"
    );
    public static final JavaSemanticRule FEATURE_ENVY_MANIPULATE = new JavaSemanticRule(
        "SEM_FEATURE_ENVY_MANIPULATE",
        SemanticDiagnostic.Severity.INFO,
        "Feature envy: '%s' mostly manipulates '%s' instead of its own data. Consider moving the work into '%s'."
    );
    public static final JavaSemanticRule FEATURE_ENVY_TIGHTLY_COUPLED = new JavaSemanticRule(
        "SEM_FEATURE_ENVY_TIGHTLY_COUPLED",
        SemanticDiagnostic.Severity.INFO,
        "Feature envy: '%s' is tightly coupled to '%s's data; delegate the logic to '%s' or a DTO."
    );
    public static final JavaSemanticRule OVERRIDABLE_METHOD_DURING_CONSTRUCTION = new JavaSemanticRule(
        "SEM_OVERRIDABLE_METHOD_DURING_CONSTRUCTION",
        SemanticDiagnostic.Severity.WARNING,
        "Constructor calls overridable method '%s'."
    );
    public static final JavaSemanticRule OVERRIDDEN_METHOD_DURING_CONSTRUCTION = new JavaSemanticRule(
        "SEM_OVERRIDDEN_METHOD_DURING_CONSTRUCTION",
        SemanticDiagnostic.Severity.WARNING,
        "Constructor calls overridden method '%s'."
    );
    public static final JavaSemanticRule ASSERTION_WITH_SIDE_EFFECTS = new JavaSemanticRule(
        "SEM_ASSERTION_WITH_SIDE_EFFECTS",
        SemanticDiagnostic.Severity.WARNING,
        "Assertion contains side effects, which may not be executed if assertions are disabled"
    );
    public static final JavaSemanticRule DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE = new JavaSemanticRule(
        "SEM_DISALLOWED_EXCEPTION_IN_METHOD_SIGNATURE",
        SemanticDiagnostic.Severity.ERROR,
        "Method '%s' declares disallowed exception '%s' in its signature"
    );
    public static final JavaSemanticRule THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION = new JavaSemanticRule(
        "SEM_THIS_REFERENCE_ESCAPED_OBJECT_CONSTRUCTION",
        SemanticDiagnostic.Severity.WARNING,
        "Constructor leaks 'this' reference via '%s' before the object is fully constructed"
    );
    public static final JavaSemanticRule NEGATIVE_HEX_INT_IN_LONG_CONTEXT = new JavaSemanticRule(
        "SEM_NEGATIVE_HEX_INT_IN_LONG_CONTEXT",
        SemanticDiagnostic.Severity.WARNING,
        "Negative hexadecimal integer literal '%s' in long context may be misinterpreted as a negative integer literal"
    );
    public static final JavaSemanticRule FIELD_CAN_BE_LOCAL_VARIABLE = new JavaSemanticRule(
        "SEM_FIELD_CAN_BE_LOCAL_VARIABLE",
        SemanticDiagnostic.Severity.WARNING,
        "Field '%s' can be converted to a local variable"
    );
    public static final JavaSemanticRule INTERFACE_SHOULD_BE_FUNCTIONAL = new JavaSemanticRule(
        "SEM_INTERFACE_SHOULD_BE_FUNCTIONAL",
        SemanticDiagnostic.Severity.INFO,
        "Interface '%s' may be annotated as '@FunctionalInterface'"
    );
    public static final JavaSemanticRule CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL = new JavaSemanticRule(
        "SEM_CONSTANT_CONDITIONAL_EXPRESSION_HARDCODED_LITERAL",
        SemanticDiagnostic.Severity.WARNING,
        "'%s' condition is always '%s' due to hardcoded literal"
    );
    public static final JavaSemanticRule CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT = new JavaSemanticRule(
        "SEM_CONSTANT_CONDITIONAL_EXPRESSION_COMPILE_TIME_CONSTANT",
        SemanticDiagnostic.Severity.WARNING,
        "'%s' condition is always '%s' due to compile-time constant evaluation"
    );
    public static final JavaSemanticRule CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT = new JavaSemanticRule(
        "SEM_CONSTANT_CONDITIONAL_EXPRESSION_DATA_FLOW_CONSTANT",
        SemanticDiagnostic.Severity.WARNING,
        "'%s' is known to be '%s' due to data flow analysis, making the condition always '%s'"
    );
    public static final JavaSemanticRule OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK = new JavaSemanticRule(
        "SEM_OPTIONAL_GET_WITHOUT_IS_PRESENT_CHECK",
        SemanticDiagnostic.Severity.WARNING,
        "Call to 'Optional.get()' without preceding 'isPresent()' check may throw 'NoSuchElementException'"
    );
    public static final JavaSemanticRule INFINITE_RECURSION = new JavaSemanticRule(
        "SEM_INFINITE_RECURSION",
        SemanticDiagnostic.Severity.WARNING,
        "Method '%s' is recursively called without a base case, leading to infinite recursion"
    );
    public static final JavaSemanticRule AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES = new JavaSemanticRule(
        "SEM_AUTO_CLOSEABLE_WITHOUT_TRY_WITH_RESOURCES",
        SemanticDiagnostic.Severity.WARNING,
        "'%s' implements 'AutoCloseable' but is not used in a try-with-resources statement"
    );
    public static final JavaSemanticRule FALLTHROUGH_CASE_IN_SWITCH = new JavaSemanticRule(
        "SEM_FALLTHROUGH_CASE_IN_SWITCH",
        SemanticDiagnostic.Severity.WARNING,
        "Case '%s' in 'switch' statement falls through to the next case without a break or return statement"
    );
    public static final JavaSemanticRule INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT = new JavaSemanticRule(
        "SEM_INTEGER_DIVISION_IN_FLOATING_POINT_CONTEXT",
        SemanticDiagnostic.Severity.WARNING,
        "Integer division in floating-point context may lead to loss of precision"
    );
    public static final JavaSemanticRule BIG_DECIMAL_EQUALS = new JavaSemanticRule(
        "SEM_BIG_DECIMAL_EQUALS",
        SemanticDiagnostic.Severity.WARNING,
        "'BigDecimal.equals()' is scale-sensitive; consider 'compareTo() == 0' if numeric equality is intended"
    );
    public static final JavaSemanticRule SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR = new JavaSemanticRule(
        "SEM_SERIALIZABLE_CLASS_WITH_UNCONSTRUCTABLE_ANCESTOR",
        SemanticDiagnostic.Severity.WARNING,
        "Serializable type '%s' cannot be deserialized because non-serializable ancestor '%s' has no accessible no-arg constructor"
    );
    public static final JavaSemanticRule REDUNDANT_INTERFACE_DECLARATION = new JavaSemanticRule(
        "SEM_REDUNDANT_INTERFACE_DECLARATION",
        SemanticDiagnostic.Severity.INFO,
        "Type '%s' declares redundant interface '%s'"
    );

    private JavaSemanticRules() {
    }
}
