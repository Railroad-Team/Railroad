package dev.railroadide.railroad.ide.sst.provider.api;

import dev.railroadide.railroad.ide.sst.request.api.FeatureRequestContext;
import dev.railroadide.railroad.ide.sst.request.api.FeatureResult;
import dev.railroadide.railroad.ide.sst.semantic.api.SemanticModel;

/** Analyzes a captured text revision without exposing a language's compiler implementation. */
@FunctionalInterface
public interface SemanticProvider {
    /**
     * Computes semantic facts for the captured text revision while observing request activity.
     *
     * @param context captured document, workspace, cancellation token, and deadline
     * @return immutable analysis retaining the input snapshot, or null payload if unsupported
     */
    FeatureResult<SemanticModel> analyze(FeatureRequestContext context);
}
