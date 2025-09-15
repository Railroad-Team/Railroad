package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.ElementValue;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AnnotationElement(
        Span span,
        NameExpression name,
        ElementValue value
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.ANNOTATION_ELEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(name, value);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAnnotationElement(this);
    }
}
