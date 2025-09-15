package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LexerToken;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AssignmentExpression(
        Span span,
        Expression left,
        LexerToken<JavaTokenType> operator,
        Expression right) implements Expression {
    public static boolean isValidLeftHandSide(Expression left) {
        return left instanceof NameExpression || left instanceof FieldAccessExpression || left instanceof ArrayAccessExpression;
    }

    @Override
    public AstKind kind() {
        return AstKind.ASSIGNMENT_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, operator, right);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAssignmentExpression(this);
    }
}
