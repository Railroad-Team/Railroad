package dev.railroadide.railroad.ide.signature;

import dev.railroadide.railroad.ide.language.impl.index.JavaWorkspaceAnalysis;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import dev.railroadide.railroad.ide.sst.project.JavaProjectSemanticIndex;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded captured source trees; active parsers retain their tree until their lease closes. */
public final class JavaSignatureSourceCache implements AutoCloseable {
    private final int capacity;
    private final LinkedHashMap<Map<Path, String>, Entry> entries = new LinkedHashMap<>(4, 0.75f, true);
    private final Set<Entry> liveEntries = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Thread shutdownHook = new Thread(this::shutdown, "railroad-signature-source-cleanup");
    private boolean closed;

    /**
     * Creates a source-only cache and registers process shutdown cleanup.
     *
     * @param capacity maximum number of reusable trees, excluding evicted trees still being parsed
     */
    public JavaSignatureSourceCache(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("Source cache capacity must be positive");
        this.capacity = capacity;
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    /**
     * Shares an immutable dependency tree. The current compilation unit is excluded so edits to
     * its in-memory buffer do not rewrite the other captured sources on every keystroke.
     *
     * @param analysis captured Java sources
     * @param request current source buffer supplied directly to JDT
     * @return lease keeping the tree readable through parsing
     * @throws IOException if a new tree cannot be staged
     */
    public Lease acquire(JavaWorkspaceAnalysis analysis, TextDocumentSnapshot request) throws IOException {
        return acquire(analysis, request, JavaSignatureSources::checkCancelled);
    }

    /**
     * Acquires a staged source tree, observing request cancellation during preparation and lock waits.
     *
     * @param analysis captured source inputs
     * @param request current source buffer
     * @param activityCheck cancellation/deadline check
     * @return source lease that must be closed by the parser
     * @throws IOException if source staging fails
     */
    public Lease acquire(JavaWorkspaceAnalysis analysis, TextDocumentSnapshot request, Runnable activityCheck)
        throws IOException {
        Objects.requireNonNull(activityCheck, "activityCheck").run();
        Set<Path> excluded = new HashSet<>();
        String name = request.uri().fileName().orElse("Snapshot.java");
        String unit = null;
        for (JavaProjectSemanticIndex.SourceFileIndex file : analysis.sourceIndex().files().values()) {
            activityCheck.run();
            if (file.path().getFileName().toString().equals(name)
                && request.text().equals(analysis.sourceTexts().get(file.path()))) {
                unit = Objects.toString(file.packageName(), "") + "/" + name;
                break;
            }
        }
        if (unit != null) {
            for (JavaProjectSemanticIndex.SourceFileIndex file : analysis.sourceIndex().files().values()) {
                activityCheck.run();
                if (unit.equals(Objects.toString(file.packageName(), "") + "/" + file.path().getFileName())) {
                    excluded.add(file.path());
                }
            }
        }
        Map<Path, String> dependencies = new HashMap<>(analysis.sourceTexts());
        excluded.forEach(dependencies::remove);
        Map<Path, String> key = Map.copyOf(dependencies);
        try {
            while (!lock.tryLock(25, TimeUnit.MILLISECONDS)) {
                activityCheck.run();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Signature request cancelled while waiting for sources");
        }
        try {
            activityCheck.run();
            if (closed)
                throw new CancellationException("Signature source cache is closed");
            Entry entry = entries.get(key);
            if (entry == null) {
                entry = new Entry(JavaSignatureSources.create(analysis, null, excluded, activityCheck));
                liveEntries.add(entry);
                entries.put(key, entry);
                if (entries.size() > capacity) {
                    Entry oldest = entries.pollFirstEntry().getValue();
                    oldest.retired = true;
                    if (oldest.readers == 0) {
                        dispose(oldest);
                    }
                }
            }
            entry.readers++;
            return new Lease(entry);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed)
                return;
            closed = true;
            entries.values().forEach(entry -> {
                entry.retired = true;
                if (entry.readers == 0) {
                    dispose(entry);
                }
            });
            entries.clear();
            if (liveEntries.isEmpty()) {
                removeShutdownHook();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes all staged trees during process shutdown, including evicted trees with active readers.
     * Unlike ordinary closure, shutdown invalidates outstanding leases because their workers may
     * never finish before the JVM exits. Closing those leases afterwards remains safe.
     */
    public void shutdown() {
        lock.lock();
        try {
            closed = true;
            for (Entry entry : new ArrayList<>(liveEntries)) {
                entry.retired = true;
                dispose(entry);
            }
            entries.clear();
            removeShutdownHook();
        } finally {
            lock.unlock();
        }
    }

    private void dispose(Entry entry) {
        if (liveEntries.remove(entry)) {
            entry.sources.close();
        }
        if (closed && liveEntries.isEmpty()) {
            removeShutdownHook();
        }
    }

    private void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException _) {
        }
    }

    /** Keeps a shared source tree alive until JDT has finished reading it. */
    public final class Lease implements AutoCloseable {
        private Entry entry;
        private Lease(Entry entry) {
            this.entry = entry;
        }

        /**
         * Returns the immutable source root.
         *
         * @return staged dependency directory
         */
        public Path directory() {
            return entry.sources.directory();
        }

        @Override
        public void close() {
            lock.lock();
            try {
                if (entry == null)
                    return;
                if (--entry.readers == 0 && entry.retired) {
                    dispose(entry);
                }
                entry = null;
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class Entry {
        private final JavaSignatureSources sources;
        private int readers;
        private boolean retired;
        private Entry(JavaSignatureSources sources) {
            this.sources = sources;
        }
    }
}
