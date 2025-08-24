package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record MethodInvocationExpression(
        Span span,
        Optional<Expression> scope,
        List<TypeParameter> typeArguments,
        Name methodName,
        List<Expression> arguments
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.METHOD_INVOCATION_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        scope.ifPresent(children::add);
        children.addAll(typeArguments);
        children.add(methodName);
        children.addAll(arguments);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitMethodInvocationExpression(this);
    }
}
