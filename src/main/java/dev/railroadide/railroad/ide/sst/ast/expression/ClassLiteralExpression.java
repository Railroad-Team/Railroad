package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A class literal naming a type followed by {@code .class}.
 *
 * @param span source range occupied by this node
 * @param type type preceding the class literal suffix
 */
public record ClassLiteralExpression(Span span, TypeRef type) implements LiteralExpression {
    @Override
    public AstKind kind() {
        return AstKind.CLASS_LITERAL;
    }

    @Override
    public List<AstNode> children() {
        return List.of(type);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitClassLiteral(this);
    }
}
