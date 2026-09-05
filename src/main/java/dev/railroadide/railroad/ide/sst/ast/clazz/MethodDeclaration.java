package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.parameter.Parameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.ReceiverParameter;
import dev.railroadide.railroad.ide.sst.ast.parameter.TypeParameter;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A method declaration with its full signature and optional implementation body.
 *
 * @param span source range occupied by this node
 * @param annotations annotations attached to this node
 * @param modifiers modifiers attached to the declaration
 * @param typeParameters declared type parameters
 * @param returnType declared method return type
 * @param name declared method name
 * @param receiverParameter optional explicit receiver parameter
 * @param parameters formal parameters in declaration order
 * @param thrownTypes declared exception types
 * @param body optional method implementation block
 */
public record MethodDeclaration(
    Span span,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    List<TypeParameter> typeParameters,
    TypeRef returnType,
    NameExpression name,
    Optional<ReceiverParameter> receiverParameter,
    List<Parameter> parameters,
    List<TypeRef> thrownTypes,
    Optional<BlockStatement> body
) implements ClassMember {
    @Override
    public AstKind kind() {
        return AstKind.METHOD_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(typeParameters);
        children.add(returnType);
        children.add(name);
        receiverParameter.ifPresent(children::add);
        children.addAll(parameters);
        children.addAll(thrownTypes);
        body.ifPresent(children::add);

        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitMethodDeclaration(this);
    }
}
