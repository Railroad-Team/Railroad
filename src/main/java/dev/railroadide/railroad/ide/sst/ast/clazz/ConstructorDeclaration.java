package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A constructor declaration with parameters, thrown types, and an optional body.
 *
 * @param span source range occupied by this node
 * @param modifiers modifiers attached to the declaration
 * @param annotations annotations attached to this node
 * @param typeParameters declared type parameters
 * @param name constructor name matching the containing type
 * @param parameters formal parameters in declaration order
 * @param thrownTypes declared exception types
 * @param body optional constructor implementation block
 */
public record ConstructorDeclaration(
    Span span,
    List<Modifier> modifiers,
    List<Annotation> annotations,
    List<TypeParameter> typeParameters,
    NameExpression name,
    List<Parameter> parameters,
    List<TypeRef> thrownTypes,
    Optional<BlockStatement> body
) implements ClassBodyDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.CONSTRUCTOR_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.addAll(typeParameters);
        children.add(name);
        children.addAll(parameters);
        children.addAll(thrownTypes);
        body.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitConstructorDeclaration(this);
    }
}
