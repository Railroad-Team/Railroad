package dev.railroadide.railroad.debug.model;

/**
 * Identifies a stack frame within one suspension; resuming invalidates the identifier.
 *
 * @param suspensionGeneration generation of the suspension that owns this frame
 * @param threadId JDI unique identifier of the owning thread
 * @param frameIndex zero-based position in the thread stack
 */
public record DebugFrameId(
    long suspensionGeneration,
    long threadId,
    int frameIndex
) {
}
