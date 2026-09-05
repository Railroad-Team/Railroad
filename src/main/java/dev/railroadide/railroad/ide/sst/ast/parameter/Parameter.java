package dev.railroadide.railroad.ide.sst.ast.parameter;

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
import java.util.Optional;

/**
 * A formal parameter with an optional type and a variable-arity flag.
 *
 * @param span source range occupied by this node
 * @param modifiers modifiers attached to the declaration
 * @param annotations annotations attached to this node
 * @param type optional declared parameter type, absent for an inferred lambda parameter
 * @param isVarArgs whether this is a variable-arity parameter
 * @param name declared parameter name
 */
public record Parameter(
    Span span,
    List<Modifier> modifiers,
    List<Annotation> annotations,
    Optional<TypeRef> type,
    boolean isVarArgs,
    NameExpression name
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.PARAMETER;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        type.ifPresent(children::add);
        children.add(name);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitParameter(this);
    }
}
