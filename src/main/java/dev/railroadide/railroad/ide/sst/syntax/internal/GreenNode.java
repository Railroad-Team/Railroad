package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
public final class GreenNode extends GreenElement {
    private final List<GreenElement> children;

    public GreenNode(SyntaxKind kind, List<? extends GreenElement> children) {
        super(kind, sumWidths(children));
        Objects.requireNonNull(children, "children");
        this.children = List.copyOf(children);
    }

    public List<GreenElement> children() {
        return children;
    }

    private static int sumWidths(List<? extends GreenElement> children) {
        Objects.requireNonNull(children, "children");
        int sum = 0;
        for (GreenElement child : children) {
            Objects.requireNonNull(child, "children cannot contain nulls");
            sum += child.width();
        }

        return sum;
    }

    public static GreenNode root(List<? extends GreenElement> children) {
        return new GreenNode(SyntaxKind.ROOT, children);
    }
}
