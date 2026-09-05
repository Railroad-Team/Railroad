package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Immutable internal syntax token storing its kind and source text.
 */
@ApiStatus.Internal
public final class GreenToken extends GreenElement {
    private final String text;

    /**
     * Creates a token, treating null text as an empty string.
     *
     * @param kind the token's syntax kind
     * @param text the source text, or {@code null} for empty text
     */
    public GreenToken(SyntaxKind kind, String text) {
        super(kind, normalize(text).length());
        this.text = normalize(text);
    }

    /**
     * Returns the token's source text.
     *
     * @return the nonnull token text
     */
    public String text() {
        return text;
    }

    private static String normalize(String text) {
        return Objects.requireNonNullElse(text, "");
    }
}
