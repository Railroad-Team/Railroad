package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.RecordComponent;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record RecordDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        NameExpression name,
        List<TypeParameter> typeParameters,
        List<RecordComponent> components,
        List<TypeRef> implementsTypes,
        List<ClassBodyDeclaration> bodyDeclarations
) implements TypeDeclaration {

    @Override
    public AstKind kind() {
        return AstKind.RECORD_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.add(name);
        children.addAll(typeParameters);
        children.addAll(components);
        children.addAll(implementsTypes);
        children.addAll(bodyDeclarations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitRecordDeclaration(this);
    }
}
