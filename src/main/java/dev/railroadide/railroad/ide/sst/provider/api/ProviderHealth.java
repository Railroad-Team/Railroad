package dev.railroadide.railroad.ide.sst.provider.api;

/**
 * Immutable, bounded aggregate measurements for a provider. Durations use monotonic nanoseconds.
 * A timed-out invocation can remain active until its code actually exits; execution time and slow-call
 * counts are recorded on that exit, while terminal outcomes are recorded when the caller receives them.
 * No document text, snapshots, exception objects, or individual invocation history is retained.
 *
 * @param providerId stable language-neutral provider identity
 * @param succeeded successful responses
 * @param failed failed responses and isolated rule failures reported by provider hosts
 * @param cancelled explicitly cancelled responses
 * @param timedOut expired responses
 * @param rejected responses rejected by an admission limit
 * @param slow completed executions exceeding the configured slow threshold
 * @param active executions that have not actually exited
 * @param totalQueueNanos total dispatch delay before provider code starts
 * @param totalExecutionNanos execution time of calls that have actually exited
 * @param maximumExecutionNanos longest completed execution
 * @param lastFailureType exception class name of the last failure, or an empty string
 * @param lastProjectId project owning the last admitted request
 * @param lastWorkspaceRevision captured workspace revision of the last admitted request
 */
public record ProviderHealth(
    String providerId,
    long succeeded,
    long failed,
    long cancelled,
    long timedOut,
    long rejected,
    long slow,
    int active,
    long totalQueueNanos,
    long totalExecutionNanos,
    long maximumExecutionNanos,
    String lastFailureType,
    String lastProjectId,
    long lastWorkspaceRevision
) {
    /**
     * Indicates that the provider has exceeded a budget, failed, or exhausted its admission allowance.
     * Counters describe the retained aggregate, rather than a claim that a provider is currently unhealthy.
     *
     * @return whether this aggregate contains a provider-health diagnostic
     */
    public boolean hasWarnings() {
        return failed > 0 || timedOut > 0 || rejected > 0 || slow > 0;
    }
}
