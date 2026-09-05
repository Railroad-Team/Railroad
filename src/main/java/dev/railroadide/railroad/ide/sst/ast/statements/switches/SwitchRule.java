package dev.railroadide.railroad.ide.sst.ast.statements.switches;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.statements.Statement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A switch rule pairing one or more labels with a statement body.
 *
 * @param span source range occupied by this node
 * @param labels labels selecting this rule
 * @param body statement executed when a label matches
 */
public record SwitchRule(Span span, List<SwitchLabel> labels, Statement body) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.SWITCH_RULE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(labels);
        children.add(body);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSwitchRule(this);
    }
}
