package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Immutable syntax element storing its kind and text width independently of source position and parent links.
 */
@ApiStatus.Internal
public sealed abstract class GreenElement permits GreenNode, GreenToken {
    private final SyntaxKind kind;
    private final int width;

    protected GreenElement(SyntaxKind kind, int width) {
        this.kind = Objects.requireNonNull(kind, "kind");
        if (width < 0)
            throw new IllegalArgumentException("width cannot be negative");

        this.width = width;
    }

    /**
     * Returns the syntax category of this element.
     *
     * @return the element's syntax kind
     */
    public SyntaxKind kind() {
        return kind;
    }

    /**
     * Returns the total text width without assigning an absolute source position.
     *
     * @return the width in UTF-16 code units
     */
    public int width() {
        return width;
    }
}
