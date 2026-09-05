package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A floating-point literal retaining its source spelling, numeric value, and precision flag.
 *
 * @param span source range occupied by this node
 * @param rawValue original literal spelling
 * @param value numeric value stored as a double
 * @param isFloat whether the literal has float precision
 */
public record FloatingPointLiteralExpression(
    Span span,
    String rawValue,
    double value,
    boolean isFloat
) implements LiteralExpression {
    @Override
    public AstKind kind() {
        return AstKind.FLOATING_POINT_LITERAL;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitFloatingPointLiteral(this);
    }
}
