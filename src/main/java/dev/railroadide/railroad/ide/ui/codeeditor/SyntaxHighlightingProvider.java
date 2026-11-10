package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import org.fxmisc.richtext.model.StyleSpans;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Computes CSS style spans for a complete source-text snapshot.
 */
@FunctionalInterface
public interface SyntaxHighlightingProvider {
    /**
     * Computes syntax styling for the supplied source snapshot.
     *
     * @param text complete source text to highlight
     * @return style spans whose collections identify CSS classes for each text range
     */
    StyleSpans<Collection<String>> compute(String text);

    /**
     * Computes syntax styling for a captured text request and identifies its inputs.
     * The default implementation checks activity before and after a normal return from
     * {@link #compute(String)}, preserving existing implementations and lambdas. Binary
     * inputs are rejected because the returned ranges describe text offsets.
     * <p>
     * Provider exceptions, including cancellation, propagate unchanged. These boundary
     * checks do not interrupt work inside an existing provider; long-running providers
     * must cooperate with the common request controls during computation. Publication
     * owners must validate request identity and currentness before applying styles.
     *
     * @param context captured workspace, document, cancellation token, and deadline
     * @return a non-null wrapper retaining the exact context and computed style spans
     * @throws NullPointerException if {@code context} is {@code null}
     * @throws IllegalArgumentException if an active request contains a binary document
     * @throws CancellationException if cancellation is observed or the provider cancels
     * @throws DeadlineExceededException if a boundary check observes expiry
     */
    default @NotNull FeatureResult<StyleSpans<Collection<String>>> compute(FeatureRequestContext context) {
        Objects.requireNonNull(context, "context");
        context.checkActive();
        if (!(context.documentSnapshot() instanceof TextDocumentSnapshot snapshot))
            throw new IllegalArgumentException("Syntax highlighting requires a text document snapshot");

        StyleSpans<Collection<String>> computed = compute(snapshot.text());
        context.checkActive();
        return new FeatureResult<>(context, computed);
    }
}
