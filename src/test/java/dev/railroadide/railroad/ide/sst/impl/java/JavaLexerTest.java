package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer.LexError;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaLexerTest {

    @Test
    public void lexesSignificantTokensInOrder() {
        String source = "package demo;\nclass Sample { int x = 42; }\n";
        List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(source);

        List<JavaTokenType> significantTypes = tokens.stream()
            .filter(token -> token.channel() == TokenChannel.DEFAULT)
            .map(Token::type)
            .toList();

        assertIterableEquals(
            List.of(
                JavaTokenType.PACKAGE_KEYWORD,
                JavaTokenType.IDENTIFIER,
                JavaTokenType.SEMICOLON,
                JavaTokenType.CLASS_KEYWORD,
                JavaTokenType.IDENTIFIER,
                JavaTokenType.OPEN_BRACE,
                JavaTokenType.INT_KEYWORD,
                JavaTokenType.IDENTIFIER,
                JavaTokenType.EQUALS,
                JavaTokenType.NUMBER_INT_LITERAL,
                JavaTokenType.SEMICOLON,
                JavaTokenType.CLOSE_BRACE,
                JavaTokenType.EOF),
            significantTypes);
    }

    @Test
    public void classifiesLineAndBlockCommentsAsTrivia() {
        String source = "class A { // line comment\n/* block comment */ int x; }";
        List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(source);

        assertTrue(tokens.stream()
            .anyMatch(token -> token.type() == JavaTokenType.LINE_COMMENT && token.channel() == TokenChannel.TRIVIA));
        assertTrue(tokens.stream()
            .anyMatch(token -> token.type() == JavaTokenType.BLOCK_COMMENT && token.channel() == TokenChannel.TRIVIA));
    }

    @Test
    public void lexesRightShiftOperatorsIncludingAtEndOfInput() {
        List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(">> >>> >>= >>>=");

        List<JavaTokenType> significantTypes = tokens.stream()
            .filter(token -> token.channel() == TokenChannel.DEFAULT)
            .map(Token::type)
            .toList();

        assertIterableEquals(List.of(
            JavaTokenType.RIGHT_SHIFT,
            JavaTokenType.UNSIGNED_RIGHT_SHIFT,
            JavaTokenType.RIGHT_SHIFT_EQUALS,
            JavaTokenType.UNSIGNED_RIGHT_SHIFT_EQUALS,
            JavaTokenType.EOF), significantTypes);
    }

    @Test
    public void reportsDiagnosticAndErrorFlagForUnterminatedString() {
        String source = "class A { String s = " + '"' + "unterminated; }";
        List<Token<JavaTokenType>> tokens;
        List<LexError> diagnostics;
        try (JavaLexer lexer = new JavaLexer(source)) {
            tokens = JavaParserTestSupport.lexAll(lexer);
            diagnostics = lexer.diagnostics();
        }

        assertFalse(diagnostics.isEmpty(), "Expected lexer diagnostics for unterminated string");
        assertTrue(tokens.stream().anyMatch(token -> token.flags().contains(TokenFlag.ERROR)));
    }

    @Test
    public void lexesUnicodeEscapedIdentifiersAndKeywordsWithTheirRawText() {
        List<Token<JavaTokenType>> tokens = significant(
            "\\u0069nt \\u004Cist = S\\uu0065t; \\\\u0041 \\u00zz");

        assertEquals(JavaTokenType.INT_KEYWORD, tokens.get(0).type());
        assertEquals("\\u0069nt", tokens.get(0).lexeme());
        assertEquals(JavaTokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("\\u004Cist", tokens.get(1).lexeme());
        assertEquals(JavaTokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("S\\uu0065t", tokens.get(3).lexeme());
        assertEquals(JavaTokenType.SEMICOLON, tokens.get(4).type());
        assertEquals(List.of(JavaTokenType.UNKNOWN, JavaTokenType.UNKNOWN, JavaTokenType.IDENTIFIER),
            tokens.subList(5, 8).stream().map(Token::type).toList(),
            "A backslash after an odd number of backslashes does not start an escape");
        assertEquals(List.of(JavaTokenType.UNKNOWN, JavaTokenType.IDENTIFIER, JavaTokenType.EOF),
            tokens.subList(8, 11).stream().map(Token::type).toList(), "Malformed escapes are not translated");
    }

    @Test
    public void translatesUnicodeEscapesLikeTheCompiler() {
        assertEquals("List", JavaUnicodeEscapes.translate("\\u004Cist"));
        assertEquals("Set", JavaUnicodeEscapes.translate("S\\uuu0065t"));
        assertEquals("\\\\u0041", JavaUnicodeEscapes.translate("\\\\u0041"));
        assertEquals("\\\\A", JavaUnicodeEscapes.translate("\\\\\\u0041"));
        assertEquals("\\u0041", JavaUnicodeEscapes.translate("\\u005cu0041"),
            "A translated backslash never starts another escape");
        assertEquals("\\u12", JavaUnicodeEscapes.translate("\\u12"));
    }

    private static List<Token<JavaTokenType>> significant(String source) {
        return JavaParserTestSupport.lexAll(source).stream()
            .filter(token -> token.channel() == TokenChannel.DEFAULT)
            .toList();
    }
}
