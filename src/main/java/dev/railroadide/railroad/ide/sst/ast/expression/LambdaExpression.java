package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LambdaBody;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A lambda expression with declared or inferred parameters and an expression or block body.
 *
 * @param span source range occupied by this node
 * @param parameters formal parameters in declaration order
 * @param inferredParameters whether parameter types are inferred
 * @param body expression or block implementing the lambda
 */
public record LambdaExpression(
    Span span,
    List<Parameter> parameters,
    boolean inferredParameters,
    LambdaBody body
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.LAMBDA_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(parameters);
        children.add(body);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLambdaExpression(this);
    }
}
