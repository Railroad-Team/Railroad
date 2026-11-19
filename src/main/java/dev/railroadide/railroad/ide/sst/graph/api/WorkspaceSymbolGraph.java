package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolId;
import dev.railroadide.railroad.ide.sst.request.internal.ActivityCheckpoint;
import java.util.*;
import java.util.function.Function;

/**
 * Immutable captured graph with disk shards and optional open-document overlays. A replacement updates
 * only that document's postings; unrelated shard and posting values are shared. Retaining a graph pins
 * its answers, so a query must use the same instance throughout. Owners serialize read-modify-publish
 * transactions (the project index service already does this) or use an atomic-reference update.
 * Duplicate declarations from different origins remain available under the same identity.
 */
public final class WorkspaceSymbolGraph {
    private final Map<DocumentId, SemanticIndexShard> disk;
    private final Map<DocumentId, SemanticIndexShard> overlays;
    private final Map<SymbolId, List<SymbolDescriptor>> symbols;
    private final Map<SymbolId, List<ReferenceOccurrence>> reverse;
    private final Map<SymbolId, List<SymbolEdge>> outgoing;
    private final Map<SymbolId, List<SymbolEdge>> incoming;

    private WorkspaceSymbolGraph(
        Map<DocumentId, SemanticIndexShard> disk,
        Map<DocumentId, SemanticIndexShard> overlays,
        Map<SymbolId, List<SymbolDescriptor>> symbols,
        Map<SymbolId, List<ReferenceOccurrence>> reverse,
        Map<SymbolId, List<SymbolEdge>> outgoing,
        Map<SymbolId, List<SymbolEdge>> incoming
    ) {
        this.disk = Map.copyOf(disk);
        this.overlays = Map.copyOf(overlays);
        this.symbols = Map.copyOf(symbols);
        this.reverse = Map.copyOf(reverse);
        this.outgoing = Map.copyOf(outgoing);
        this.incoming = Map.copyOf(incoming);
    }

    /**
     * Creates an immutable graph with no document facts.
     *
     * @return immutable graph with no document facts
     */
    public static WorkspaceSymbolGraph empty() {
        return new WorkspaceSymbolGraph(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    /**
     * Indexes captured disk shards without installing overlays.
     *
     * @param shards captured document shards with unique document identities
     * @return immutable graph indexing the supplied disk shards
     */
    public static WorkspaceSymbolGraph of(Collection<SemanticIndexShard> shards) {
        return of(shards, ActivityCheckpoint::checkInterrupted);
    }

    /**
     * Indexes captured disk shards without installing overlays.
     *
     * @param shards captured document shards with unique document identities
     * @param activityCheck callback invoked to check cancellation or interruption; may throw to abort
     * @return immutable graph indexing the supplied disk shards
     */
    public static WorkspaceSymbolGraph of(Collection<SemanticIndexShard> shards, Runnable activityCheck) {
        Objects.requireNonNull(activityCheck).run();
        Map<DocumentId, SemanticIndexShard> documents = new LinkedHashMap<>();
        for (var shard : shards) {
            activityCheck.run();
            if (documents.put(shard.documentId(), shard) != null)
                throw new IllegalArgumentException("Duplicate shard document: " + shard.documentId());
        }
        return new WorkspaceSymbolGraph(documents, Map.of(),
            collect(shards, SemanticIndexShard::declarations, SymbolDescriptor::id, activityCheck),
            collect(shards, SemanticIndexShard::references, ReferenceOccurrence::target, activityCheck),
            collect(shards, SemanticIndexShard::edges, SymbolEdge::source, activityCheck),
            collect(shards, SemanticIndexShard::edges, SymbolEdge::target, activityCheck));
    }

    private static <T> Map<SymbolId, List<T>> collect(
        Collection<SemanticIndexShard> shards,
        Function<SemanticIndexShard, List<T>> values,
        Function<T, SymbolId> key,
        Runnable activityCheck
    ) {
        Map<SymbolId, List<T>> result = new HashMap<>();
        for (var shard : shards) {
            activityCheck.run();
            for (T value : values.apply(shard)) {
                activityCheck.run();
                result.computeIfAbsent(key.apply(value), _ -> new ArrayList<>()).add(value);
            }
        }
        result.replaceAll((_, entries) -> {
            activityCheck.run();
            return List.copyOf(entries);
        });
        return result;
    }

    /**
     * Returns the visible document facts after applying overlays.
     *
     * @return immutable visible shards, with overlays taking precedence over disk facts
     */
    public Map<DocumentId, SemanticIndexShard> shards() {
        var result = new HashMap<>(disk);
        result.putAll(overlays);
        return Map.copyOf(result);
    }

    /**
     * Returns every visible declaration view sharing a canonical identity.
     *
     * @param id declaration identity to query
     * @return all visible declaration views for the identity, or an empty list
     */
    public List<SymbolDescriptor> symbols(SymbolId id) {
        return symbols.getOrDefault(Objects.requireNonNull(id), List.of());
    }

    /**
     * Looks up resolved occurrences of a declaration across visible shards.
     *
     * @param id declaration identity to query
     * @return resolved occurrences targeting the identity, or an empty list
     */
    public List<ReferenceOccurrence> referencesTo(SymbolId id) {
        return reverse.getOrDefault(Objects.requireNonNull(id), List.of());
    }

    /**
     * Looks up relations whose source is the supplied identity.
     *
     * @param id declaration identity to query
     * @return relations starting at the identity, or an empty list
     */
    public List<SymbolEdge> outgoingEdges(SymbolId id) {
        return outgoing.getOrDefault(Objects.requireNonNull(id), List.of());
    }

    /**
     * Looks up relations whose target is the supplied identity.
     *
     * @param id declaration identity to query
     * @return relations ending at the identity, or an empty list
     */
    public List<SymbolEdge> incomingEdges(SymbolId id) {
        return incoming.getOrDefault(Objects.requireNonNull(id), List.of());
    }

    /**
     * Replaces disk facts atomically while retaining a visible overlay, if any.
     *
     * @param shard document facts to publish
     * @return new graph retaining any visible overlay for the replaced document
     */
    public WorkspaceSymbolGraph replaceShard(SemanticIndexShard shard) {
        Objects.requireNonNull(shard);
        rejectOlder(disk.get(shard.documentId()), shard);
        var updated = new HashMap<>(disk);
        updated.put(shard.documentId(), shard);
        return change(updated, overlays, shard.documentId());
    }

    /**
     * Deletes both layers and all postings owned by this document, including reverse edges.
     *
     * @param id document identity to update
     * @return new graph with both layers and postings for the document removed
     */
    public WorkspaceSymbolGraph removeShard(DocumentId id) {
        var updated = new HashMap<>(disk);
        var updatedOverlays = new HashMap<>(overlays);
        updated.remove(Objects.requireNonNull(id));
        updatedOverlays.remove(id);
        return change(updated, updatedOverlays, id);
    }

    /**
     * Replaces the visible document facts without destroying its persisted baseline.
     *
     * @param shard document facts to publish
     * @return new graph exposing the overlay while retaining disk facts
     */
    public WorkspaceSymbolGraph withOverlay(SemanticIndexShard shard) {
        Objects.requireNonNull(shard);
        rejectOlder(disk.get(shard.documentId()), shard);
        rejectOlder(overlays.get(shard.documentId()), shard);
        var updated = new HashMap<>(overlays);
        updated.put(shard.documentId(), shard);
        return change(disk, updated, shard.documentId());
    }

    /**
     * Closes an overlay, revealing the latest disk shard. Saving must first replace that disk shard.
     *
     * @param id document identity to update
     * @return new graph exposing disk facts for the document, if present
     */
    public WorkspaceSymbolGraph withoutOverlay(DocumentId id) {
        var updated = new HashMap<>(overlays);
        updated.remove(Objects.requireNonNull(id));
        return change(disk, updated, id);
    }

    private static void rejectOlder(SemanticIndexShard old, SemanticIndexShard replacement) {
        if (old != null && replacement.documentVersion().isBefore(old.documentVersion()))
            throw new IllegalArgumentException("Cannot publish an older document revision");
    }

    private WorkspaceSymbolGraph change(
        Map<DocumentId, SemanticIndexShard> newDisk,
        Map<DocumentId, SemanticIndexShard> newOverlays,
        DocumentId id
    ) {
        var old = overlays.getOrDefault(id, disk.get(id));
        var replacement = newOverlays.getOrDefault(id, newDisk.get(id));
        if (old == replacement)
            return new WorkspaceSymbolGraph(newDisk, newOverlays, symbols, reverse, outgoing, incoming);
        return new WorkspaceSymbolGraph(newDisk, newOverlays,
            update(symbols, old == null ? List.of() : old.declarations(),
                replacement == null ? List.of() : replacement.declarations(), SymbolDescriptor::id),
            update(reverse, old == null ? List.of() : old.references(),
                replacement == null ? List.of() : replacement.references(), ReferenceOccurrence::target),
            update(outgoing, old == null ? List.of() : old.edges(),
                replacement == null ? List.of() : replacement.edges(), SymbolEdge::source),
            update(incoming, old == null ? List.of() : old.edges(),
                replacement == null ? List.of() : replacement.edges(), SymbolEdge::target));
    }

    private static <T> Map<SymbolId, List<T>> update(
        Map<SymbolId, List<T>> index,
        List<T> removed,
        List<T> added,
        Function<T, SymbolId> key
    ) {
        var result = new HashMap<>(index);
        Map<SymbolId, List<T>> changed = new HashMap<>();
        for (T value : removed) {
            SymbolId id = key.apply(value);
            changed.computeIfAbsent(id, _ -> new ArrayList<>(index.getOrDefault(id, List.of()))).remove(value);
        }
        for (T value : added) {
            SymbolId id = key.apply(value);
            changed.computeIfAbsent(id, _ -> new ArrayList<>(index.getOrDefault(id, List.of()))).add(value);
        }
        changed.forEach((id, values) -> {
            if (values.isEmpty()) {
                result.remove(id);
            } else {
                result.put(id, List.copyOf(values));
            }
        });
        return result;
    }
}
