package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;

import java.util.EnumMap;
import java.util.Map;

public final class JavaSyntaxKinds {
    public static final SyntaxKind COMPILATION_UNIT = SyntaxKind.of("JAVA_COMPILATION_UNIT");
    public static final SyntaxKind PACKAGE_DECLARATION = SyntaxKind.of("JAVA_PACKAGE_DECLARATION");
    public static final SyntaxKind IMPORT_DECLARATION = SyntaxKind.of("JAVA_IMPORT_DECLARATION");
    public static final SyntaxKind IMPORT_TARGET = SyntaxKind.of("JAVA_IMPORT_TARGET");
    public static final SyntaxKind MODULE_DECLARATION = SyntaxKind.of("JAVA_MODULE_DECLARATION");
    public static final SyntaxKind MODULE_BODY = SyntaxKind.of("JAVA_MODULE_BODY");
    public static final SyntaxKind MODULE_REQUIRES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_REQUIRES_DIRECTIVE");
    public static final SyntaxKind MODULE_EXPORTS_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_EXPORTS_DIRECTIVE");
    public static final SyntaxKind MODULE_OPENS_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_OPENS_DIRECTIVE");
    public static final SyntaxKind MODULE_USES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_USES_DIRECTIVE");
    public static final SyntaxKind MODULE_PROVIDES_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_PROVIDES_DIRECTIVE");
    public static final SyntaxKind MODULE_UNKNOWN_DIRECTIVE = SyntaxKind.of("JAVA_MODULE_UNKNOWN_DIRECTIVE");
    public static final SyntaxKind QUALIFIED_NAME = SyntaxKind.of("JAVA_QUALIFIED_NAME");
    public static final SyntaxKind ANNOTATION = SyntaxKind.of("JAVA_ANNOTATION");
    public static final SyntaxKind TYPE_DECLARATION = SyntaxKind.of("JAVA_TYPE_DECLARATION");
    public static final SyntaxKind CLASS_DECLARATION = SyntaxKind.of("JAVA_CLASS_DECLARATION");
    public static final SyntaxKind INTERFACE_DECLARATION = SyntaxKind.of("JAVA_INTERFACE_DECLARATION");
    public static final SyntaxKind ENUM_DECLARATION = SyntaxKind.of("JAVA_ENUM_DECLARATION");
    public static final SyntaxKind ANNOTATION_TYPE_DECLARATION = SyntaxKind.of("JAVA_ANNOTATION_TYPE_DECLARATION");
    public static final SyntaxKind RECORD_DECLARATION = SyntaxKind.of("JAVA_RECORD_DECLARATION");
    public static final SyntaxKind EMPTY_TYPE_DECLARATION = SyntaxKind.of("JAVA_EMPTY_TYPE_DECLARATION");
    public static final SyntaxKind CLASS_BODY = SyntaxKind.of("JAVA_CLASS_BODY");
    public static final SyntaxKind INTERFACE_BODY = SyntaxKind.of("JAVA_INTERFACE_BODY");
    public static final SyntaxKind ENUM_BODY = SyntaxKind.of("JAVA_ENUM_BODY");
    public static final SyntaxKind ANNOTATION_TYPE_BODY = SyntaxKind.of("JAVA_ANNOTATION_TYPE_BODY");
    public static final SyntaxKind RECORD_BODY = SyntaxKind.of("JAVA_RECORD_BODY");
    public static final SyntaxKind TYPE_MEMBER = SyntaxKind.of("JAVA_TYPE_MEMBER");
    public static final SyntaxKind ANNOTATION_TYPE_MEMBER = SyntaxKind.of("JAVA_ANNOTATION_TYPE_MEMBER");
    public static final SyntaxKind RECORD_COMPACT_CONSTRUCTOR = SyntaxKind.of("JAVA_RECORD_COMPACT_CONSTRUCTOR");
    public static final SyntaxKind ENUM_CONSTANT = SyntaxKind.of("JAVA_ENUM_CONSTANT");
    public static final SyntaxKind FIELD_DECLARATION = SyntaxKind.of("JAVA_FIELD_DECLARATION");
    public static final SyntaxKind VARIABLE_DECLARATOR = SyntaxKind.of("JAVA_VARIABLE_DECLARATOR");
    public static final SyntaxKind METHOD_DECLARATION = SyntaxKind.of("JAVA_METHOD_DECLARATION");
    public static final SyntaxKind CONSTRUCTOR_DECLARATION = SyntaxKind.of("JAVA_CONSTRUCTOR_DECLARATION");
    public static final SyntaxKind PARAMETER_LIST = SyntaxKind.of("JAVA_PARAMETER_LIST");
    public static final SyntaxKind PARAMETER = SyntaxKind.of("JAVA_PARAMETER");
    public static final SyntaxKind THROWS_CLAUSE = SyntaxKind.of("JAVA_THROWS_CLAUSE");
    public static final SyntaxKind STATIC_INITIALIZER = SyntaxKind.of("JAVA_STATIC_INITIALIZER");
    public static final SyntaxKind INSTANCE_INITIALIZER = SyntaxKind.of("JAVA_INSTANCE_INITIALIZER");
    public static final SyntaxKind RECORD_HEADER = SyntaxKind.of("JAVA_RECORD_HEADER");
    public static final SyntaxKind RECORD_COMPONENT = SyntaxKind.of("JAVA_RECORD_COMPONENT");
    public static final SyntaxKind TYPE_PARAMETERS = SyntaxKind.of("JAVA_TYPE_PARAMETERS");
    public static final SyntaxKind TYPE_ARGUMENTS = SyntaxKind.of("JAVA_TYPE_ARGUMENTS");
    public static final SyntaxKind TYPE_PARAMETER = SyntaxKind.of("JAVA_TYPE_PARAMETER");
    public static final SyntaxKind TYPE_BOUND = SyntaxKind.of("JAVA_TYPE_BOUND");
    public static final SyntaxKind WILDCARD_TYPE = SyntaxKind.of("JAVA_WILDCARD_TYPE");
    public static final SyntaxKind DIAMOND_TYPE_ARGUMENTS = SyntaxKind.of("JAVA_DIAMOND_TYPE_ARGUMENTS");
    public static final SyntaxKind TYPE_REFERENCE = SyntaxKind.of("JAVA_TYPE_REFERENCE");
    public static final SyntaxKind INTERSECTION_TYPE_REFERENCE = SyntaxKind.of("JAVA_INTERSECTION_TYPE_REFERENCE");
    public static final SyntaxKind UNION_TYPE_REFERENCE = SyntaxKind.of("JAVA_UNION_TYPE_REFERENCE");
    public static final SyntaxKind ARRAY_DIMENSION = SyntaxKind.of("JAVA_ARRAY_DIMENSION");
    public static final SyntaxKind EXTENDS_CLAUSE = SyntaxKind.of("JAVA_EXTENDS_CLAUSE");
    public static final SyntaxKind IMPLEMENTS_CLAUSE = SyntaxKind.of("JAVA_IMPLEMENTS_CLAUSE");
    public static final SyntaxKind PERMITS_CLAUSE = SyntaxKind.of("JAVA_PERMITS_CLAUSE");
    public static final SyntaxKind STATEMENT = SyntaxKind.of("JAVA_STATEMENT");
    public static final SyntaxKind BLOCK = SyntaxKind.of("JAVA_BLOCK");
    public static final SyntaxKind EMPTY_STATEMENT = SyntaxKind.of("JAVA_EMPTY_STATEMENT");
    public static final SyntaxKind EXPRESSION_STATEMENT = SyntaxKind.of("JAVA_EXPRESSION_STATEMENT");
    public static final SyntaxKind LOCAL_VARIABLE_DECLARATION_STATEMENT = SyntaxKind.of("JAVA_LOCAL_VARIABLE_DECLARATION_STATEMENT");
    public static final SyntaxKind IF_STATEMENT = SyntaxKind.of("JAVA_IF_STATEMENT");
    public static final SyntaxKind SWITCH_STATEMENT = SyntaxKind.of("JAVA_SWITCH_STATEMENT");
    public static final SyntaxKind SWITCH_RULE = SyntaxKind.of("JAVA_SWITCH_RULE");
    public static final SyntaxKind SWITCH_LABEL = SyntaxKind.of("JAVA_SWITCH_LABEL");
    public static final SyntaxKind SWITCH_CASE_ITEM = SyntaxKind.of("JAVA_SWITCH_CASE_ITEM");
    public static final SyntaxKind PATTERN_GUARD = SyntaxKind.of("JAVA_PATTERN_GUARD");
    public static final SyntaxKind WHILE_STATEMENT = SyntaxKind.of("JAVA_WHILE_STATEMENT");
    public static final SyntaxKind DO_WHILE_STATEMENT = SyntaxKind.of("JAVA_DO_WHILE_STATEMENT");
    public static final SyntaxKind FOR_STATEMENT = SyntaxKind.of("JAVA_FOR_STATEMENT");
    public static final SyntaxKind BASIC_FOR_STATEMENT = SyntaxKind.of("JAVA_BASIC_FOR_STATEMENT");
    public static final SyntaxKind ENHANCED_FOR_STATEMENT = SyntaxKind.of("JAVA_ENHANCED_FOR_STATEMENT");
    public static final SyntaxKind TRY_STATEMENT = SyntaxKind.of("JAVA_TRY_STATEMENT");
    public static final SyntaxKind TRY_RESOURCE = SyntaxKind.of("JAVA_TRY_RESOURCE");
    public static final SyntaxKind CATCH_CLAUSE = SyntaxKind.of("JAVA_CATCH_CLAUSE");
    public static final SyntaxKind FINALLY_CLAUSE = SyntaxKind.of("JAVA_FINALLY_CLAUSE");
    public static final SyntaxKind SYNCHRONIZED_STATEMENT = SyntaxKind.of("JAVA_SYNCHRONIZED_STATEMENT");
    public static final SyntaxKind RETURN_STATEMENT = SyntaxKind.of("JAVA_RETURN_STATEMENT");
    public static final SyntaxKind THROW_STATEMENT = SyntaxKind.of("JAVA_THROW_STATEMENT");
    public static final SyntaxKind BREAK_STATEMENT = SyntaxKind.of("JAVA_BREAK_STATEMENT");
    public static final SyntaxKind CONTINUE_STATEMENT = SyntaxKind.of("JAVA_CONTINUE_STATEMENT");
    public static final SyntaxKind ASSERT_STATEMENT = SyntaxKind.of("JAVA_ASSERT_STATEMENT");
    public static final SyntaxKind YIELD_STATEMENT = SyntaxKind.of("JAVA_YIELD_STATEMENT");
    public static final SyntaxKind LABELED_STATEMENT = SyntaxKind.of("JAVA_LABELED_STATEMENT");
    public static final SyntaxKind EXPRESSION = SyntaxKind.of("JAVA_EXPRESSION");
    public static final SyntaxKind LAMBDA_EXPRESSION = SyntaxKind.of("JAVA_LAMBDA_EXPRESSION");
    public static final SyntaxKind LAMBDA_PARAMETERS = SyntaxKind.of("JAVA_LAMBDA_PARAMETERS");
    public static final SyntaxKind LAMBDA_PARAMETER = SyntaxKind.of("JAVA_LAMBDA_PARAMETER");
    public static final SyntaxKind LAMBDA_BODY = SyntaxKind.of("JAVA_LAMBDA_BODY");
    public static final SyntaxKind ASSIGNMENT_EXPRESSION = SyntaxKind.of("JAVA_ASSIGNMENT_EXPRESSION");
    public static final SyntaxKind CONDITIONAL_EXPRESSION = SyntaxKind.of("JAVA_CONDITIONAL_EXPRESSION");
    public static final SyntaxKind BINARY_EXPRESSION = SyntaxKind.of("JAVA_BINARY_EXPRESSION");
    public static final SyntaxKind INSTANCEOF_EXPRESSION = SyntaxKind.of("JAVA_INSTANCEOF_EXPRESSION");
    public static final SyntaxKind UNARY_EXPRESSION = SyntaxKind.of("JAVA_UNARY_EXPRESSION");
    public static final SyntaxKind CAST_EXPRESSION = SyntaxKind.of("JAVA_CAST_EXPRESSION");
    public static final SyntaxKind POSTFIX_EXPRESSION = SyntaxKind.of("JAVA_POSTFIX_EXPRESSION");
    public static final SyntaxKind PRIMARY_EXPRESSION = SyntaxKind.of("JAVA_PRIMARY_EXPRESSION");
    public static final SyntaxKind PARENTHESIZED_EXPRESSION = SyntaxKind.of("JAVA_PARENTHESIZED_EXPRESSION");
    public static final SyntaxKind NAME_EXPRESSION = SyntaxKind.of("JAVA_NAME_EXPRESSION");
    public static final SyntaxKind THIS_EXPRESSION = SyntaxKind.of("JAVA_THIS_EXPRESSION");
    public static final SyntaxKind SUPER_EXPRESSION = SyntaxKind.of("JAVA_SUPER_EXPRESSION");
    public static final SyntaxKind FIELD_ACCESS_EXPRESSION = SyntaxKind.of("JAVA_FIELD_ACCESS_EXPRESSION");
    public static final SyntaxKind ARRAY_ACCESS_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_ACCESS_EXPRESSION");
    public static final SyntaxKind METHOD_INVOCATION_EXPRESSION = SyntaxKind.of("JAVA_METHOD_INVOCATION_EXPRESSION");
    public static final SyntaxKind ARGUMENT_LIST = SyntaxKind.of("JAVA_ARGUMENT_LIST");
    public static final SyntaxKind METHOD_REFERENCE_EXPRESSION = SyntaxKind.of("JAVA_METHOD_REFERENCE_EXPRESSION");
    public static final SyntaxKind CLASS_INSTANCE_CREATION_EXPRESSION = SyntaxKind.of("JAVA_CLASS_INSTANCE_CREATION_EXPRESSION");
    public static final SyntaxKind ANONYMOUS_CLASS_BODY = SyntaxKind.of("JAVA_ANONYMOUS_CLASS_BODY");
    public static final SyntaxKind ARRAY_CREATION_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_CREATION_EXPRESSION");
    public static final SyntaxKind ARRAY_INITIALIZER_EXPRESSION = SyntaxKind.of("JAVA_ARRAY_INITIALIZER_EXPRESSION");
    public static final SyntaxKind CLASS_LITERAL_EXPRESSION = SyntaxKind.of("JAVA_CLASS_LITERAL_EXPRESSION");
    public static final SyntaxKind SWITCH_EXPRESSION = SyntaxKind.of("JAVA_SWITCH_EXPRESSION");
    public static final SyntaxKind LITERAL_EXPRESSION = SyntaxKind.of("JAVA_LITERAL_EXPRESSION");
    public static final SyntaxKind PATTERN = SyntaxKind.of("JAVA_PATTERN");
    public static final SyntaxKind ERROR = SyntaxKind.of("JAVA_ERROR");

    private static final Map<JavaTokenType, SyntaxKind> TOKEN_KINDS = createTokenKinds();
    private static final Map<JavaTokenType, SyntaxKind> MISSING_TOKEN_KINDS = createMissingTokenKinds();

    private JavaSyntaxKinds() {
    }

    public static SyntaxKind tokenKind(JavaTokenType tokenType) {
        return TOKEN_KINDS.get(tokenType);
    }

    static SyntaxKind missingTokenKind(JavaTokenType tokenType) {
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
