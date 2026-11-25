package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.Token;
import dev.railroadide.railroad.ide.sst.lexer.TokenFlag;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;

public final class JavaParserTestSupport {
    private JavaParserTestSupport() {
    }

    public static List<Token<JavaTokenType>> lexAll(String source) {
        try (JavaLexer lexer = new JavaLexer(source)) {
            return lexAll(lexer);
        }
    }

    public static List<Token<JavaTokenType>> lexAll(JavaLexer lexer) {
        List<Token<JavaTokenType>> tokens = new ArrayList<>();
        while (true) {
            Token<JavaTokenType> token = lexer.nextToken();
            tokens.add(token);
            if (isEofToken(token))
                return List.copyOf(tokens);
        }
    }

    public static String syntaxText(SyntaxTree tree) {
        return syntaxText(tree.root());
    }

    public static String syntaxText(SyntaxNode node) {
        var builder = new StringBuilder(node.width());
        for (SyntaxToken token : collectSyntaxTokens(node)) {
            builder.append(token.text());
        }

        return builder.toString();
    }

    public static List<SyntaxToken> collectSyntaxTokens(SyntaxTree tree) {
        return collectSyntaxTokens(tree.root());
    }

    public static List<SyntaxToken> collectSyntaxTokens(SyntaxNode root) {
        List<SyntaxToken> tokens = new ArrayList<>();
        Deque<SyntaxNode> pending = new ArrayDeque<SyntaxNode>();
        pending.push(root);
        while (!pending.isEmpty()) {
            SyntaxNode node = pending.pop();
            if (node instanceof SyntaxToken token) {
                tokens.add(token);
            } else {
                List<SyntaxNode> children = node.children();
                for (int index = children.size() - 1; index >= 0; index--) {
                    pending.push(children.get(index));
                }
            }
        }

        return List.copyOf(tokens);
    }

    public static boolean isEofToken(Token<JavaTokenType> token) {
        return token.type() == JavaTokenType.EOF || token.flags().contains(TokenFlag.EOF);
    }

    public static boolean isSyntaxEofToken(SyntaxToken token) {
        return token.kind().id().equals(JavaSyntaxKinds.tokenKind(JavaTokenType.EOF).id());
    }
}
