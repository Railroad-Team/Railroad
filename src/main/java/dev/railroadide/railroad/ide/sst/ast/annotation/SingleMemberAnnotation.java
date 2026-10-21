package dev.railroadide.railroad.ide.sst.ast.annotation;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An annotation use supplying a single unnamed value argument.
 *
 * @param span source range occupied by this node
 * @param name annotation type name
 * @param value argument supplied to the annotation's value element
 */
public record SingleMemberAnnotation(
    Span span,
    NameExpression name,
    ElementValue value
) implements Annotation {
    @Override
    public AstKind kind() {
        return AstKind.SINGLE_MEMBER_ANNOTATION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(name, value);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSingleMemberAnnotation(this);
    }
}
