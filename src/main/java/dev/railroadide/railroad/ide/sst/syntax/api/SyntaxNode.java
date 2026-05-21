package dev.railroadide.railroad.ide.sst.syntax.api;

import java.util.List;
import java.util.Optional;

/**
 * Public syntax node API exposed to consumers.
 * <p>
 * This is the primary tree API used by inspections. Nodes expose parser kinds, source
 * offsets, and parent/child relationships while remaining independent of the internal
 * green/red tree implementation.
 * <p>
 * Contract:
 * <ul>
 *     <li>offsets are zero-based, half-open ranges {@code [start, end)}</li>
 *     <li>node width is {@code end() - start()}</li>
 *     <li>token nodes have no children</li>
 *     <li>parent/child links are consistent</li>
 *     <li>child ranges are contiguous and fully cover the parent range</li>
 * </ul>
 */
public interface SyntaxNode {
    /**
     * Returns the stable parser kind for this node.
     *
     * @return the parser kind
     */
    SyntaxKind kind();

    /**
     * Returns the inclusive start offset in the source document.
     *
     * @return the inclusive start offset
     */
    int start();

    /**
     * Returns the exclusive end offset in the source document.
     *
     * @return the exclusive end offset
     */
    int end();

    /**
     * Returns child nodes in source order.
     *
     * @return immutable child nodes in source order
     */
    List<SyntaxNode> children();

    /**
     * Returns the parent node when one exists.
     *
     * @return the parent node, if this is not the root
     */
    Optional<SyntaxNode> parent();

    /**
     * Returns {@code Math.max(0, end() - start())}.
     *
     * @return the node width in characters
     */
    default int width() {
        return Math.max(0, end() - start());
    }
}
