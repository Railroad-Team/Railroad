package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LambdaBody(Span span, boolean isExpressionBody, AstNode body) implements AstNode {
    public static LambdaBody expression(Span span, Expression expression) {
        return new LambdaBody(span, true, expression);
    }

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
