package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A method call with an optional receiver, explicit type arguments, and argument expressions.
 *
 * @param span source range occupied by this node
 * @param scope optional qualifying expression
 * @param typeArguments explicit type arguments
 * @param methodName name of the invoked method
 * @param arguments argument expressions in source order
 */
public record MethodInvocationExpression(
    Span span,
    Optional<Expression> scope,
    List<TypeRef> typeArguments,
    NameExpression methodName,
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
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitMethodInvocationExpression(this);
    }
}
