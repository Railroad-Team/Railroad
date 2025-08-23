package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record UnionTypeRef(
        Span span,
        TypeRef left,
        TypeRef right
) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.UNION_TYPE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, right);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitUnionType(this);
    }
}
