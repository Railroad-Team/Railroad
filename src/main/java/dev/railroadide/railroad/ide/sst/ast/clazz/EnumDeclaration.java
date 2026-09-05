package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An enum declaration containing its constants, implemented interfaces, and additional members.
 *
 * @param span source range occupied by this node
 * @param modifiers modifiers attached to the declaration
 * @param annotations annotations attached to this node
 * @param name declared enum type name
 * @param implementedInterfaces implemented interface types
 * @param constants enum constants in declaration order
 * @param bodyDeclarations declarations in the type body
 */
public record EnumDeclaration(
    Span span,
    List<Modifier> modifiers,
    List<Annotation> annotations,
    NameExpression name,
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
