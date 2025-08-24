package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record LambdaExpression(
        Span span,
        List<Parameter> parameters,
        boolean inferredParameters,
        Optional<TypeRef> returnType,
        Expression body
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.LAMBDA_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(parameters);
        returnType.ifPresent(children::add);
        children.add(body);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLambdaExpression(this);
    }
}
