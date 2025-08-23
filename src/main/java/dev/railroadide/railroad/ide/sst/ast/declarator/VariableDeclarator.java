package dev.railroadide.railroad.ide.sst.ast.declarator;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record VariableDeclarator(Span span, Name name, Optional<Expression> initExpression) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.VARIABLE_DECLARATOR;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(name);
        initExpression.ifPresent(children::add);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitVariableDeclarator(this);
    }
}
