package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.statements.switches.SwitchRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A value-producing switch with a selector expression and ordered rules.
 *
 * @param span source range occupied by this node
 * @param selector switch selector expression
 * @param switchRule switch body and its labels
 */
public record SwitchExpression(
    Span span,
    Expression selector,
    List<SwitchRule> switchRule
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.SWITCH_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(selector);
        children.addAll(switchRule);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSwitchExpression(this);
    }
}
