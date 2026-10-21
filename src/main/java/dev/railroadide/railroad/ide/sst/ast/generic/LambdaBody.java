package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An expression or statement block forming the body of a lambda.
 *
 * @param span source range occupied by this node
 * @param isExpressionBody whether the body is an expression
 * @param body expression or block node, consistent with the expression-body flag
 */
public record LambdaBody(Span span, boolean isExpressionBody, AstNode body) implements AstNode {
    /**
     * Creates a lambda body containing an expression.
     *
     * @param span source range occupied by this node
     * @param expression expression evaluated as the lambda result
     * @return new expression lambda body
     */
    public static LambdaBody expression(Span span, Expression expression) {
        return new LambdaBody(span, true, expression);
    }

    /**
     * Creates a lambda body containing a statement block.
     *
     * @param span source range occupied by this node
     * @param block lambda block body
     * @return new block lambda body
     */
    public static LambdaBody block(Span span, BlockStatement block) {
        return new LambdaBody(span, false, block);
    }

    @Override
    public AstKind kind() {
        return AstKind.LAMBDA_BODY;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLambdaBody(this);
    }
}
