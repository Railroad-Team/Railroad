package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.expression.NameExpression;
import dev.railroadide.railroad.ide.sst.ast.generic.ClassMember;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * An enum constant with constructor arguments and an optional constant-specific class body.
 *
 * @param span source range occupied by this node
 * @param annotations annotations attached to this node
 * @param name declared enum constant name
 * @param arguments argument expressions in source order
 * @param body constant-specific body declarations, empty when no body is present
 */
public record EnumConstantDeclaration(
    Span span,
    List<Annotation> annotations,
    NameExpression name,
    List<Expression> arguments,
    List<ClassBodyDeclaration> body
) implements ClassMember {
    @Override
    public AstKind kind() {
        return AstKind.ENUM_CONSTANT_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.add(name);
        children.addAll(arguments);
        children.addAll(body);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitEnumConstantDeclaration(this);
    }
}
