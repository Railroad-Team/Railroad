package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A Javadoc comment retained as source text in the AST.
 *
 * @param span source range occupied by this node
 * @param content source text preserved by this node
 */
public record JavadocComment(Span span, String content) implements Comment {
    @Override
    public AstKind kind() {
        return AstKind.JAVADOC_COMMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitJavadocComment(this);
    }
}
