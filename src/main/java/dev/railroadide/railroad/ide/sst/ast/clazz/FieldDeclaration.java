package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.VariableDeclarator;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A field declaration containing a shared type and one or more variable declarators.
 *
 * @param span source range occupied by this node
 * @param annotations annotations attached to this node
 * @param modifiers modifiers attached to the declaration
 * @param type shared declared type of the field variables
 * @param name name associated with the field declaration
 * @param variableDeclarators variables declared by the field
 */
public record FieldDeclaration(
    Span span,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    TypeRef type,
    NameExpression name,
    List<VariableDeclarator> variableDeclarators
) implements ClassMember, AnnotationMember {
    @Override
    public AstKind kind() {
        return AstKind.FIELD_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(modifiers);
        children.add(type);
        children.add(name);
        children.addAll(variableDeclarators);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitFieldDeclaration(this);
    }
}
