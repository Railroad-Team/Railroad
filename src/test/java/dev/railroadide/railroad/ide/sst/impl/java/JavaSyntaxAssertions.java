package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class JavaSyntaxAssertions {
    private JavaSyntaxAssertions() {
    }

    static JavaSyntaxParser.ParseResult assertParsesWithoutRecovery(String source) {
        JavaSyntaxParser.ParseResult result = JavaSyntaxParser.parseWithDiagnostics(source);
        assertTrue(
                result.diagnostics().isEmpty(),
                () -> "Expected no syntax diagnostics, got: " + result.diagnostics()
        );
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
        return result;
    }

    static JavaSyntaxParser.ParseResult assertParsesWithRecovery(String source) {
        JavaSyntaxParser.ParseResult result = JavaSyntaxParser.parseWithDiagnostics(source);
        assertFalse(
                result.diagnostics().isEmpty(),
                "Expected syntax diagnostics for recovery input"
        );
        assertEquals(source, JavaParserTestSupport.syntaxText(result.tree()));
        return result;
    }

    static SyntaxTree assertRoundTrip(String source) {
        SyntaxTree tree = JavaSyntaxParser.parse(source);
        assertEquals(source, JavaParserTestSupport.syntaxText(tree));
        return tree;
    }
}
