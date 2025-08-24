package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.utility.Either;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ArrayInitializer(
        Span span,
        List<Either<Expression, ArrayInitializer>> values
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.ARRAY_INITIALIZER;
    }

    @Override
    public List<AstNode> children() {
        List<? extends AstNode> children = values.stream()
                .map(either -> either.map(List::of, List::of))
                .flatMap(List::stream)
                .toList();
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitArrayInitializer(this);
    }
}
