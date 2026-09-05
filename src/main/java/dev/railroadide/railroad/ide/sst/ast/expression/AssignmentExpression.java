package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LexerToken;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An assignment or compound assignment to a target expression.
 *
 * @param span source range occupied by this node
 * @param left assignment target expression
 * @param operator operator token
 * @param right value assigned to or combined with the target
 */
public record AssignmentExpression(
    Span span,
    Expression left,
    LexerToken<JavaTokenType> operator,
    Expression right
) implements Expression {
    /**
     * Checks whether an expression has a syntactic form permitted as an assignment target.
     *
     * @param left left operand
     * @return whether the expression is a name, field access, or array access
     */
    public static boolean isValidLeftHandSide(Expression left) {
        return left instanceof NameExpression || left instanceof FieldAccessExpression
            || left instanceof ArrayAccessExpression;
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
