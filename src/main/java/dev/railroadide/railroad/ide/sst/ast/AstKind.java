package dev.railroadide.railroad.ide.sst.ast;

/**
 * Identifies the grammatical construct represented by an AST node.
 */
public enum AstKind {
    // Program structure
    /**
     * Identifies compilation unit nodes.
     */
    COMPILATION_UNIT,
    /**
     * Identifies package declaration nodes.
     */
    PACKAGE_DECLARATION,
    /**
     * Identifies import declaration nodes.
     */
    IMPORT_DECLARATION,

    // Java 9+ Program structure
    /**
     * Identifies modular compilation unit nodes.
     */
    MODULAR_COMPILATION_UNIT,
    /**
     * Identifies requires directive nodes.
     */
    REQUIRES_DIRECTIVE,
    /**
     * Identifies exports directive nodes.
     */
    EXPORTS_DIRECTIVE,
    /**
     * Identifies opens directive nodes.
     */
    OPENS_DIRECTIVE,
    /**
     * Identifies uses directive nodes.
     */
    USES_DIRECTIVE,
    /**
     * Identifies provides directive nodes.
     */
    PROVIDES_DIRECTIVE,

    // Class Declarations
    /**
     * Identifies class declaration nodes.
     */
    CLASS_DECLARATION,
    /**
     * Identifies enum declaration nodes.
     */
    ENUM_DECLARATION,
    /**
     * Identifies record declaration nodes.
     */
    RECORD_DECLARATION, // Java 14+ (preview), Java 16+ (standard)
    /**
     * Identifies record component nodes.
     */
    RECORD_COMPONENT, // Java 14+ (preview), Java 16+ (standard)
    /**
     * Identifies interface declaration nodes.
     */
    INTERFACE_DECLARATION,
    /**
     * Identifies annotation type declaration nodes.
     */
    ANNOTATION_TYPE_DECLARATION,
    /**
     * Identifies annotation element nodes.
     */
    ANNOTATION_ELEMENT, // e.g., value = "example" in
                        // @MyAnnotation(value = "example")
    /**
     * Identifies empty type declaration nodes.
     */
    EMPTY_TYPE_DECLARATION, // e.g. ';'
    /**
     * Identifies anonymous class declaration nodes.
     */
    ANONYMOUS_CLASS_DECLARATION, // e.g., new MyClass() { ... }

    // Member Declarations
    /**
     * Identifies field declaration nodes.
     */
    FIELD_DECLARATION,
    /**
     * Identifies method declaration nodes.
     */
    METHOD_DECLARATION,
    /**
     * Identifies constructor declaration nodes.
     */
    CONSTRUCTOR_DECLARATION,
    /**
     * Identifies compact constructor declaration nodes.
     */
    COMPACT_CONSTRUCTOR_DECLARATION, // (Records) Java
                                     // 14+ (preview),
                                     // Java 16+
                                     // (standard)
    /**
     * Identifies enum constant declaration nodes.
     */
    ENUM_CONSTANT_DECLARATION,
    /**
     * Identifies annotation type member declaration nodes.
     */
    ANNOTATION_TYPE_MEMBER_DECLARATION,

    // Initializers
    /**
     * Identifies static initializer block nodes.
     */
    STATIC_INITIALIZER_BLOCK,
    /**
     * Identifies instance initializer block nodes.
     */
    INSTANCE_INITIALIZER_BLOCK,

    // Statements
    /**
     * Identifies block statement nodes.
     */
    BLOCK_STATEMENT,
    /**
     * Identifies empty statement nodes.
     */
    EMPTY_STATEMENT, // e.g. ;
    /**
     * Identifies labeled statement nodes.
     */
    LABELED_STATEMENT, // e.g. label: while(condition) { ... }
    /**
     * Identifies expression statement nodes.
     */
    EXPRESSION_STATEMENT,
    /**
     * Identifies local variable declaration statement nodes.
     */
    LOCAL_VARIABLE_DECLARATION_STATEMENT,
    /**
     * Identifies if statement nodes.
     */
    IF_STATEMENT,
    /**
     * Identifies switch statement nodes.
     */
    SWITCH_STATEMENT,
    /**
     * Identifies switch rule nodes.
     */
    SWITCH_RULE, // case ...
                 // ->,
                 // default
                 // ->, etc.
    /**
     * Identifies case label nodes.
     */
    CASE_LABEL,
    /**
     * Identifies default label nodes.
     */
    DEFAULT_LABEL,
    /**
     * Identifies case constant nodes.
     */
    CASE_CONSTANT,
    /**
     * Identifies case pattern nodes.
     */
    CASE_PATTERN, // Java 17+ (standard) TODO: Check version
    /**
     * Identifies case pattern guard nodes.
     */
    CASE_PATTERN_GUARD, // Java 17+ (standard) TODO: Check version

    /**
     * Identifies case null nodes.
     */
    CASE_NULL,
    /**
     * Identifies while statement nodes.
     */
    WHILE_STATEMENT,
    /**
     * Identifies do while statement nodes.
     */
    DO_WHILE_STATEMENT,
    /**
     * Identifies basic for statement nodes.
     */
    BASIC_FOR_STATEMENT,
    /**
     * Identifies enhanced for statement nodes.
     */
    ENHANCED_FOR_STATEMENT,
    /**
     * Identifies break statement nodes.
     */
    BREAK_STATEMENT,
    /**
     * Identifies continue statement nodes.
     */
    CONTINUE_STATEMENT,
    /**
     * Identifies return statement nodes.
     */
    RETURN_STATEMENT,
    /**
     * Identifies throw statement nodes.
     */
    THROW_STATEMENT,
    /**
     * Identifies try statement nodes.
     */
    TRY_STATEMENT,
    /**
     * Identifies catch clause nodes.
     */
    CATCH_CLAUSE,
    /**
     * Identifies finally clause nodes.
     */
    FINALLY_CLAUSE,
    /**
     * Identifies synchronized statement nodes.
     */
    SYNCHRONIZED_STATEMENT,
    /**
     * Identifies assert statement nodes.
     */
    ASSERT_STATEMENT,
    /**
     * Identifies yield statement nodes.
     */
    YIELD_STATEMENT, // Java
                     // 13+
                     // (preview),
                     // Java
                     // 14+
                     // (standard)

    // Expressions
    /**
     * Identifies assignment expression nodes.
     */
    ASSIGNMENT_EXPRESSION,
    /**
     * Identifies conditional expression nodes.
     */
    CONDITIONAL_EXPRESSION,
    /**
     * Identifies lambda expression nodes.
     */
    LAMBDA_EXPRESSION, // Java 8+
    /**
     * Identifies method invocation expression nodes.
     */
    METHOD_INVOCATION_EXPRESSION,
    /**
     * Identifies method reference expression nodes.
     */
    METHOD_REFERENCE_EXPRESSION, // Java 8+
    /**
     * Identifies object creation expression nodes.
     */
    OBJECT_CREATION_EXPRESSION,
    /**
     * Identifies array creation expression nodes.
     */
    ARRAY_CREATION_EXPRESSION,
    /**
     * Identifies array initializer nodes.
     */
    ARRAY_INITIALIZER,
    /**
     * Identifies array access expression nodes.
     */
    ARRAY_ACCESS_EXPRESSION,
    /**
     * Identifies field access expression nodes.
     */
    FIELD_ACCESS_EXPRESSION,
    /**
     * Identifies this expression nodes.
     */
    THIS_EXPRESSION,
    /**
     * Identifies super expression nodes.
     */
    SUPER_EXPRESSION,
    /**
     * Identifies parenthesized expression nodes.
     */
    PARENTHESIZED_EXPRESSION,
    /**
     * Identifies type cast expression nodes.
     */
    TYPE_CAST_EXPRESSION,
    /**
     * Identifies instanceof expression nodes.
     */
    INSTANCEOF_EXPRESSION,
    /**
     * Identifies binary expression nodes.
     */
    BINARY_EXPRESSION,
    /**
     * Identifies unary expression nodes.
     */
    UNARY_EXPRESSION,
    /**
     * Identifies switch expression nodes.
     */
    SWITCH_EXPRESSION, // Java
                       // 12+
                       // (preview),
                       // Java
                       // 14+
                       // (standard)

    // Patterns
    /**
     * Identifies type test pattern nodes.
     */
    TYPE_TEST_PATTERN, // instanceof with pattern matching (Java 14+ preview, Java 16+ standard)
    /**
     * Identifies record pattern nodes.
     */
    RECORD_PATTERN, // Java 14+ (preview), Java 16+ (standard)
    /**
     * Identifies match all pattern nodes.
     */
    MATCH_ALL_PATTERN, // Java 23+ (TODO: Check version)

    // Type Declarations
    // TODO: Look at what version some of these were introduced
    /**
     * Identifies primitive type nodes.
     */
    PRIMITIVE_TYPE, // e.g., int, boolean
    /**
     * Identifies array type nodes.
     */
    ARRAY_TYPE, // e.g., int[], String[]
    /**
     * Identifies class or interface type nodes.
     */
    CLASS_OR_INTERFACE_TYPE, // e.g., List<String>
    /**
     * Identifies class or interface type part nodes.
     */
    CLASS_OR_INTERFACE_TYPE_PART,
    /**
     * Identifies intersection type nodes.
     */
    INTERSECTION_TYPE, // A & B
    /**
     * Identifies union type nodes.
     */
    UNION_TYPE, // A | B
    /**
     * Identifies wildcard type nodes.
     */
    WILDCARD_TYPE, // ? extends A, ? super B, or just ?
    /**
     * Identifies exception type nodes.
     */
    EXCEPTION_TYPE, // CLASS_OR_INTERFACE_TYPE or TYPE_VARIABLE
    /**
     * Identifies sugar type nodes.
     */
    SUGAR_TYPE, // e.g., final @Deprecated Integer
    /**
     * Identifies type diamond nodes.
     */
    TYPE_DIAMOND, // e.g., <>

    // Modifiers
    /**
     * Identifies modifier nodes.
     */
    MODIFIER, // e.g., public, private, protected, static, final, abstract
    /**
     * Identifies marker annotation nodes.
     */
    MARKER_ANNOTATION, // e.g., @Override, @Deprecated
    /**
     * Identifies single member annotation nodes.
     */
    SINGLE_MEMBER_ANNOTATION, // e.g., @SuppressWarnings("unchecked")
    /**
     * Identifies normal annotation nodes.
     */
    NORMAL_ANNOTATION, // e.g., @MyAnnotation(value = "example")
    /**
     * Identifies element value array nodes.
     */
    ELEMENT_VALUE_ARRAY, // e.g., { "value1", "value2" } in annotations

    // Names and Miscellaneous
    /**
     * Identifies name nodes.
     */
    NAME,
    /**
     * Identifies parameter nodes.
     */
    PARAMETER,
    /**
     * Identifies receiver parameter nodes.
     */
    RECEIVER_PARAMETER, // Java 8+ (standard)
    /**
     * Identifies type parameter nodes.
     */
    TYPE_PARAMETER,
    /**
     * Identifies throws clause nodes.
     */
    THROWS_CLAUSE,
    /**
     * Identifies variable declarator nodes.
     */
    VARIABLE_DECLARATOR,
    /**
     * Identifies lambda body nodes.
     */
    LAMBDA_BODY,

    /**
     * Identifies token nodes.
     */
    TOKEN, // Generic token node to wrap tokens like operators, punctuation, keywords
    /**
     * Identifies whitespace nodes.
     */
    WHITESPACE, // Whitespace node to preserve formatting
    /**
     * Identifies line comment nodes.
     */
    LINE_COMMENT,
    /**
     * Identifies block comment nodes.
     */
    BLOCK_COMMENT,
    /**
     * Identifies javadoc comment nodes.
     */
    JAVADOC_COMMENT,

    // Literals
    /**
     * Identifies integer literal nodes.
     */
    INTEGER_LITERAL,
    /**
     * Identifies floating point literal nodes.
     */
    FLOATING_POINT_LITERAL,
    /**
     * Identifies boolean literal nodes.
     */
    BOOLEAN_LITERAL,
    /**
     * Identifies character literal nodes.
     */
    CHARACTER_LITERAL,
    /**
     * Identifies string literal nodes.
     */
    STRING_LITERAL,
    /**
     * Identifies null literal nodes.
     */
    NULL_LITERAL,
    /**
     * Identifies class literal nodes.
     */
    CLASS_LITERAL // e.g.,
                  // String.class
}
