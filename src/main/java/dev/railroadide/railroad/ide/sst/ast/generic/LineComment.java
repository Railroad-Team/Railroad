package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A line comment retained as source text in the AST.
 *
 * @param span source range occupied by this node
 * @param content source text preserved by this node
 */
public record LineComment(Span span, String content) implements Comment {
    @Override
    public AstKind kind() {
        return AstKind.LINE_COMMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitLineComment(this);
    }
}
