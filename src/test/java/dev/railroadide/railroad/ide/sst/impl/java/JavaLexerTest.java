package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Lexer.LexError;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenChannel;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public void preservesRawSpellingAndLongestMatchForEscapedHyphens() {
        var tokens = significant("non\\u002dsealed \\u002d- -\\u003e");
        assertEquals(List.of(JavaTokenType.IDENTIFIER, JavaTokenType.MINUS, JavaTokenType.SEALED_KEYWORD,
            JavaTokenType.MINUS_MINUS, JavaTokenType.ARROW, JavaTokenType.EOF),
            tokens.stream().map(Token::type).toList());
        assertEquals(List.of("non", "\\u002d", "sealed", "\\u002d-", "-\\u003e"),
            tokens.subList(0, 5).stream().map(Token::lexeme).toList());
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

    @Test
    public void lexesEveryNumericLiteralFormAsOneToken() {
        Map<String, JavaTokenType> literals = new LinkedHashMap<>();
        for (String literal : List.of("0", "42", "1_000", "1__000", "5L", "0L")) {
            literals.put(literal, JavaTokenType.NUMBER_INT_LITERAL);
        }
        for (String literal : List.of("017", "0_7", "07L")) {
            literals.put(literal, JavaTokenType.NUMBER_OCTAL_LITERAL);
        }
        for (String literal : List.of("0x1F", "0XffL", "0x1_f")) {
            literals.put(literal, JavaTokenType.NUMBER_HEXADECIMAL_LITERAL);
        }
        for (String literal : List.of("0b1010", "0B1_0L")) {
            literals.put(literal, JavaTokenType.NUMBER_BINARY_LITERAL);
        }
        for (String literal : List.of("0.5f", "3.5F", "3.5d", "3.5D", "1_000.5f", "3.5", "1.", "1.f", "1.e3", ".5",
            ".5f", "1f", "1D", "1e3", "1E-3F", "1.0e+3d", "09.5", "07.5", "0e1", "0x1p3f", "0x1.8p1", "0x.8P-1D")) {
            literals.put(literal, JavaTokenType.NUMBER_FLOATING_POINT_LITERAL);
        }

        literals.forEach((literal, type) -> {
            try (var lexer = new JavaLexer("x = " + literal + ";")) {
                List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(lexer).stream()
                    .filter(token -> token.channel() == TokenChannel.DEFAULT)
                    .toList();

                assertEquals(type, tokens.get(2).type(), literal);
                assertEquals(literal, tokens.get(2).lexeme(), literal);
                assertEquals(JavaTokenType.SEMICOLON, tokens.get(3).type(), literal);
                assertEquals(List.of(), lexer.diagnostics(), literal);
            }
        });
    }

    @Test
    public void malformedNumericLiteralsKeepTheirTextInOneErrorToken() {
        for (String literal : List.of("1_", "0x", "0x_1", "0x1.8", "1e", "1e+", "09", "0b2", "1xyz", "5Lf", "1._5")) {
            try (var lexer = new JavaLexer("x = " + literal + ";")) {
                List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(lexer).stream()
                    .filter(token -> token.channel() == TokenChannel.DEFAULT)
                    .toList();

                assertEquals(literal, tokens.get(2).lexeme(), literal);
                assertTrue(tokens.get(2).flags().contains(TokenFlag.ERROR), literal);
                assertEquals(JavaTokenType.SEMICOLON, tokens.get(3).type(), literal);
                assertEquals(1, lexer.diagnostics().size(), literal);
            }
        }
    }

    @Test
    public void suffixedFloatingPointLiteralsSurviveParsing() {
        String source = "class A { float[] values = { 0.5f, 3.5F, 2.0d }; Object o = foo(0.25f, 0x1p3f); }";

        assertEquals(source, JavaParserTestSupport.syntaxText(JavaSyntaxParser.parse(source)));
    }

    @Test
    public void textBlockEscapesKeepTheBlockOpenUntilItsDelimiter() {
        for (String block : List.of("\"\"\"\n    joined \\\n    line\n    \"\"\"",
            "\"\"\"\r\n    joined \\\r\n    line\"\"\"",
            "\"\"\"\n    tab\\t\"\"\"", "\"\"\"\n    quote\\\"\"\"\"")) {
            try (var lexer = new JavaLexer("x = " + block + "; y")) {
                List<Token<JavaTokenType>> tokens = JavaParserTestSupport.lexAll(lexer).stream()
                    .filter(token -> token.channel() == TokenChannel.DEFAULT)
                    .toList();

                assertEquals(JavaTokenType.TEXT_BLOCK_LITERAL, tokens.get(2).type(), block);
                assertEquals(block, tokens.get(2).lexeme(), block);
                assertEquals(JavaTokenType.SEMICOLON, tokens.get(3).type(), block);
                assertEquals(List.of(), lexer.diagnostics(), block);
            }
        }
    }

    private static List<Token<JavaTokenType>> significant(String source) {
        return JavaParserTestSupport.lexAll(source).stream()
            .filter(token -> token.channel() == TokenChannel.DEFAULT)
            .toList();
    }
}
