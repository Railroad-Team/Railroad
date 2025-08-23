package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record AnnotationTypeDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        Name name,
        List<AnnotationMember> members
) implements TypeDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.ANNOTATION_TYPE_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(members);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAnnotationTypeDeclaration(this);
    }
}
