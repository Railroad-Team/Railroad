package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An annotation element declaration with a return type and optional default expression.
 *
 * @param span source range occupied by this node
 * @param modifiers modifiers attached to the declaration
 * @param annotations annotations attached to this node
 * @param type declared return type of the annotation element
 * @param name declared annotation element name
 * @param defaultValue optional default annotation value
 */
public record AnnotationTypeMemberDeclaration(
    Span span,
    List<Modifier> modifiers,
    List<Annotation> annotations,
    TypeRef type,
    NameExpression name,
    Optional<Expression> defaultValue
) implements AnnotationMember {
    @Override
    public AstKind kind() {
        return AstKind.ANNOTATION_TYPE_MEMBER_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.add(type);
        children.add(name);
        defaultValue.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAnnotationTypeMemberDeclaration(this);
    }
}
