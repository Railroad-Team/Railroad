package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ArrayTypeRef(Span span, TypeRef elementType, int dimensions) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.ARRAY_TYPE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(elementType);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitArrayType(this);
    }
}
