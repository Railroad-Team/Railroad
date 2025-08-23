package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record AnnotationTypeMemberDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        TypeRef typeRef,
        Name name,
        Optional<Expression> defaultValue
) implements ClassMember {
    @Override
    public AstKind kind() {
        return AstKind.ANNOTATION_TYPE_MEMBER_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.add(typeRef);
        children.add(name);
        defaultValue.ifPresent(children::add);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAnnotationTypeMemberDeclaration(this);
    }
}
