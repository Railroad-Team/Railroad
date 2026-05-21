package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

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

    public SyntaxKind kind() {
        return kind;
    }

    public int width() {
        return width;
    }
}
