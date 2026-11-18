package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;

import java.util.List;

/**
 * Discovers and resolves code actions for a captured document without mutating it.
 * <p>
 * Discovery should be cheap: it may return {@linkplain CodeAction#deferred deferred} actions whose edits are
 * computed by {@link #resolve} only when the user selects them. Providers offer an action only when the captured
 * document and workspace show the problem and its fix with certainty; when a fix a user might expect cannot be
 * made safely, they either offer nothing or an {@linkplain CodeAction#unavailable unavailable} action explaining
 * why.
 * <p>
 * Methods run on SST workers, must observe the context's cancellation and deadline, and must return immutable
 * payloads. Every edit must be bound to the snapshots of the request's captured workspace.
 */
@FunctionalInterface
public interface CodeActionProvider {
    /**
     * Discovers the actions available for a range of the captured document.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @param request caret or selection and the diagnostics shown near it
     * @return actions in display order; an empty list means no applicable actions
     */
    FeatureResult<List<CodeAction>> actions(FeatureRequestContext context, CodeActionRequest request);

    /**
     * Computes the edit of a deferred action. Hosts call this with a context capturing the same workspace as the
     * discovery that returned the action. The default implementation returns the action unchanged, which hosts
     * treat as unsupported when the action has no edit.
     *
     * @param context captured document and workspace of the discovery, with fresh cancellation and deadline
     * @param action action returned by {@link #actions} for that workspace
     * @return the action with its edit, or {@linkplain CodeAction#asUnavailable unavailable} when it no longer
     *         applies
     */
    default FeatureResult<CodeAction> resolve(FeatureRequestContext context, CodeAction action) {
        return new FeatureResult<>(context, action);
    }
}
