package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ElementValueArray(Span span, List<ElementValue> values) implements ElementValue {
    @Override
    public AstKind kind() {
        return AstKind.ELEMENT_VALUE_ARRAY;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(values);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitElementValueArray(this);
    }
}
