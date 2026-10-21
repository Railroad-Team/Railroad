package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A string literal or text block and its stored string value.
 *
 * @param span source range occupied by this node
 * @param value string value stored for the literal or text block
 * @param isTextBlock whether this is a text block
 */
public record StringLiteralExpression(
    Span span,
    String value,
    boolean isTextBlock
) implements LiteralExpression {
    @Override
    public AstKind kind() {
        return AstKind.STRING_LITERAL;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitStringLiteral(this);
    }
}
