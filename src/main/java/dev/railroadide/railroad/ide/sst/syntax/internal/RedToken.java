package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxToken;

final class RedToken extends RedElement implements SyntaxToken {
    RedToken(GreenToken green, RedNode parent, int start) {
        super(green, parent, start);
    }

    @Override
    public String text() {
        return ((GreenToken) green()).text();
    }
}
