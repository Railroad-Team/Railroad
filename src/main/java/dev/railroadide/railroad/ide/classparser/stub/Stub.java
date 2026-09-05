package dev.railroadide.railroad.ide.classparser.stub;

/**
 * Common contract for named declarations parsed from class files.
 */
public interface Stub {
    /**
     * Returns the declaration name used by this stub.
     *
     * @return the declaration name; constructors use {@code <init>}
     */
    String name();
}
