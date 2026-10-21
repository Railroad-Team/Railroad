package dev.railroadide.railroad.debug.breakpoint;

import dev.railroadide.railroad.debug.source.DebugSource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores normalized file breakpoints and moves their positions as tracked documents change.
 */
public final class BreakpointService {
    private final Map<Path, Map<Integer, SourceBreakpoint>> breakpoints = new HashMap<>();
    private final List<BreakpointListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<Path, TrackedDocument> documents = new HashMap<>();

    /**
     * Tracks an open document so edits move its breakpoints once, even across multiple views.
     * Closing the last tracking handle releases the text snapshot but retains breakpoints.
     *
     * @param file backing file
     * @param text initial editor contents
     * @return handle to update after text changes and close with the editor
     */
    public synchronized DocumentTracking trackDocument(Path file, String text) {
        Objects.requireNonNull(text, "text");
        Path normalizedFile = normalize(file);
        TrackedDocument document = documents.computeIfAbsent(normalizedFile,
            _ -> new TrackedDocument(text));
        document.views++;
        return new DocumentTracking(normalizedFile, document);
    }

    private void moveBreakpoints(Path file, List<BreakpointLineMapping> mappings) {
        Map<Integer, SourceBreakpoint> existing = breakpoints.get(file);
        if (existing == null || mappings.isEmpty())
            return;

        Map<Integer, SourceBreakpoint> updated = new HashMap<>();
        List<BreakpointEvent> events = new ArrayList<>();
        for (SourceBreakpoint breakpoint : existing.values().stream()
            .sorted(Comparator.comparingInt(SourceBreakpoint::line)).toList()) {
            int line = breakpoint.line();
            for (BreakpointLineMapping mapping : mappings) {
                line = mapping.map(line);
                if (line == 0)
                    break;
            }
            if (line == 0 || updated.containsKey(line)) {
                // Joining marked lines keeps the first breakpoint at the resulting line.
                events.add(new BreakpointEvent.Removed(breakpoint));
                continue;
            }
            SourceBreakpoint moved = line == breakpoint.line()
                ? breakpoint
                : new SourceBreakpoint(breakpoint.id(), breakpoint.source(), line, breakpoint.enabled());
            updated.put(line, moved);
            if (moved != breakpoint) {
                events.add(new BreakpointEvent.Changed(moved));
            }
        }
        if (updated.isEmpty()) {
            breakpoints.remove(file);
        } else {
            breakpoints.put(file, updated);
        }
        // Publish only after the complete remapping, so listeners never see partial moves.
        events.forEach(this::fire);
    }

    /** An open view's handle to the file's shared breakpoint position tracking. */
    public final class DocumentTracking implements AutoCloseable {
        private final Path file;
        private final TrackedDocument document;
        private boolean closed;

        private DocumentTracking(Path file, TrackedDocument document) {
            this.file = file;
            this.document = document;
        }

        /**
         * Applies the current editor contents and emits changes for relocated breakpoints.
         * Repeated snapshots, such as another view reloading the same saved edit, are ignored.
         *
         * @param text current editor contents after a text transaction
         */
        public void update(String text) {
            update(null, text, List.of());
        }

        /**
         * Applies a text transaction using its exact edit locations. Exact locations distinguish
         * insertions among identical lines; stale views and whole-buffer reloads use a content diff.
         *
         * @param before contents in this view before the transaction, or null for a snapshot-only update
         * @param text current editor contents
         * @param changes ordered replacements, each relative to the preceding replacement
         */
        public void update(@Nullable String before, String text, List<TextChange> changes) {
            Objects.requireNonNull(text, "text");
            synchronized (BreakpointService.this) {
                if (closed || document.text.equals(text))
                    return;
                List<BreakpointLineMapping> mappings = new ArrayList<>();
                if (breakpoints.containsKey(file)) {
                    if (document.text.equals(before)) {
                        String snapshot = before;
                        for (TextChange change : changes) {
                            int end = change.position() + change.removed().length();
                            if (change.position() < 0 || end > snapshot.length()
                                || !snapshot.substring(change.position(), end).equals(change.removed())) {
                                mappings.clear();
                                break;
                            }
                            String next = snapshot.substring(0, change.position()) + change.inserted()
                                + snapshot.substring(end);
                            mappings.add(new BreakpointLineMapping(snapshot, next, change.position(),
                                change.removed().length(), change.inserted().length()));
                            snapshot = next;
                        }
                        if (!snapshot.equals(text)) {
                            mappings.clear();
                        }
                    }
                    if (mappings.isEmpty()) {
                        mappings.add(new BreakpointLineMapping(document.text, text));
                    }
                }
                document.text = text;
                moveBreakpoints(file, mappings);
            }
        }

        /** Releases this view's tracking state. */
        @Override
        public void close() {
            synchronized (BreakpointService.this) {
                if (closed)
                    return;
                closed = true;
                if (--document.views == 0) {
                    documents.remove(file);
                }
            }
        }
    }

    /**
     * A plain text replacement, independent of the editor toolkit.
     *
     * @param position zero-based UTF-16 offset
     * @param removed replaced text
     * @param inserted replacement text
     */
    public record TextChange(int position, String removed, String inserted) {
        /**
         * Validates a replacement offset and requires non-null removed and inserted text.
         *
         * @param position zero-based UTF-16 replacement offset
         * @param removed text replaced by the edit
         * @param inserted replacement text
         * @throws IllegalArgumentException if the offset is negative
         * @throws NullPointerException if removed or inserted text is null
         */
        public TextChange {
            if (position < 0)
                throw new IllegalArgumentException("Text change position must be non-negative");
            Objects.requireNonNull(removed, "removed");
            Objects.requireNonNull(inserted, "inserted");
        }
    }

    private static final class TrackedDocument {
        private String text;
        private int views;

        private TrackedDocument(String text) {
            this.text = text;
        }
    }

    /**
     * Finds the breakpoint at a normalized source-file location.
     *
     * @param file backing source file, normalized to an absolute path
     * @param line one-based source line
     * @return the stored breakpoint, or empty when the line has no breakpoint
     */
    public synchronized Optional<SourceBreakpoint> get(Path file, int line) {
        file = normalize(file);

        return Optional.ofNullable(
            breakpoints
                .getOrDefault(file, Map.of())
                .get(line));
    }

    /**
     * Returns a snapshot of all stored breakpoints.
     *
     * @return an unmodifiable snapshot across all files
     */
    public synchronized Collection<SourceBreakpoint> getAll() {
        return breakpoints.values()
            .stream()
            .flatMap(map -> map.values().stream())
            .toList();
    }

    /**
     * Returns a snapshot of the breakpoints belonging to one normalized file.
     *
     * @param file backing source file, normalized to an absolute path
     * @return an unmodifiable snapshot, empty when no breakpoints belong to the file
     */
    public synchronized Collection<SourceBreakpoint> getForFile(Path file) {
        return List.copyOf(breakpoints
            .getOrDefault(normalize(file), Map.of())
            .values());
    }

    /**
     * Adds an enabled breakpoint and publishes its addition, or returns the breakpoint already at that line.
     *
     * @param file backing source file, normalized to an absolute path
     * @param line one-based source line
     * @return the new or existing breakpoint definition
     * @throws IllegalArgumentException if adding a breakpoint at a non-positive line
     */
    public synchronized SourceBreakpoint add(Path file, int line) {
        if (line < 1)
            throw new IllegalArgumentException("Breakpoint lines must be positive");
        file = normalize(file);
        SourceBreakpoint existing = breakpoints.getOrDefault(file, Map.of()).get(line);
        if (existing != null)
            return existing;

        var breakpoint = new SourceBreakpoint(
            UUID.randomUUID(),
            new DebugSource.FileSource(file),
            line,
            true);

        breakpoints.computeIfAbsent(file, _ -> new HashMap<>())
            .put(line, breakpoint);

        fire(new BreakpointEvent.Added(breakpoint));

        return breakpoint;
    }

    /**
     * Removes the breakpoint at a source location and publishes its removal if present.
     *
     * @param file backing source file, normalized to an absolute path
     * @param line one-based source line
     */
    public synchronized void remove(Path file, int line) {
        file = normalize(file);

        Map<Integer, SourceBreakpoint> fileBreakpoints = breakpoints.get(file);
        if (fileBreakpoints == null)
            return;

        SourceBreakpoint removed = fileBreakpoints.remove(line);

        if (fileBreakpoints.isEmpty()) {
            breakpoints.remove(file);
        }

        if (removed != null) {
            fire(new BreakpointEvent.Removed(removed));
        }
    }

    /**
     * Removes an existing breakpoint or adds an enabled breakpoint at the requested location.
     *
     * @param file backing source file, normalized to an absolute path
     * @param line one-based source line
     * @throws IllegalArgumentException if adding a breakpoint at a non-positive line
     */
    public synchronized void toggle(Path file, int line) {
        if (get(file, line).isPresent()) {
            remove(file, line);
        } else {
            add(file, line);
        }
    }

    /**
     * Checks whether a source location has a stored breakpoint.
     *
     * @param file backing source file, normalized to an absolute path
     * @param line one-based source line
     * @return true when the normalized file and line have a breakpoint
     */
    public synchronized boolean has(Path file, int line) {
        return get(file, line).isPresent();
    }

    /**
     * Registers a listener for synchronous notifications after breakpoint mutations.
     *
     * @param listener recipient of synchronous breakpoint mutation events
     */
    public synchronized void addListener(BreakpointListener listener) {
        listeners.add(listener);
    }

    /**
     * Unregisters a listener from subsequent breakpoint notifications.
     *
     * @param listener recipient of synchronous breakpoint mutation events
     */
    public synchronized void removeListener(BreakpointListener listener) {
        listeners.remove(listener);
    }

    private void fire(BreakpointEvent event) {
        listeners.forEach(listener -> listener.onBreakpointEvent(event));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
