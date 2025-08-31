package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.AnnotationElement;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record NormalAnnotation(Span span, Name name, List<AnnotationElement> elements) implements Annotation {
    @Override
    public AstKind kind() {
        return AstKind.NORMAL_ANNOTATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(name);
        children.addAll(elements);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitNormalAnnotation(this);
    }
}
