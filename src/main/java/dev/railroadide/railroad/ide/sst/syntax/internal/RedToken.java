package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;

public final class RedToken extends RedElement implements SyntaxToken {
    public RedToken(GreenToken green, RedNode parent, int start) {
        super(green, parent, start);
    }

    @Override
    public String text() {
        return ((GreenToken) green()).text();
    }
}
