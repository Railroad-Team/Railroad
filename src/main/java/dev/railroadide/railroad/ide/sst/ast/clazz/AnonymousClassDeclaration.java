package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The body declarations of an anonymous class created within an expression.
 *
 * @param span source range occupied by this node
 * @param bodyDeclarations declarations in the type body
 */
public record AnonymousClassDeclaration(Span span, List<ClassBodyDeclaration> bodyDeclarations) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.ANONYMOUS_CLASS_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(bodyDeclarations);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAnonymousClassDeclaration(this);
    }
}
