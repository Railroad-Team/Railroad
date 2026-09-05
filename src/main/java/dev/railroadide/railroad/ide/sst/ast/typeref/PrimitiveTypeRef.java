package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LexerToken;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A type reference represented by a primitive keyword token.
 *
 * @param span source range occupied by this node
 * @param keyword primitive type keyword
 */
public record PrimitiveTypeRef(Span span, LexerToken<JavaTokenType> keyword) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.PRIMITIVE_TYPE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(keyword);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitPrimitiveType(this);
    }
}
