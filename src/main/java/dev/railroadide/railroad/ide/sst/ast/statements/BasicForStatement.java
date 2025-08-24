package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record BasicForStatement(
        Span span,
        Optional<Statement> initStatement,
        Optional<Expression> condition,
        List<Expression> updates,
        Statement body
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.BASIC_FOR_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        initStatement.ifPresent(children::add);
        condition.ifPresent(children::add);
        children.addAll(updates);
        children.add(body);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitBasicForStatement(this);
    }
}
