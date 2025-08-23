package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record TypeVariableRef(Span span, String name) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.TYPE_VARIABLE;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitTypeVariable(this);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
