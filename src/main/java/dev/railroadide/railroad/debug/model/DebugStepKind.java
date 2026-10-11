package dev.railroadide.railroad.debug.model;

/**
 * Controls how a line step crosses method-call boundaries.
 */
public enum DebugStepKind {
    /**
     * Stops at the next source line, entering called methods when applicable.
     */
    INTO,
    /**
     * Stops at the next source line without stepping into called methods.
     */
    OVER,
    /**
     * Runs until execution returns from the current method.
     */
    OUT;
}
