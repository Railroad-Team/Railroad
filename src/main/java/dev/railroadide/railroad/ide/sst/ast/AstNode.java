package dev.railroadide.railroad.ide.sst.ast;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base contract for every Java AST node.
 * <p>
 * AST nodes represent language-level constructs rather than raw parser productions.
 * Implementations are immutable value objects, usually records, that expose:
 * <ul>
 *     <li>{@link #kind()} for the broad node category</li>
 *     <li>{@link #span()} for source coordinates</li>
 *     <li>{@link #children()} for generic traversal</li>
 *     <li>{@link #accept(AstVisitor)} for typed traversal</li>
 * </ul>
 * <p>
 * Prefer {@link #accept(AstVisitor)} when you want type-safe logic over specific node
 * kinds. Prefer {@link #children()} when writing generic walkers, printers, or utilities.
 */
public interface AstNode {
    /**
     * Returns the coarse-grained node kind.
     *
     * @return the node kind
     */
    AstKind kind();

    /**
     * Returns the source span that produced this node.
     *
     * @return the source span for this node
     */
    Span span();

    /**
     * Returns child nodes in source order.
     *
     * @return immutable child nodes in source order
     */
    List<AstNode> children();

    /**
     * Dispatches to the matching visitor method for this node type.
     *
     * @param visitor typed AST visitor
     * @param <R> visitor result type
     * @return the value returned by the specific visitor method
     * @throws NullPointerException if {@code visitor} is {@code null}
     */
    <R> R accept(@NotNull AstVisitor<R> visitor);
}
