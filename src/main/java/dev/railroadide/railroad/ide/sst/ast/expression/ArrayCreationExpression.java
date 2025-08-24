package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.ArrayInitializer;
import dev.railroadide.railroad.ide.sst.ast.typeref.ClassOrInterfaceTypeRef;
import dev.railroadide.railroad.ide.sst.ast.typeref.PrimitiveTypeRef;
import dev.railroadide.railroad.utility.Either;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ArrayCreationExpression(
        Span span,
        Either<PrimitiveTypeRef, ClassOrInterfaceTypeRef> type,
        List<Expression> dimensions,
        Optional<ArrayInitializer> initializer
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.ARRAY_CREATION_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(type.map(List::of, List::of));
        children.addAll(dimensions);
        initializer.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitArrayCreationExpression(this);
    }
}
