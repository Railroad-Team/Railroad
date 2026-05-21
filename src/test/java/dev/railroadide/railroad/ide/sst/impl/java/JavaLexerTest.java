package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.lexer.Lexer.LexError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaLexerTest {

    @Test
    void lexesSignificantTokensInOrder() {
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
                        JavaTokenType.EOF
                ),
                significantTypes
        );
    }

    @Test
    void classifiesLineAndBlockCommentsAsTrivia() {
        String source = "class A { // line comment\n/* block comment */ int x; }";
        List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(source);

        assertTrue(tokens.stream().anyMatch(token ->
                token.type() == JavaTokenType.LINE_COMMENT && token.channel() == TokenChannel.TRIVIA
        ));
        assertTrue(tokens.stream().anyMatch(token ->
                token.type() == JavaTokenType.BLOCK_COMMENT && token.channel() == TokenChannel.TRIVIA
        ));
    }

    @Test
    void reportsDiagnosticAndErrorFlagForUnterminatedString() {
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
}
