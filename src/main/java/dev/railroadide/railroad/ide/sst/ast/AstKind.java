package dev.railroadide.railroad.ide.sst.ast;

public enum AstKind {
    // Program structure
    COMPILATION_UNIT,
    PACKAGE_DECLARATION,
    IMPORT_DECLARATION,

    // Java 9+ Program structure
    MODULAR_COMPILATION_UNIT,
    REQUIRES_DIRECTIVE,
    EXPORTS_DIRECTIVE,
    OPENS_DIRECTIVE,
    USES_DIRECTIVE,
    PROVIDES_DIRECTIVE,

    // Class Declarations
    CLASS_DECLARATION,
    ENUM_DECLARATION,
    RECORD_DECLARATION, // Java 14+ (preview), Java 16+ (standard)
    RECORD_COMPONENT, // Java 14+ (preview), Java 16+ (standard)
    INTERFACE_DECLARATION,
    ANNOTATION_TYPE_DECLARATION,
    ANNOTATION_ELEMENT, // e.g., value = "example" in @MyAnnotation(value = "example")
    EMPTY_TYPE_DECLARATION, // e.g. ';'
    ANONYMOUS_CLASS_DECLARATION, // e.g., new MyClass() { ... }

    // Member Declarations
    FIELD_DECLARATION,
    METHOD_DECLARATION,
    CONSTRUCTOR_DECLARATION,
    COMPACT_CONSTRUCTOR_DECLARATION, // (Records) Java 14+ (preview), Java 16+ (standard)
    ENUM_CONSTANT_DECLARATION,
    ANNOTATION_TYPE_MEMBER_DECLARATION,

    // Initializers
    STATIC_INITIALIZER_BLOCK,
    INSTANCE_INITIALIZER_BLOCK,

    // Statements
    BLOCK_STATEMENT,
    EMPTY_STATEMENT, // e.g. ;
    LABELED_STATEMENT, // e.g. label: while(condition) { ... }
    EXPRESSION_STATEMENT,
    LOCAL_VARIABLE_DECLARATION_STATEMENT,
    IF_STATEMENT,
    SWITCH_STATEMENT,
    SWITCH_RULE, // case ... ->, default ->, etc.
    CASE_LABEL,
    DEFAULT_LABEL,
    CASE_CONSTANT,
    CASE_PATTERN, // Java 17+ (standard) TODO: Check version
    CASE_PATTERN_GUARD, // Java 17+ (standard) TODO: Check version

    CASE_NULL,
    WHILE_STATEMENT,
    DO_WHILE_STATEMENT,
    BASIC_FOR_STATEMENT,
    ENHANCED_FOR_STATEMENT,
    BREAK_STATEMENT,
    CONTINUE_STATEMENT,
    RETURN_STATEMENT,
    THROW_STATEMENT,
    TRY_STATEMENT,
    CATCH_CLAUSE,
    FINALLY_CLAUSE,
    SYNCHRONIZED_STATEMENT,
    ASSERT_STATEMENT,
    YIELD_STATEMENT, // Java 13+ (preview), Java 14+ (standard)

    // Expressions
    ASSIGNMENT_EXPRESSION,
    CONDITIONAL_EXPRESSION,
    LAMBDA_EXPRESSION, // Java 8+
    METHOD_INVOCATION_EXPRESSION,
    METHOD_REFERENCE_EXPRESSION, // Java 8+
    OBJECT_CREATION_EXPRESSION,
    ARRAY_CREATION_EXPRESSION,
    ARRAY_INITIALIZER,
    ARRAY_ACCESS_EXPRESSION,
    FIELD_ACCESS_EXPRESSION,
    THIS_EXPRESSION,
    SUPER_EXPRESSION,
    PARENTHESIZED_EXPRESSION,
    TYPE_CAST_EXPRESSION,
    INSTANCEOF_EXPRESSION,
    BINARY_EXPRESSION,
    UNARY_EXPRESSION,
    SWITCH_EXPRESSION, // Java 12+ (preview), Java 14+ (standard)

    // Patterns
    TYPE_TEST_PATTERN, // instanceof with pattern matching (Java 14+ preview, Java 16+ standard)
    RECORD_PATTERN, // Java 14+ (preview), Java 16+ (standard)
    MATCH_ALL_PATTERN, // Java 23+ (TODO: Check version)

    // Type Declarations
    // TODO: Look at what version some of these were introduced
    PRIMITIVE_TYPE, // e.g., int, boolean
    ARRAY_TYPE, // e.g., int[], String[]
    CLASS_OR_INTERFACE_TYPE, // e.g., List<String>
    CLASS_OR_INTERFACE_TYPE_PART,
    TYPE_VARIABLE, // <T>
    INTERSECTION_TYPE, // A & B
    UNION_TYPE, // A | B
    WILDCARD_TYPE, // ? extends A, ? super B, or just ?
    EXCEPTION_TYPE, // CLASS_OR_INTERFACE_TYPE or TYPE_VARIABLE
    SUGAR_TYPE, // e.g., final @Deprecated Integer
    TYPE_DIAMOND, // e.g., <>

    // Modifiers
    MODIFIER, // e.g., public, private, protected, static, final, abstract
    MARKER_ANNOTATION, // e.g., @Override, @Deprecated
    SINGLE_MEMBER_ANNOTATION, // e.g., @SuppressWarnings("unchecked")
    NORMAL_ANNOTATION, // e.g., @MyAnnotation(value = "example")
    ELEMENT_VALUE_ARRAY, // e.g., { "value1", "value2" } in annotations

    // Names and Miscellaneous
    NAME,
    PARAMETER,
    RECEIVER_PARAMETER, // Java 8+ (standard)
    TYPE_PARAMETER,
    THROWS_CLAUSE,
    VARIABLE_DECLARATOR,
    TOKEN, // Generic token node to wrap tokens like operators, punctuation, keywords
    WHITESPACE, // Whitespace node to preserve formatting
    LINE_COMMENT,
    BLOCK_COMMENT,
    JAVADOC_COMMENT,

    // Literals
    INTEGER_LITERAL,
    FLOATING_POINT_LITERAL,
    BOOLEAN_LITERAL,
    CHARACTER_LITERAL,
    STRING_LITERAL,
    NULL_LITERAL,
    CLASS_LITERAL // e.g., String.class
}
