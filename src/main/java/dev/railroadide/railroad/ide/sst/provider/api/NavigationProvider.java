package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.DefinitionTarget;
import java.util.List;

/**
 * Resolves source positions to versioned navigation targets without opening an editor.
 * <p>
 * Every target carries the destination revision its location belongs to. That revision is normally captured by the
 * request's workspace, so callers can validate the target against the workspace before navigating. A provider may
 * also target a file-backed read-only view outside the workspace, such as the source of a library class; callers
 * open that file and validate against its text instead. Providers never guess: an unresolved symbol yields no
 * targets, and a symbol with several equally valid declarations yields all of them in rank order (navigable
 * original source before attached, generated or decompiled views, then by address and offset). Callers must
 * offer a choice for more than one target rather than silently taking the first.
 * <p>
 * Methods run on SST workers, must observe the context's cancellation and deadline, and must return immutable
 * payloads.
 */
@FunctionalInterface
public interface NavigationProvider {
    /**
     * Resolves declarations of the symbol at a position in the captured text document.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param offset UTF-16 offset of the source position to resolve
     * @return ranked declaration targets, or an empty list when the position resolves to no navigable declaration
     */
    FeatureResult<List<DefinitionTarget>> definitions(FeatureRequestContext context, int offset);

    /**
     * Resolves implementations of the type or callable at a position: transitive subtypes of a type, or
     * transitive overriding declarations of a method. Providers without implementation facts return no targets.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param offset UTF-16 offset of the source position to resolve
     * @return ranked implementation targets, or an empty list when none are known
     */
    default FeatureResult<List<DefinitionTarget>> implementations(FeatureRequestContext context, int offset) {
        context.checkActive();
        return new FeatureResult<>(context, List.of());
    }

    /**
     * Searches the captured workspace for usages of a symbol. Partial results may be streamed to the sink on the
     * worker thread before the complete result is returned; consumers must correlate them with their request and
     * discard them after cancellation.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param query position or stable symbol whose usages are searched
     * @param sink receiver of incremental batches and progress
     * @return complete search, or a {@code null} value when the symbol is unresolved or the provider cannot search
     */
    default FeatureResult<UsageSearch> usages(FeatureRequestContext context, UsageQuery query, UsageSink sink) {
        context.checkActive();
        return new FeatureResult<>(context, null);
    }
}
