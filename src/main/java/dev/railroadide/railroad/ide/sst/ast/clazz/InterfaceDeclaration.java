package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An interface declaration with generic parameters, extended interfaces, and body declarations.
 *
 * @param span source range occupied by this node
 * @param modifiers modifiers attached to the declaration
 * @param annotations annotations attached to this node
 * @param name declared interface name
 * @param typeParameters declared type parameters
 * @param extendsTypes extended interface types
 * @param declarations contained declarations in source order
 */
public record InterfaceDeclaration(
    Span span,
    List<Modifier> modifiers,
    List<Annotation> annotations,
    NameExpression name,
    List<TypeParameter> typeParameters,
    List<TypeRef> extendsTypes,
    List<ClassBodyDeclaration> declarations
) implements TypeDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.INTERFACE_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(typeParameters);
        children.addAll(extendsTypes);
        children.addAll(declarations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitInterfaceDeclaration(this);
    }
}
