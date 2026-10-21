package dev.railroadide.railroad.ide.sst.ast.statements.switches;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.statements.Statement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A switch statement selecting among an ordered set of labeled rules.
 *
 * @param span source range occupied by this node
 * @param selectionExpression switch selector expression
 * @param rule switch rules in source order
 */
public record SwitchStatement(
    Span span,
    Expression selectionExpression,
    List<SwitchRule> rule
) implements Statement {

    @Override
    public AstKind kind() {
        return AstKind.SWITCH_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(selectionExpression);
        children.addAll(rule);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSwitchStatement(this);
    }
}
