package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Positioned syntax node that lazily materializes and caches its child views.
 */
public final class RedNode extends RedElement {
    private volatile List<SyntaxNode> children;

    /**
     * Creates a positioned syntax view over the supplied green element.
     *
     * @param green the immutable backing element
     * @param parent the containing syntax node, or {@code null} for a root
     * @param start the nonnegative absolute UTF-16 start offset
     */
    public RedNode(GreenNode green, RedNode parent, int start) {
        super(green, parent, start);
    }

    @Override
    public List<SyntaxNode> children() {
        return children(() -> {
        });
    }

    /**
     * Returns stable child views while cooperatively checking request activity.
     * The callback runs before cache lookups, including after acquiring the cache lock.
     * Construction polls the callback's work budget for each child. Callback exceptions
     * propagate; an interrupted build leaves its
     * partial list unpublished so a later active caller can retry.
     *
     * <p>
     * The completed immutable list is shared with {@link #children()}. The callback
     * is used only for this call and is never retained by the node or its cached children.
     * Activity checks do not reserve execution time or guarantee continued activity.
     *
     * @param activityCheck request activity check, invoked on the calling thread
     * @return the cached immutable child list, building it if necessary
     * @throws NullPointerException if the callback is {@code null}
     */
    public List<SyntaxNode> children(Runnable activityCheck) {
        Objects.requireNonNull(activityCheck, "activityCheck");
        activityCheck.run();

        List<SyntaxNode> cached = children;
        if (cached != null)
            return cached;

        synchronized (this) {
            activityCheck.run();
            if (children != null)
                return children;

            children = buildChildren(activityCheck);
            return children;
        }
    }

    private List<SyntaxNode> buildChildren(Runnable activityCheck) {
        Objects.requireNonNull(activityCheck, "activityCheck");

        GreenNode greenNode = (GreenNode) green();
        List<GreenElement> greenChildren = greenNode.children();
        List<SyntaxNode> built = new ArrayList<>(greenChildren.size());

        int childStart = start();
        for (GreenElement child : greenChildren) {
            ActivityCheckpoint.poll(activityCheck);

            built.add(RedFactory.create(child, this, childStart));
            childStart += child.width();
        }

        return List.copyOf(built);
    }
}
