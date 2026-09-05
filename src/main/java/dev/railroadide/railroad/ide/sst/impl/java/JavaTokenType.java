package dev.railroadide.railroad.ide.sst.impl.java;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Token categories emitted by the Java lexer, with keyword and operator lookup tables.
 */
public enum JavaTokenType {
    // Trivia tokens
    /**
     * Horizontal whitespace between Java tokens.
     */
    WHITESPACE,
    /**
     * A Java source line terminator.
     */
    LINE_TERMINATOR,
    /**
     * A comment extending to the end of a source line.
     */
    LINE_COMMENT,
    /**
     * A delimited block comment.
     */
    BLOCK_COMMENT,
    /**
     * A documentation comment.
     */
    JAVADOC_COMMENT,

    // Strict Keywords
    /**
     * The {@code abstract} keyword token.
     */
    ABSTRACT_KEYWORD,
    /**
     * The {@code assert} keyword token.
     */
    ASSERT_KEYWORD,
    /**
     * The {@code boolean} keyword token.
     */
    BOOLEAN_KEYWORD,
    /**
     * The {@code break} keyword token.
     */
    BREAK_KEYWORD,
    /**
     * The {@code byte} keyword token.
     */
    BYTE_KEYWORD,
    /**
     * The {@code case} keyword token.
     */
    CASE_KEYWORD,
    /**
     * The {@code catch} keyword token.
     */
    CATCH_KEYWORD,
    /**
     * The {@code char} keyword token.
     */
    CHAR_KEYWORD,
    /**
     * The {@code class} keyword token.
     */
    CLASS_KEYWORD,
    /**
     * The {@code const} keyword token.
     */
    CONST_KEYWORD,
    /**
     * The {@code continue} keyword token.
     */
    CONTINUE_KEYWORD,
    /**
     * The {@code default} keyword token.
     */
    DEFAULT_KEYWORD,
    /**
     * The {@code do} keyword token.
     */
    DO_KEYWORD,
    /**
     * The {@code double} keyword token.
     */
    DOUBLE_KEYWORD,
    /**
     * The {@code else} keyword token.
     */
    ELSE_KEYWORD,
    /**
     * The {@code enum} keyword token.
     */
    ENUM_KEYWORD,
    /**
     * The {@code extends} keyword token.
     */
    EXTENDS_KEYWORD,
    /**
     * The {@code final} keyword token.
     */
    FINAL_KEYWORD,
    /**
     * The {@code finally} keyword token.
     */
    FINALLY_KEYWORD,
    /**
     * The {@code float} keyword token.
     */
    FLOAT_KEYWORD,
    /**
     * The {@code for} keyword token.
     */
    FOR_KEYWORD,
    /**
     * The {@code goto} keyword token.
     */
    GOTO_KEYWORD,
    /**
     * The {@code if} keyword token.
     */
    IF_KEYWORD,
    /**
     * The {@code implements} keyword token.
     */
    IMPLEMENTS_KEYWORD,
    /**
     * The {@code import} keyword token.
     */
    IMPORT_KEYWORD,
    /**
     * The {@code instanceof} keyword token.
     */
    INSTANCEOF_KEYWORD,
    /**
     * The {@code int} keyword token.
     */
    INT_KEYWORD,
    /**
     * The {@code interface} keyword token.
     */
    INTERFACE_KEYWORD,
    /**
     * The {@code @interface} keyword token.
     */
    AT_INTERFACE_KEYWORD,
    /**
     * The {@code long} keyword token.
     */
    LONG_KEYWORD,
    /**
     * The {@code native} keyword token.
     */
    NATIVE_KEYWORD,
    /**
     * The {@code new} keyword token.
     */
    NEW_KEYWORD,
    /**
     * The {@code package} keyword token.
     */
    PACKAGE_KEYWORD,
    /**
     * The {@code private} keyword token.
     */
    PRIVATE_KEYWORD,
    /**
     * The {@code protected} keyword token.
     */
    PROTECTED_KEYWORD,
    /**
     * The {@code public} keyword token.
     */
    PUBLIC_KEYWORD,
    /**
     * The {@code return} keyword token.
     */
    RETURN_KEYWORD,
    /**
     * The {@code short} keyword token.
     */
    SHORT_KEYWORD,
    /**
     * The {@code static} keyword token.
     */
    STATIC_KEYWORD,
    /**
     * The {@code strictfp} keyword token.
     */
    STRICTFP_KEYWORD,
    /**
     * The {@code super} keyword token.
     */
    SUPER_KEYWORD,
    /**
     * The {@code switch} keyword token.
     */
    SWITCH_KEYWORD,
    /**
     * The {@code synchronized} keyword token.
     */
    SYNCHRONIZED_KEYWORD,
    /**
     * The {@code this} keyword token.
     */
    THIS_KEYWORD,
    /**
     * The {@code throw} keyword token.
     */
    THROW_KEYWORD,
    /**
     * The {@code throws} keyword token.
     */
    THROWS_KEYWORD,
    /**
     * The {@code transient} keyword token.
     */
    TRANSIENT_KEYWORD,
    /**
     * The {@code try} keyword token.
     */
    TRY_KEYWORD,
    /**
     * The {@code void} keyword token.
     */
    VOID_KEYWORD,
    /**
     * The {@code volatile} keyword token.
     */
    VOLATILE_KEYWORD,
    /**
     * The {@code while} keyword token.
     */
    WHILE_KEYWORD,

    // Contextual Keywords
    /**
     * The {@code _} keyword token.
     */
    UNDERSCORE_KEYWORD,
    /**
     * The {@code exports} keyword token.
     */
    EXPORTS_KEYWORD,
    /**
     * The {@code module} keyword token.
     */
    MODULE_KEYWORD,
    /**
     * The {@code non-sealed} keyword token.
     */
    NON_SEALED_KEYWORD,
    /**
     * The {@code open} keyword token.
     */
    OPEN_KEYWORD,
    /**
     * The {@code opens} keyword token.
     */
    OPENS_KEYWORD,
    /**
     * The {@code permits} keyword token.
     */
    PERMITS_KEYWORD,
    /**
     * The {@code provides} keyword token.
     */
    PROVIDES_KEYWORD,
    /**
     * The {@code record} keyword token.
     */
    RECORD_KEYWORD,
    /**
     * The {@code requires} keyword token.
     */
    REQUIRES_KEYWORD,
    /**
     * The {@code sealed} keyword token.
     */
    SEALED_KEYWORD,
    /**
     * The {@code to} keyword token.
     */
    TO_KEYWORD,
    /**
     * The {@code transitive} keyword token.
     */
    TRANSITIVE_KEYWORD,
    /**
     * The {@code uses} keyword token.
     */
    USES_KEYWORD,
    /**
     * The {@code var} keyword token.
     */
    VAR_KEYWORD,
    /**
     * The {@code with} keyword token.
     */
    WITH_KEYWORD,
    /**
     * The {@code yield} keyword token.
     */
    YIELD_KEYWORD,
    /**
     * The {@code when} keyword token.
     */
    WHEN_KEYWORD,

    // Literal tokens
    /**
     * The {@code null} literal.
     */
    NULL_LITERAL,
    /**
     * A {@code true} or {@code false} literal.
     */
    BOOLEAN_LITERAL,
    /**
     * A decimal integer literal.
     */
    NUMBER_INT_LITERAL,
    /**
     * A hexadecimal integer literal.
     */
    NUMBER_HEXADECIMAL_LITERAL,
    /**
     * A binary integer literal.
     */
    NUMBER_BINARY_LITERAL,
    /**
     * An octal integer literal.
     */
    NUMBER_OCTAL_LITERAL,
    /**
     * A floating-point numeric literal.
     */
    NUMBER_FLOATING_POINT_LITERAL,
    /**
     * A single-quoted character literal.
     */
    CHARACTER_LITERAL,
    /**
     * A double-quoted string literal.
     */
    STRING_LITERAL,
    /**
     * A multiline text-block literal.
     */
    TEXT_BLOCK_LITERAL,

    /**
     * A Java identifier that is not classified as a keyword or literal.
     */
    IDENTIFIER,

    // Punctuation tokens
    /**
     * The {@code (} token.
     */
    OPEN_PAREN,
    /**
     * The {@code )} token.
     */
    CLOSE_PAREN,
    /**
     * The opening brace token.
     */
    OPEN_BRACE,
    /**
     * The closing brace token.
     */
    CLOSE_BRACE,
    /**
     * The {@code [} token.
     */
    OPEN_BRACKET,
    /**
     * The {@code ]} token.
     */
    CLOSE_BRACKET,
    /**
     * The {@code ;} token.
     */
    SEMICOLON,
    /**
     * The {@code ,} token.
     */
    COMMA,
    /**
     * The {@code .} token.
     */
    DOT,
    /**
     * The {@code ...} token.
     */
    ELLIPSIS,

    // Operator tokens
    /**
     * The {@code =} token.
     */
    EQUALS, // =
    /**
     * The {@code >} token.
     */
    RIGHT_ANGLED_BRACKET, // >
    /**
     * The {@code <} token.
     */
    LEFT_ANGLED_BRACKET, // <
    /**
     * The {@code !} token.
     */
    EXCLAMATION_MARK, // !
    /**
     * The {@code ?} token.
     */
    QUESTION_MARK, // ?
    /**
     * The {@code :} token.
     */
    COLON, // :
    /**
     * The {@code ->} token.
     */
    ARROW, // ->
    /**
     * The {@code ::} token.
     */
    DOUBLE_COLON, // ::
    /**
     * The {@code ==} token.
     */
    DOUBLE_EQUALS, // ==
    /**
     * The {@code >=} token.
     */
    GREATER_THAN_OR_EQUALS, // >=
    /**
     * The {@code <=} token.
     */
    LESS_THAN_OR_EQUALS, // <=
    /**
     * The {@code !=} token.
     */
    NOT_EQUALS, // !=
    /**
     * The {@code &&} token.
     */
    AND, // &&
    /**
     * The {@code ||} token.
     */
    OR, // ||
    /**
     * The {@code +} token.
     */
    PLUS, // +
    /**
     * The {@code -} token.
     */
    MINUS, // -
    /**
     * The {@code ++} token.
     */
    PLUS_PLUS, // ++
    /**
     * The {@code --} token.
     */
    MINUS_MINUS, // --
    /**
     * The {@code *} token.
     */
    STAR, // *
    /**
     * The {@code /} token.
     */
    SLASH, // /
    /**
     * The {@code %} token.
     */
    PERCENT, // %
    /**
     * The {@code ~} token.
     */
    TILDA, // ~
    /**
     * The {@code &} token.
     */
    AMPERSAND, // &
    /**
     * The {@code |} token.
     */
    PIPE, // |
    /**
     * The {@code ^} token.
     */
    CARET, // ^
    /**
     * The {@code @} token.
     */
    AT, // @
    /**
     * The {@code <<} token.
     */
    LEFT_SHIFT, // <<
    /**
     * The {@code +=} token.
     */
    PLUS_EQUALS, // +=
    /**
     * The {@code -=} token.
     */
    MINUS_EQUALS, // -=
    /**
     * The {@code *=} token.
     */
    STAR_EQUALS, // *=
    /**
     * The {@code /=} token.
     */
    SLASH_EQUALS, // /=
    /**
     * The {@code %=} token.
     */
    PERCENT_EQUALS, // %=
    /**
     * The {@code &=} token.
     */
    AMPERSAND_EQUALS, // &=
    /**
     * The {@code |=} token.
     */
    PIPE_EQUALS, // |=
    /**
     * The {@code ^=} token.
     */
    CARET_EQUALS, // ^=
    /**
     * The {@code <<=} token.
     */
    LEFT_SHIFT_EQUALS, // <<=
    /**
     * The {@code >>=} token.
     */
    RIGHT_SHIFT_EQUALS, // >>=
    /**
     * The {@code >>>=} token.
     */
    UNSIGNED_RIGHT_SHIFT_EQUALS, // >>>=
    /**
     * The {@code >>} token.
     */
    RIGHT_SHIFT, // >>
    /**
     * The {@code >>>} token.
     */
    UNSIGNED_RIGHT_SHIFT, // >>>

    /**
     * Marks the end of the source input.
     */
    EOF, // End of file token
    /**
     * An input token that does not match a recognized Java token category.
     */
    UNKNOWN; // Represents any token that does not match the above types

    /**
     * Multicharacter operator and punctuation spellings grouped by their first character.
     */
    public static final Map<Character, List<Map.Entry<CharSequence, JavaTokenType>>> MULTI_CHAR_TOKENS = new HashMap<>() {
        {
            put('+', List.of(
                Map.entry("++", PLUS_PLUS),
                Map.entry("+=", PLUS_EQUALS)));
            put('-', List.of(
                Map.entry("--", MINUS_MINUS),
                Map.entry("-=", MINUS_EQUALS),
                Map.entry("->", ARROW)));
            put('*', List.of(
                Map.entry("*=", STAR_EQUALS)));
            put('/', List.of(
                Map.entry("/=", SLASH_EQUALS)));
            put('%', List.of(
                Map.entry("%=", PERCENT_EQUALS)));
            put('&', List.of(
                Map.entry("&&", AND),
                Map.entry("&=", AMPERSAND_EQUALS)));
            put('|', List.of(
                Map.entry("||", OR),
                Map.entry("|=", PIPE_EQUALS)));
            put('^', List.of(
                Map.entry("^=", CARET_EQUALS)));
            put('>', List.of(
                Map.entry(">=", GREATER_THAN_OR_EQUALS),
                Map.entry(">>", RIGHT_SHIFT),
                Map.entry(">>>", UNSIGNED_RIGHT_SHIFT),
                Map.entry(">>=", RIGHT_SHIFT_EQUALS),
                Map.entry(">>>=", UNSIGNED_RIGHT_SHIFT_EQUALS)));
            put('<', List.of(
                Map.entry("<<", LEFT_SHIFT),
                Map.entry("<<=", LEFT_SHIFT_EQUALS),
                Map.entry("<=", LESS_THAN_OR_EQUALS)));
            put('=', List.of(
                Map.entry("==", DOUBLE_EQUALS)));
            put('!', List.of(
                Map.entry("!=", NOT_EQUALS)));
            put('.', List.of(
                Map.entry("...", ELLIPSIS)));
            put(':', List.of(
                Map.entry("::", DOUBLE_COLON)));
        }
    };

    /**
     * Maps single-character operator and punctuation spellings to token categories.
     */
    public static final Map<Character, JavaTokenType> SINGLE_CHAR_TOKENS = new HashMap<>() {
        {
            put('=', EQUALS);
            put('>', RIGHT_ANGLED_BRACKET);
            put('<', LEFT_ANGLED_BRACKET);
            put('!', EXCLAMATION_MARK);
            put('?', QUESTION_MARK);
            put(':', COLON);
            put('+', PLUS);
            put('-', MINUS);
            put('*', STAR);
            put('/', SLASH);
            put('%', PERCENT);
            put('~', TILDA);
            put('&', AMPERSAND);
            put('|', PIPE);
            put('^', CARET);
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
        }
    };

    private static final Map<String, JavaTokenType> KEYWORDS = new HashMap<>();

    /**
     * Builds or returns the cached map from recognized keyword spellings to token categories.
     *
     * @return the keyword spelling-to-token map
     */
    public static Map<String, JavaTokenType> listKeywords() {
        if (!KEYWORDS.isEmpty())
            return KEYWORDS;

        Map<String, JavaTokenType> keywords = new HashMap<>();
        for (JavaTokenType tokenType : JavaTokenType.values()) {
            if (tokenType == NON_SEALED_KEYWORD) {
                keywords.put("non-sealed", tokenType);
                continue;
            } else if (tokenType == AT_INTERFACE_KEYWORD) {
                keywords.put("@interface", tokenType);
                continue;
            }

            if (tokenType.name().endsWith("_KEYWORD")) {
                String keyword = tokenType.name().replace("_KEYWORD", "").toLowerCase(Locale.ROOT);
                keywords.put(keyword, tokenType);
            }
        }

        KEYWORDS.putAll(keywords);
        return keywords;
    }

    /**
     * Tests whether this token is one of the declaration modifiers recognized by the parser.
     *
     * @return whether this token is a supported modifier
     */
    public boolean isModifier() {
        return switch (this) {
            case ABSTRACT_KEYWORD, FINAL_KEYWORD, NATIVE_KEYWORD, PRIVATE_KEYWORD, PROTECTED_KEYWORD,
                PUBLIC_KEYWORD, STATIC_KEYWORD, STRICTFP_KEYWORD, SYNCHRONIZED_KEYWORD, TRANSIENT_KEYWORD,
                VOLATILE_KEYWORD, DEFAULT_KEYWORD -> true;
            default -> false;
        };
    }

    /**
     * Tests whether this token is a simple or compound assignment operator.
     *
     * @return whether this token performs assignment
     */
    public boolean isAssignmentOperator() {
        return switch (this) {
            case EQUALS, PLUS_EQUALS, MINUS_EQUALS, STAR_EQUALS, SLASH_EQUALS, PERCENT_EQUALS,
                AMPERSAND_EQUALS, PIPE_EQUALS, CARET_EQUALS, LEFT_SHIFT_EQUALS, RIGHT_SHIFT_EQUALS,
                UNSIGNED_RIGHT_SHIFT_EQUALS -> true;
            default -> false;
        };
    }

    /**
     * Tests whether this token represents a null, boolean, numeric, character, or string literal.
     *
     * @return whether this token is a literal
     */
    public boolean isLiteral() {
        return switch (this) {
            case NULL_LITERAL, BOOLEAN_LITERAL, NUMBER_INT_LITERAL, NUMBER_HEXADECIMAL_LITERAL,
                NUMBER_BINARY_LITERAL, NUMBER_OCTAL_LITERAL, NUMBER_FLOATING_POINT_LITERAL,
                CHARACTER_LITERAL, STRING_LITERAL, TEXT_BLOCK_LITERAL -> true;
            default -> false;
        };
    }
}
