package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxNode;

import java.util.Objects;
import java.util.Optional;

abstract class RedElement implements SyntaxNode {
    private final GreenElement green;
    private final RedNode parent;
    private final int start;

    protected RedElement(GreenElement green, RedNode parent, int start) {
        this.green = Objects.requireNonNull(green, "green");
        if (start < 0)
            throw new IllegalArgumentException("start cannot be negative");

        this.parent = parent;
        this.start = start;
    }

    @Override
    public SyntaxKind kind() {
        return green.kind();
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return start + green.width();
    }

    @Override
    public Optional<SyntaxNode> parent() {
        return Optional.ofNullable(parent);
    }

    protected GreenElement green() {
        return green;
    }
}
