package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

@ApiStatus.Internal
public final class GreenToken extends GreenElement {
    private final String text;

    public GreenToken(SyntaxKind kind, String text) {
        super(kind, normalize(text).length());
        this.text = normalize(text);
    }

    public String text() {
        return text;
    }

    private static String normalize(String text) {
        return Objects.requireNonNullElse(text, "");
    }
}
