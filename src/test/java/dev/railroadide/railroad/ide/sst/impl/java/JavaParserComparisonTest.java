package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class JavaParserComparisonTest {

    @Test
    void lexerAndSyntaxTreeExposeSameTokenTextSequence() {
        String source = """
                package demo;
                // a comment
                class A {
                    int x = 1 + 2;
                }
                """;

        List<String> lexerLexemes = JavaParserTestSupport.lexAll(source).stream()
                .filter(token -> !JavaParserTestSupport.isEofToken(token))
                .map(Token::lexeme)
                .toList();

        List<String> syntaxLexemes = JavaParserTestSupport.collectSyntaxTokens(JavaSyntaxParser.parse(source)).stream()
                .filter(token -> !JavaParserTestSupport.isSyntaxEofToken(token))
                .map(SyntaxToken::text)
                .toList();

        assertIterableEquals(lexerLexemes, syntaxLexemes);
    }
}
