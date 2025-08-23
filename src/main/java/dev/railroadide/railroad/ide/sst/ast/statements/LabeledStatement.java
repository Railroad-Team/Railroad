package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LabeledStatement(Span span, String label, Statement statement) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.LABELED_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(statement);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLabeledStatement(this);
    }
}
