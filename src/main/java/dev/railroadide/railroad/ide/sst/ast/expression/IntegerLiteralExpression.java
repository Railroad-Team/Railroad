package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An integer literal retaining its spelling, numeric value, radix, and long-type flag.
 *
 * @param span source range occupied by this node
 * @param rawValue original literal spelling
 * @param value numeric value stored as a long
 * @param base radix used to interpret the integer literal
 * @param isLong whether the literal has long type
 */
public record IntegerLiteralExpression(
    Span span,
    String rawValue,
    long value,
    int base,
    boolean isLong
) implements LiteralExpression {
    @Override
    public AstKind kind() {
        return AstKind.INTEGER_LITERAL;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitIntegerLiteral(this);
    }
}
