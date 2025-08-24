package dev.railroadide.railroad.ide.sst.ast.expression;

import com.google.gson.Gson;
import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Pattern;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record InstanceofExpression(
        Span span,
        Expression expression,
        Pattern pattern
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.INSTANCEOF_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression, pattern);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitInstanceofExpression(this);
    }
}
