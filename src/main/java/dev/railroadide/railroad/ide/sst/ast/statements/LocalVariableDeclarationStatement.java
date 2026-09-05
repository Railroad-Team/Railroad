package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.VariableDeclarator;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * A local declaration sharing a type and modifiers across one or more variables.
 *
 * @param span source range occupied by this node
 * @param annotations annotations attached to this node
 * @param modifiers modifiers attached to the declaration
 * @param type shared declared or inferred type of the local variables
 * @param isVar whether the declaration uses local variable type inference
 * @param declarations variable declarators in source order
 */
public record LocalVariableDeclarationStatement(
    Span span,
    List<Annotation> annotations,
    List<Modifier> modifiers,
    TypeRef type,
    boolean isVar,
    List<VariableDeclarator> declarations
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.LOCAL_VARIABLE_DECLARATION_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(modifiers);
        children.add(type);
        children.addAll(declarations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLocalVariableDeclarationStatement(this);
    }
}
