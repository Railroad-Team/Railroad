package dev.railroadide.railroad.ide.sst.impl.java;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public enum JavaTokenType {
    // Trivia tokens
    WHITESPACE,
    LINE_TERMINATOR,
    LINE_COMMENT,
    BLOCK_COMMENT,
    JAVADOC_COMMENT,

    // Strict Keywords
    ABSTRACT_KEYWORD,
    ASSERT_KEYWORD,
    BOOLEAN_KEYWORD,
    BREAK_KEYWORD,
    BYTE_KEYWORD,
    CASE_KEYWORD,
    CATCH_KEYWORD,
    CHAR_KEYWORD,
    CLASS_KEYWORD,
    CONST_KEYWORD,
    CONTINUE_KEYWORD,
    DEFAULT_KEYWORD,
    DO_KEYWORD,
    DOUBLE_KEYWORD,
    ELSE_KEYWORD,
    ENUM_KEYWORD,
    EXTENDS_KEYWORD,
    FINAL_KEYWORD,
    FINALLY_KEYWORD,
    FLOAT_KEYWORD,
    FOR_KEYWORD,
    GOTO_KEYWORD,
    IF_KEYWORD,
    IMPLEMENTS_KEYWORD,
    IMPORT_KEYWORD,
    INSTANCEOF_KEYWORD,
    INT_KEYWORD,
    INTERFACE_KEYWORD,
    LONG_KEYWORD,
    NATIVE_KEYWORD,
    NEW_KEYWORD,
    PACKAGE_KEYWORD,
    PRIVATE_KEYWORD,
    PROTECTED_KEYWORD,
    PUBLIC_KEYWORD,
    RETURN_KEYWORD,
    SHORT_KEYWORD,
    STATIC_KEYWORD,
    STRICTFP_KEYWORD,
    SUPER_KEYWORD,
    SWITCH_KEYWORD,
    SYNCHRONIZED_KEYWORD,
    THIS_KEYWORD,
    THROW_KEYWORD,
    THROWS_KEYWORD,
    TRANSIENT_KEYWORD,
    TRY_KEYWORD,
    VOID_KEYWORD,
    VOLATILE_KEYWORD,
    WHILE_KEYWORD,

    // Contextual Keywords
    UNDERSCORE_KEYWORD,
    EXPORTS_KEYWORD,
    MODULE_KEYWORD,
    NON_SEALED_KEYWORD,
    OPEN_KEYWORD,
    OPENS_KEYWORD,
    PERMITS_KEYWORD,
    PROVIDES_KEYWORD,
    RECORD_KEYWORD,
    REQUIRES_KEYWORD,
    SEALED_KEYWORD,
    TO_KEYWORD,
    TRANSITIVE_KEYWORD,
    USES_KEYWORD,
    VAR_KEYWORD,
    WITH_KEYWORD,
    YIELD_KEYWORD,
    WHEN_KEYWORD,

    // Literal tokens
    NULL_LITERAL,
    BOOLEAN_LITERAL,
    NUMBER_INT_LITERAL,
    NUMBER_HEXADECIMAL_LITERAL,
    NUMBER_BINARY_LITERAL,
    NUMBER_OCTAL_LITERAL,
    NUMBER_FLOATING_POINT_LITERAL,
    CHARACTER_LITERAL,
    STRING_LITERAL,
    TEXT_BLOCK_LITERAL,

    IDENTIFIER,

    // Punctuation tokens
    OPEN_PAREN,
    CLOSE_PAREN,
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_BRACKET,
    CLOSE_BRACKET,
    SEMICOLON,
    COMMA,
    DOT,
    ELLIPSIS,

    // Operator tokens
    ASSIGN, // =
    GREATER_THAN, // >
    LESS_THAN, // <
    NOT, // !
    QUESTION_MARK, // ?
    COLON, // :
    ARROW, // ->
    DOUBLE_COLON, // ::
    EQUALS, // ==
    GREATER_THAN_OR_EQUALS, // >=
    LESS_THAN_OR_EQUALS, // <=
    NOT_EQUALS, // !=
    AND, // &&
    OR, // ||
    PLUS, // +
    MINUS, // -
    PLUS_PLUS, // ++
    MINUS_MINUS, // --
    MULTIPLY, // *
    DIVIDE, // /
    MODULUS, // %
    BITWISE_COMPLEMENT, // ~
    BITWISE_AND, // &
    BITWISE_OR, // |
    BITWISE_XOR, // ^
    AT, // @
    LEFT_SHIFT, // <<
    RIGHT_SHIFT, // >>
    UNSIGNED_RIGHT_SHIFT, // >>>
    PLUS_EQUALS, // +=
    MINUS_EQUALS, // -=
    MULTIPLY_EQUALS, // *=
    DIVIDE_EQUALS, // /=
    MODULUS_EQUALS, // %=
    AND_EQUALS, // &=
    OR_EQUALS, // |=
    XOR_EQUALS, // ^=
    LEFT_SHIFT_EQUALS, // <<=
    RIGHT_SHIFT_EQUALS, // >>=

    EOF, // End of file token
    UNKNOWN; // Represents any token that does not match the above types

    public static final Map<Character, List<Map.Entry<CharSequence, JavaTokenType>>> MULTI_CHAR_TOKENS = new HashMap<>() {{
        put('+', List.of(
                Map.entry("++", PLUS_PLUS),
                Map.entry("+=", PLUS_EQUALS))
        );
        put('-', List.of(
                Map.entry("--", MINUS_MINUS),
                Map.entry("-=", MINUS_EQUALS))
        );
        put('*', List.of(
                Map.entry("*=", MULTIPLY_EQUALS))
        );
        put('/', List.of(
                Map.entry("/=", DIVIDE_EQUALS))
        );
        put('%', List.of(
                Map.entry("%=", MODULUS_EQUALS))
        );
        put('&', List.of(
                Map.entry("&&", AND),
                Map.entry("&=", AND_EQUALS))
        );
        put('|', List.of(
                Map.entry("||", OR),
                Map.entry("|=", OR_EQUALS))
        );
        put('^', List.of(
                Map.entry("^=", XOR_EQUALS))
        );
        put('>', List.of(
                Map.entry(">>", RIGHT_SHIFT),
                Map.entry(">>>", UNSIGNED_RIGHT_SHIFT),
                Map.entry(">=", GREATER_THAN_OR_EQUALS),
                Map.entry(">>=", RIGHT_SHIFT_EQUALS))
        );
        put('<', List.of(
                Map.entry("<<", LEFT_SHIFT),
                Map.entry("<<=", LEFT_SHIFT_EQUALS),
                Map.entry("<=", LESS_THAN_OR_EQUALS))
        );
        put('=', List.of(
                Map.entry("==", EQUALS),
                Map.entry("=>", ARROW))
        );
        put('!', List.of(
                Map.entry("!=", NOT_EQUALS))
        );
        put(':', List.of(
                Map.entry("::", DOUBLE_COLON))
        );
    }};

    public static final Map<Character, JavaTokenType> SINGLE_CHAR_TOKENS = new HashMap<>() {{
        put('=', ASSIGN);
        put('>', GREATER_THAN);
        put('<', LESS_THAN);
        put('!', NOT);
        put('?', QUESTION_MARK);
        put(':', COLON);
        put('+', PLUS);
        put('-', MINUS);
        put('*', MULTIPLY);
        put('/', DIVIDE);
        put('%', MODULUS);
        put('~', BITWISE_COMPLEMENT);
        put('&', BITWISE_AND);
        put('|', BITWISE_OR);
        put('^', BITWISE_XOR);
        put('(', OPEN_PAREN);
        put(')', CLOSE_PAREN);
        put('{', OPEN_BRACE);
        put('}', CLOSE_BRACE);
        put('[', OPEN_BRACKET);
        put(']', CLOSE_BRACKET);
        put(';', SEMICOLON);
        put(',', COMMA);
        put('.', DOT);
        put('@', AT);
    }};

    public static final Map<String, JavaTokenType> KEYWORDS = new HashMap<>() {{
        for (JavaTokenType value : values()) {
            if(value == NON_SEALED_KEYWORD) {
                put("non-sealed", value);
                continue;
            }

            if (value.name().endsWith("_KEYWORD")) {
                String keyword = value.name().replace("_KEYWORD", "").toLowerCase(Locale.ROOT);
                put(keyword, value);
            }
        }
    }};
}
