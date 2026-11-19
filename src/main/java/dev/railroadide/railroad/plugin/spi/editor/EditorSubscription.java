package dev.railroadide.railroad.plugin.spi.editor;

/** A disposable editor observation. Closing is idempotent and cancels queued delivery. */
@FunctionalInterface
public interface EditorSubscription extends AutoCloseable {
    @Override
    void close();
}
