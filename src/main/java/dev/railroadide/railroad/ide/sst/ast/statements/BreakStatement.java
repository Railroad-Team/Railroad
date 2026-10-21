package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A break from an enclosing loop or switch, optionally targeting a label.
 *
 * @param span source range occupied by this node
 * @param label optional target label; empty for the innermost enclosing loop or switch
 */
public record BreakStatement(
    Span span,
    Optional<NameExpression> label
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.BREAK_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        label.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitBreakStatement(this);
    }
}
