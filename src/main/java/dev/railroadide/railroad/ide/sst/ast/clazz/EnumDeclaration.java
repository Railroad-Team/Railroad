package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record EnumDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        Name name,
        List<TypeRef> implementedInterfaces,
        List<EnumConstantDeclaration> constants,
        List<ClassBodyDeclaration> bodyDeclarations
) implements TypeDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.ENUM_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.add(name);
        children.addAll(implementedInterfaces);
        children.addAll(constants);
        children.addAll(bodyDeclarations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitEnumDeclaration(this);
    }
}
