package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record MarkerAnnotation(
        Span span,
        Name name
) implements Annotation {
    @Override
    public AstKind kind() {
        return AstKind.MARKER_ANNOTATION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(name);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitMarkerAnnotation(this);
    }
}
