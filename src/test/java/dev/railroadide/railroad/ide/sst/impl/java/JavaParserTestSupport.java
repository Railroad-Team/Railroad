package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;

import java.util.ArrayList;
import java.util.List;

final class JavaParserTestSupport {
    private JavaParserTestSupport() {
    }

    static List<Token<JavaTokenType>> lexAll(String source) {
        try (JavaLexer lexer = new JavaLexer(source)) {
            return lexAll(lexer);
        }
    }

    static List<Token<JavaTokenType>> lexAll(JavaLexer lexer) {
        List<Token<JavaTokenType>> tokens = new ArrayList<>();
        while (true) {
            Token<JavaTokenType> token = lexer.nextToken();
            tokens.add(token);
            if (isEofToken(token))
                return List.copyOf(tokens);
        }
    }

    static String syntaxText(SyntaxTree tree) {
        return syntaxText(tree.root());
    }

    static String syntaxText(SyntaxNode node) {
        if (node instanceof SyntaxToken token)
            return token.text();

        StringBuilder builder = new StringBuilder();
        for (SyntaxNode child : node.children()) {
            builder.append(syntaxText(child));
        }
        return builder.toString();
    }

    static List<SyntaxToken> collectSyntaxTokens(SyntaxTree tree) {
        return collectSyntaxTokens(tree.root());
    }

    static List<SyntaxToken> collectSyntaxTokens(SyntaxNode root) {
        List<SyntaxToken> tokens = new ArrayList<>();
        collectSyntaxTokens(root, tokens);
        return List.copyOf(tokens);
    }

    static boolean isEofToken(Token<JavaTokenType> token) {
        return token.type() == JavaTokenType.EOF || token.flags().contains(TokenFlag.EOF);
    }

    static boolean isSyntaxEofToken(SyntaxToken token) {
        return token.kind().id().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.EOF).id());
    }

    private static void collectSyntaxTokens(SyntaxNode node, List<SyntaxToken> out) {
        if (node instanceof SyntaxToken token) {
            out.add(token);
            return;
        }

        for (SyntaxNode child : node.children()) {
            collectSyntaxTokens(child, out);
        }
    }
}
