package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A wildcard type argument with an optional extends or super bound.
 *
 * @param span source range occupied by this node
 * @param variance wildcard bound direction
 * @param bound optional wildcard bound
 */
public record WildcardTypeRef(
    Span span,
    Variance variance,
    Optional<TypeRef> bound
) implements TypeRef {
    @Override
    public AstKind kind() {
        return AstKind.WILDCARD_TYPE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        bound.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitWildcardType(this);
    }

    /**
     * Identifies whether a wildcard has an upper bound, lower bound, or no bound.
     */
    public enum Variance {
        /**
         * A lower-bounded wildcard.
         */
        SUPER,
        /**
         * An upper-bounded wildcard.
         */
        EXTENDS,
        /**
         * A wildcard without an explicit bound.
         */
        UNBOUNDED
    }
}
