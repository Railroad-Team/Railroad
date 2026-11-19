package dev.railroadide.railroad.ide.sst.request.api;

import java.util.Objects;

/**
 * A feature payload associated with the exact request that produced it.
 * The retained context exposes the captured workspace and its input versions. Document
 * requests additionally retain their selected document through {@link FeatureRequestContext}.
 * Both document and workspace-only requests use this wrapper.
 * A {@code null} payload preserves providers' unsupported-result
 * convention while retaining the identity of the request that returned it.
 * <p>
 * The context and payload are retained directly. This wrapper performs no deep copy;
 * providers must supply immutable payloads before publishing results to other threads.
 * Cancellation or deadline expiry does not prevent construction or inspection of a
 * historical result, and {@link #belongsTo(RequestContext)} does not check activity.
 * <p>
 * Each feature invocation uses its own context instance. Separate contexts can have equal
 * record components while describing different requests, such as completion at different
 * caret positions in the same document revision. Use {@code belongsTo} to correlate
 * requests; generated record equality is not a request-identity check. The publication
 * owner must also validate request activity and current editor/workspace state before
 * applying a result.
 *
 * @param <T> feature payload type
 * @param context exact context of the request producing this result
 * @param value feature payload, or {@code null} when the provider returns no supported result
 */
public record FeatureResult<T>(RequestContext context, T value) {
    /**
     * Retains a request context and its possibly absent payload without checking activity.
     *
     * @param context exact context of the request producing this result
     * @param value feature payload, which may be {@code null}
     * @throws NullPointerException if {@code context} is {@code null}
     */
    public FeatureResult {
        Objects.requireNonNull(context, "context");
    }

    /**
     * Reports whether the supplied context is the exact instance retained by this result.
     * Component equality is insufficient: two contexts with identical captured inputs and
     * controls still represent separate request invocations. This check remains usable
     * after cancellation or expiry and does not establish permission to publish a result.
     *
     * @param otherContext request context to correlate with this result
     * @return {@code true} only when the supplied context is the retained context instance
     * @throws NullPointerException if {@code otherContext} is {@code null}
     */
    public boolean belongsTo(RequestContext otherContext) {
        Objects.requireNonNull(otherContext, "otherContext");
        return context == otherContext;
    }
}
