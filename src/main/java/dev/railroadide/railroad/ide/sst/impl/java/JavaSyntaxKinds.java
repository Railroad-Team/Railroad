package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;

import java.util.EnumMap;
import java.util.Map;

/**
 * Java syntax node kinds and mappings from lexer token types to present or missing token kinds.
 */
public final class JavaSyntaxKinds {
    /**
     * Syntax kind for Java compilation unit nodes.
     */
    public static final SyntaxKind COMPILATION_UNIT = SyntaxKind.of("JAVA_COMPILATION_UNIT");
    /**
     * Syntax kind for Java package declaration nodes.
     */
    public static final SyntaxKind PACKAGE_DECLARATION = SyntaxKind.of("JAVA_PACKAGE_DECLARATION");
    /**
     * Syntax kind for Java import declaration nodes.
     */
    public static final SyntaxKind IMPORT_DECLARATION = SyntaxKind.of("JAVA_IMPORT_DECLARATION");
    /**
     * Syntax kind for Java import target nodes.
     */
    public static final SyntaxKind IMPORT_TARGET = SyntaxKind.of("JAVA_IMPORT_TARGET");
    /**
     * Syntax kind for Java module declaration nodes.
     */
    public static final SyntaxKind MODULE_DECLARATION = SyntaxKind.of("JAVA_MODULE_DECLARATION");
    /**
     * Syntax kind for Java module body nodes.
     */
    public static final SyntaxKind MODULE_BODY = SyntaxKind.of("JAVA_MODULE_BODY");
    /**
     * Syntax kind for Java module requires directive nodes.
     */
    public static final SyntaxKind MODULE_REQUIRES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_REQUIRES_DIRECTIVE");
    /**
     * Syntax kind for Java module exports directive nodes.
     */
    public static final SyntaxKind MODULE_EXPORTS_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_EXPORTS_DIRECTIVE");
    /**
     * Syntax kind for Java module opens directive nodes.
     */
    public static final SyntaxKind MODULE_OPENS_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_OPENS_DIRECTIVE");
    /**
     * Syntax kind for Java module uses directive nodes.
     */
    public static final SyntaxKind MODULE_USES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_USES_DIRECTIVE");
    /**
     * Syntax kind for Java module provides directive nodes.
     */
    public static final SyntaxKind MODULE_PROVIDES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_PROVIDES_DIRECTIVE");
    /**
     * Syntax kind for Java module unknown directive nodes.
     */
    public static final SyntaxKind MODULE_UNKNOWN_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_UNKNOWN_DIRECTIVE");
    /**
     * Syntax kind for Java qualified name nodes.
     */
    public static final SyntaxKind QUALIFIED_NAME = SyntaxKind.of("JAVA_QUALIFIED_NAME");
    /**
     * Syntax kind for Java annotation nodes.
     */
    public static final SyntaxKind ANNOTATION = SyntaxKind.of("JAVA_ANNOTATION");
    /**
     * Syntax kind for Java type declaration nodes.
     */
    public static final SyntaxKind TYPE_DECLARATION = SyntaxKind.of("JAVA_TYPE_DECLARATION");
    /**
     * Syntax kind for Java class declaration nodes.
     */
    public static final SyntaxKind CLASS_DECLARATION = SyntaxKind.of("JAVA_CLASS_DECLARATION");
    /**
     * Syntax kind for Java interface declaration nodes.
     */
    public static final SyntaxKind INTERFACE_DECLARATION = SyntaxKind.of("JAVA_INTERFACE_DECLARATION");
    /**
     * Syntax kind for Java enum declaration nodes.
     */
    public static final SyntaxKind ENUM_DECLARATION = SyntaxKind.of("JAVA_ENUM_DECLARATION");
    /**
     * Syntax kind for Java annotation type declaration nodes.
     */
    public static final SyntaxKind ANNOTATION_TYPE_DECLARATION = SyntaxKind.of("JAVA_ANNOTATION_TYPE_DECLARATION");
    /**
     * Syntax kind for Java record declaration nodes.
     */
    public static final SyntaxKind RECORD_DECLARATION = SyntaxKind.of("JAVA_RECORD_DECLARATION");
    /**
     * Syntax kind for Java empty type declaration nodes.
     */
    public static final SyntaxKind EMPTY_TYPE_DECLARATION = SyntaxKind.of("JAVA_EMPTY_TYPE_DECLARATION");
    /**
     * Syntax kind for Java class body nodes.
     */
    public static final SyntaxKind CLASS_BODY = SyntaxKind.of("JAVA_CLASS_BODY");
    /**
     * Syntax kind for Java interface body nodes.
     */
    public static final SyntaxKind INTERFACE_BODY = SyntaxKind.of("JAVA_INTERFACE_BODY");
    /**
     * Syntax kind for Java enum body nodes.
     */
    public static final SyntaxKind ENUM_BODY = SyntaxKind.of("JAVA_ENUM_BODY");
    /**
     * Syntax kind for Java annotation type body nodes.
     */
    public static final SyntaxKind ANNOTATION_TYPE_BODY = SyntaxKind.of("JAVA_ANNOTATION_TYPE_BODY");
    /**
     * Syntax kind for Java record body nodes.
     */
    public static final SyntaxKind RECORD_BODY = SyntaxKind.of("JAVA_RECORD_BODY");
    /**
     * Syntax kind for Java type member nodes.
     */
    public static final SyntaxKind TYPE_MEMBER = SyntaxKind.of("JAVA_TYPE_MEMBER");
    /**
     * Syntax kind for Java annotation type member nodes.
     */
    public static final SyntaxKind ANNOTATION_TYPE_MEMBER = SyntaxKind.of("JAVA_ANNOTATION_TYPE_MEMBER");
    /**
     * Syntax kind for Java record compact constructor nodes.
     */
    public static final SyntaxKind RECORD_COMPACT_CONSTRUCTOR = SyntaxKind.of("JAVA_RECORD_COMPACT_CONSTRUCTOR");
    /**
     * Syntax kind for Java enum constant nodes.
     */
    public static final SyntaxKind ENUM_CONSTANT = SyntaxKind.of("JAVA_ENUM_CONSTANT");
    /**
     * Syntax kind for Java field declaration nodes.
     */
    public static final SyntaxKind FIELD_DECLARATION = SyntaxKind.of("JAVA_FIELD_DECLARATION");
    /**
     * Syntax kind for Java variable declarator nodes.
     */
    public static final SyntaxKind VARIABLE_DECLARATOR = SyntaxKind.of("JAVA_VARIABLE_DECLARATOR");
    /**
     * Syntax kind for Java method declaration nodes.
     */
    public static final SyntaxKind METHOD_DECLARATION = SyntaxKind.of("JAVA_METHOD_DECLARATION");
    /**
     * Syntax kind for Java constructor declaration nodes.
     */
    public static final SyntaxKind CONSTRUCTOR_DECLARATION = SyntaxKind.of("JAVA_CONSTRUCTOR_DECLARATION");
    /**
     * Syntax kind for Java parameter list nodes.
     */
    public static final SyntaxKind PARAMETER_LIST = SyntaxKind.of("JAVA_PARAMETER_LIST");
    /**
     * Syntax kind for Java parameter nodes.
     */
    public static final SyntaxKind PARAMETER = SyntaxKind.of("JAVA_PARAMETER");
    /**
     * Syntax kind for Java throws clause nodes.
     */
    public static final SyntaxKind THROWS_CLAUSE = SyntaxKind.of("JAVA_THROWS_CLAUSE");
    /**
     * Syntax kind for Java static initializer nodes.
     */
    public static final SyntaxKind STATIC_INITIALIZER = SyntaxKind.of("JAVA_STATIC_INITIALIZER");
    /**
     * Syntax kind for Java instance initializer nodes.
     */
    public static final SyntaxKind INSTANCE_INITIALIZER = SyntaxKind.of("JAVA_INSTANCE_INITIALIZER");
    /**
     * Syntax kind for Java record header nodes.
     */
    public static final SyntaxKind RECORD_HEADER = SyntaxKind.of("JAVA_RECORD_HEADER");
    /**
     * Syntax kind for Java record component nodes.
     */
    public static final SyntaxKind RECORD_COMPONENT = SyntaxKind.of("JAVA_RECORD_COMPONENT");
    /**
     * Syntax kind for Java type parameters nodes.
     */
    public static final SyntaxKind TYPE_PARAMETERS = SyntaxKind.of("JAVA_TYPE_PARAMETERS");
    /**
     * Syntax kind for Java type arguments nodes.
     */
    public static final SyntaxKind TYPE_ARGUMENTS = SyntaxKind.of("JAVA_TYPE_ARGUMENTS");
    /**
     * Syntax kind for Java type parameter nodes.
     */
    public static final SyntaxKind TYPE_PARAMETER = SyntaxKind.of("JAVA_TYPE_PARAMETER");
    /**
     * Syntax kind for Java type bound nodes.
     */
    public static final SyntaxKind TYPE_BOUND = SyntaxKind.of("JAVA_TYPE_BOUND");
    /**
     * Syntax kind for Java wildcard type nodes.
     */
    public static final SyntaxKind WILDCARD_TYPE = SyntaxKind.of("JAVA_WILDCARD_TYPE");
    /**
     * Syntax kind for Java diamond type arguments nodes.
     */
    public static final SyntaxKind DIAMOND_TYPE_ARGUMENTS = SyntaxKind.of("JAVA_DIAMOND_TYPE_ARGUMENTS");
    /**
     * Syntax kind for Java type reference nodes.
     */
    public static final SyntaxKind TYPE_REFERENCE = SyntaxKind.of("JAVA_TYPE_REFERENCE");
    /**
     * Syntax kind for Java intersection type reference nodes.
     */
    public static final SyntaxKind INTERSECTION_TYPE_REFERENCE = SyntaxKind.of("JAVA_INTERSECTION_TYPE_REFERENCE");
    /**
     * Syntax kind for Java union type reference nodes.
     */
    public static final SyntaxKind UNION_TYPE_REFERENCE = SyntaxKind.of("JAVA_UNION_TYPE_REFERENCE");
    /**
     * Syntax kind for Java array dimension nodes.
     */
    public static final SyntaxKind ARRAY_DIMENSION = SyntaxKind.of("JAVA_ARRAY_DIMENSION");
    /**
     * Syntax kind for Java extends clause nodes.
     */
    public static final SyntaxKind EXTENDS_CLAUSE = SyntaxKind.of("JAVA_EXTENDS_CLAUSE");
    /**
     * Syntax kind for Java implements clause nodes.
     */
    public static final SyntaxKind IMPLEMENTS_CLAUSE = SyntaxKind.of("JAVA_IMPLEMENTS_CLAUSE");
    /**
     * Syntax kind for Java permits clause nodes.
     */
    public static final SyntaxKind PERMITS_CLAUSE = SyntaxKind.of("JAVA_PERMITS_CLAUSE");
    /**
     * Syntax kind for Java statement nodes.
     */
    public static final SyntaxKind STATEMENT = SyntaxKind.of("JAVA_STATEMENT");
    /**
     * Syntax kind for Java block nodes.
     */
    public static final SyntaxKind BLOCK = SyntaxKind.of("JAVA_BLOCK");
    /**
     * Syntax kind for Java empty statement nodes.
     */
    public static final SyntaxKind EMPTY_STATEMENT = SyntaxKind.of("JAVA_EMPTY_STATEMENT");
    /**
     * Syntax kind for Java expression statement nodes.
     */
    public static final SyntaxKind EXPRESSION_STATEMENT = SyntaxKind.of("JAVA_EXPRESSION_STATEMENT");
    /**
     * Syntax kind for Java local variable declaration statement nodes.
     */
    public static final SyntaxKind LOCAL_VARIABLE_DECLARATION_STATEMENT = SyntaxKind
        .of("JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT");
    /**
     * Syntax kind for Java if statement nodes.
     */
    public static final SyntaxKind IF_STATEMENT = SyntaxKind.of("JAVA_IF_STATEMENT");
    /**
     * Syntax kind for Java switch statement nodes.
     */
    public static final SyntaxKind SWITCH_STATEMENT = SyntaxKind.of("JAVA_SWITCH_STATEMENT");
    /**
     * Syntax kind for Java switch rule nodes.
     */
    public static final SyntaxKind SWITCH_RULE = SyntaxKind.of("JAVA_SWITCH_RULE");
    /**
     * Syntax kind for Java switch label nodes.
     */
    public static final SyntaxKind SWITCH_LABEL = SyntaxKind.of("JAVA_SWITCH_LABEL");
    /**
     * Syntax kind for Java switch case item nodes.
     */
    public static final SyntaxKind SWITCH_CASE_ITEM = SyntaxKind.of("JAVA_SWITCH_CASE_ITEM");
    /**
     * Syntax kind for Java pattern guard nodes.
     */
    public static final SyntaxKind PATTERN_GUARD = SyntaxKind.of("JAVA_PATTERN_GUARD");
    /**
     * Syntax kind for Java while statement nodes.
     */
    public static final SyntaxKind WHILE_STATEMENT = SyntaxKind.of("JAVA_WHILE_STATEMENT");
    /**
     * Syntax kind for Java do while statement nodes.
     */
    public static final SyntaxKind DO_WHILE_STATEMENT = SyntaxKind.of("JAVA_DO_WHILE_STATEMENT");
    /**
     * Syntax kind for Java for statement nodes.
     */
    public static final SyntaxKind FOR_STATEMENT = SyntaxKind.of("JAVA_FOR_STATEMENT");
    /**
     * Syntax kind for Java basic for statement nodes.
     */
    public static final SyntaxKind BASIC_FOR_STATEMENT = SyntaxKind.of("JAVA_BASIC_FOR_STATEMENT");
    /**
     * Syntax kind for Java enhanced for statement nodes.
     */
    public static final SyntaxKind ENHANCED_FOR_STATEMENT = SyntaxKind.of("JAVA_ENHANCED_FOR_STATEMENT");
    /**
     * Syntax kind for Java try statement nodes.
     */
    public static final SyntaxKind TRY_STATEMENT = SyntaxKind.of("JAVA_TRY_STATEMENT");
    /**
     * Syntax kind for Java try resource nodes.
     */
    public static final SyntaxKind TRY_RESOURCE = SyntaxKind.of("JAVA_TRY_RESOURCE");
    /**
     * Syntax kind for Java catch clause nodes.
     */
    public static final SyntaxKind CATCH_CLAUSE = SyntaxKind.of("JAVA_CATCH_CLAUSE");
    /**
     * Syntax kind for Java finally clause nodes.
     */
    public static final SyntaxKind FINALLY_CLAUSE = SyntaxKind.of("JAVA_FINALLY_CLAUSE");
    /**
     * Syntax kind for Java synchronized statement nodes.
     */
    public static final SyntaxKind SYNCHRONIZED_STATEMENT = SyntaxKind.of("JAVA_SYNCHRONIZED_STATEMENT");
    /**
     * Syntax kind for Java return statement nodes.
     */
    public static final SyntaxKind RETURN_STATEMENT = SyntaxKind.of("JAVA_RETURN_STATEMENT");
    /**
     * Syntax kind for Java throw statement nodes.
     */
    public static final SyntaxKind THROW_STATEMENT = SyntaxKind.of("JAVA_THROW_STATEMENT");
    /**
     * Syntax kind for Java break statement nodes.
     */
    public static final SyntaxKind BREAK_STATEMENT = SyntaxKind.of("JAVA_BREAK_STATEMENT");
    /**
     * Syntax kind for Java continue statement nodes.
     */
    public static final SyntaxKind CONTINUE_STATEMENT = SyntaxKind.of("JAVA_CONTINUE_STATEMENT");
    /**
     * Syntax kind for Java assert statement nodes.
     */
    public static final SyntaxKind ASSERT_STATEMENT = SyntaxKind.of("JAVA_ASSERT_STATEMENT");
    /**
     * Syntax kind for Java yield statement nodes.
     */
    public static final SyntaxKind YIELD_STATEMENT = SyntaxKind.of("JAVA_YIELD_STATEMENT");
    /**
     * Syntax kind for Java labeled statement nodes.
     */
    public static final SyntaxKind LABELED_STATEMENT = SyntaxKind.of("JAVA_LABELED_STATEMENT");
    /**
     * Syntax kind for Java expression nodes.
     */
    public static final SyntaxKind EXPRESSION = SyntaxKind.of("JAVA_EXPRESSION");
    /**
     * Syntax kind for Java lambda expression nodes.
     */
    public static final SyntaxKind LAMBDA_EXPRESSION = SyntaxKind.of("JAVA_LAMBDA_EXPRESSION");
    /**
     * Syntax kind for Java lambda parameters nodes.
     */
    public static final SyntaxKind LAMBDA_PARAMETERS = SyntaxKind.of("JAVA_LAMBDA_PARAMETERS");
    /**
     * Syntax kind for Java lambda parameter nodes.
     */
    public static final SyntaxKind LAMBDA_PARAMETER = SyntaxKind.of("JAVA_LAMBDA_PARAMETER");
    /**
     * Syntax kind for Java lambda body nodes.
     */
    public static final SyntaxKind LAMBDA_BODY = SyntaxKind.of("JAVA_LAMBDA_BODY");
    /**
     * Syntax kind for Java assignment expression nodes.
     */
    public static final SyntaxKind ASSIGNMENT_EXPRESSION = SyntaxKind.of("JAVA_ASSIGNMENT_EXPRESSION");
    /**
     * Syntax kind for Java conditional expression nodes.
     */
    public static final SyntaxKind CONDITIONAL_EXPRESSION = SyntaxKind.of("JAVA_CONDITIONAL_EXPRESSION");
    /**
     * Syntax kind for Java binary expression nodes.
     */
    public static final SyntaxKind BINARY_EXPRESSION = SyntaxKind.of("JAVA_BINARY_EXPRESSION");
    /**
     * Syntax kind for Java instanceof expression nodes.
     */
    public static final SyntaxKind INSTANCEOF_EXPRESSION = SyntaxKind.of("JAVA_INSTANCEOF_EXPRESSION");
    /**
     * Syntax kind for Java unary expression nodes.
     */
    public static final SyntaxKind UNARY_EXPRESSION = SyntaxKind.of("JAVA_UNARY_EXPRESSION");
    /**
     * Syntax kind for Java cast expression nodes.
     */
    public static final SyntaxKind CAST_EXPRESSION = SyntaxKind.of("JAVA_CAST_EXPRESSION");
    /**
     * Syntax kind for Java postfix expression nodes.
     */
    public static final SyntaxKind POSTFIX_EXPRESSION = SyntaxKind.of("JAVA_POSTFIX_EXPRESSION");
    /**
     * Syntax kind for Java primary expression nodes.
     */
    public static final SyntaxKind PRIMARY_EXPRESSION = SyntaxKind.of("JAVA_PRIMARY_EXPRESSION");
    /**
     * Syntax kind for Java parenthesized expression nodes.
     */
    public static final SyntaxKind PARENTHESIZED_EXPRESSION = SyntaxKind.of("JAVA_PARENTHESIZED_EXPRESSION");
    /**
     * Syntax kind for Java name expression nodes.
     */
    public static final SyntaxKind NAME_EXPRESSION = SyntaxKind.of("JAVA_NAME_EXPRESSION");
    /**
     * Syntax kind for Java this expression nodes.
     */
    public static final SyntaxKind THIS_EXPRESSION = SyntaxKind.of("JAVA_THIS_EXPRESSION");
    /**
     * Syntax kind for Java super expression nodes.
     */
    public static final SyntaxKind SUPER_EXPRESSION = SyntaxKind.of("JAVA_SUPER_EXPRESSION");
    /**
     * Syntax kind for Java field access expression nodes.
     */
    public static final SyntaxKind FIELD_ACCESS_EXPRESSION = SyntaxKind.of("JAVA_FIELD_ACCESS_EXPRESSION");
    /**
     * Syntax kind for Java array access expression nodes.
     */
    public static final SyntaxKind ARRAY_ACCESS_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_ACCESS_EXPRESSION");
    /**
     * Syntax kind for Java method invocation expression nodes.
     */
    public static final SyntaxKind METHOD_INVOCATION_EXPRESSION = SyntaxKind.of("JAVA_METHOD_INVOCATION_EXPRESSION");
    /**
     * Syntax kind for Java argument list nodes.
     */
    public static final SyntaxKind ARGUMENT_LIST = SyntaxKind.of("JAVA_ARGUMENT_LIST");
    /**
     * Syntax kind for Java method reference expression nodes.
     */
    public static final SyntaxKind METHOD_REFERENCE_EXPRESSION = SyntaxKind.of("JAVA_METHOD_REFERENCE_EXPRESSION");
    /**
     * Syntax kind for Java class instance creation expression nodes.
     */
    public static final SyntaxKind CLASS_INSTANCE_CREATION_EXPRESSION = SyntaxKind
        .of("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION");
    /**
     * Syntax kind for Java anonymous class body nodes.
     */
    public static final SyntaxKind ANONYMOUS_CLASS_BODY = SyntaxKind.of("JAVA_ANONYMOUS_CLASS_BODY");
    /**
     * Syntax kind for Java array creation expression nodes.
     */
    public static final SyntaxKind ARRAY_CREATION_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_CREATION_EXPRESSION");
    /**
     * Syntax kind for Java array initializer expression nodes.
     */
    public static final SyntaxKind ARRAY_INITIALIZER_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_INITIALIZER_EXPRESSION");
    /**
     * Syntax kind for Java class literal expression nodes.
     */
    public static final SyntaxKind CLASS_LITERAL_EXPRESSION = SyntaxKind.of("JAVA_CLASS_LITERAL_EXPRESSION");
    /**
     * Syntax kind for Java switch expression nodes.
     */
    public static final SyntaxKind SWITCH_EXPRESSION = SyntaxKind.of("JAVA_SWITCH_EXPRESSION");
    /**
     * Syntax kind for Java literal expression nodes.
     */
    public static final SyntaxKind LITERAL_EXPRESSION = SyntaxKind.of("JAVA_LITERAL_EXPRESSION");
    /**
     * Syntax kind for Java pattern nodes.
     */
    public static final SyntaxKind PATTERN = SyntaxKind.of("JAVA_PATTERN");
    /**
     * Syntax kind for a parser error node retaining unrecognized source.
     */
    public static final SyntaxKind ERROR = SyntaxKind.of("JAVA_ERROR");

    private static final Map<JavaTokenType, SyntaxKind> TOKEN_KINDS = createTokenKinds();
    private static final Map<JavaTokenType, SyntaxKind> MISSING_TOKEN_KINDS = createMissingTokenKinds();

    private JavaSyntaxKinds() {
    }

    /**
     * Returns the Java syntax kind for a present lexer token.
     *
     * @param tokenType the Java lexer token category
     * @return the syntax kind corresponding to the token type
     */
    public static SyntaxKind tokenKind(JavaTokenType tokenType) {
        return TOKEN_KINDS.get(tokenType);
    }

    /**
     * Returns the recovery syntax kind for an expected but absent Java token.
     *
     * @param tokenType the expected Java lexer token category
     * @return the missing-token syntax kind
     */
    public static SyntaxKind missingTokenKind(JavaTokenType tokenType) {
        return MISSING_TOKEN_KINDS.getOrDefault(tokenType, SyntaxKind.MISSING_TOKEN);
    }

    private static Map<JavaTokenType, SyntaxKind> createTokenKinds() {
        Map<JavaTokenType, SyntaxKind> tokenKinds = new EnumMap<>(JavaTokenType.class);
        for (JavaTokenType tokenType : JavaTokenType.values()) {
            tokenKinds.put(tokenType, SyntaxKind.of("JAVA_TOKEN_" + tokenType.name()));
        }

        return Map.copyOf(tokenKinds);
    }

    private static Map<JavaTokenType, SyntaxKind> createMissingTokenKinds() {
        Map<JavaTokenType, SyntaxKind> tokenKinds = new EnumMap<>(JavaTokenType.class);
        for (JavaTokenType tokenType : JavaTokenType.values()) {
            tokenKinds.put(tokenType, SyntaxKind.of("JAVA_MISSING_" + tokenType.name()));
        }

        return Map.copyOf(tokenKinds);
    }
}
