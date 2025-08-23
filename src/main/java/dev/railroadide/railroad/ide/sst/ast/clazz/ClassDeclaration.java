package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import org.freedesktop.dbus.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ClassDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        Name name,
        List<TypeParameter> typeParameters,
        Optional<TypeRef> extendsType,
        List<TypeRef> implementsTypes,
        List<ClassMember> members
) implements TypeDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.CLASS_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(typeParameters);
        extendsType.ifPresent(children::add);
        children.addAll(implementsTypes);
        children.addAll(members);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitClassDeclaration(this);
    }
}
