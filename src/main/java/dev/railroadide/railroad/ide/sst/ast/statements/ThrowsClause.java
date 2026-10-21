package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.ClassOrInterfaceTypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The exception types declared by a callable's throws clause.
 *
 * @param span source range occupied by this node
 * @param exceptions declared exception type nodes
 */
public record ThrowsClause(
    Span span,
    List<ExceptionType> exceptions
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.THROWS_CLAUSE;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(exceptions);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitThrowsClause(this);
    }

    /**
     * A class or interface type reference listed in a throws clause.
     *
     * @param span source range occupied by this node
     * @param type exception type named in the throws clause
     */
    public record ExceptionType(
        Span span,
        ClassOrInterfaceTypeRef type
    ) implements AstNode {
        @Override
        public AstKind kind() {
            return AstKind.EXCEPTION_TYPE;
        }

        @Override
        public List<AstNode> children() {
            return List.of(type);
        }

        @Override
        public <R> R accept(@NotNull AstVisitor<R> visitor) {
            return visitor.visitExceptionType(this);
        }
    }
}
