package dev.railroadide.railroad.ide.sst.graph.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.semantic.api.SymbolDescriptor;
import java.util.*;

/**
 * Immutable facts for exactly one input revision. No syntax nodes or live services are retained.
 * Missing targets are legal in a partial index. Locations in references must belong to this revision;
 * declaration alternate locations may refer to other source or binary documents.
 *
 * @param documentId identity of the document owning these facts
 * @param documentVersion captured revision of the owning document
 * @param uri exact document address, including any archive entry
 * @param declarations declaration facts to copy into the shard
 * @param references resolved occurrences belonging to this document revision
 * @param edges directed relations established by this document
 */
public record SemanticIndexShard(
    DocumentId documentId,
    DocumentVersion documentVersion,
    DocumentUri uri,
    List<SymbolDescriptor> declarations,
    List<ReferenceOccurrence> references,
    List<SymbolEdge> edges
) {
    /**
     * Copies document facts and validates declaration identities and location revisions.
     *
     * @param documentId identity of the document owning these facts
     * @param documentVersion captured revision of the owning document
     * @param uri exact document address, including any archive entry
     * @param declarations declaration facts to copy into the shard
     * @param references resolved occurrences belonging to this document revision
     * @param edges directed relations established by this document
     */
    public SemanticIndexShard {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(documentVersion, "documentVersion");
        Objects.requireNonNull(uri, "uri");
        declarations = List.copyOf(declarations);
        references = List.copyOf(references);
        edges = List.copyOf(edges);
        var ids = new HashSet<>();
        for (var declaration : declarations) {
            if (!ids.add(declaration.id()))
                throw new IllegalArgumentException("Duplicate declaration identity: " + declaration.id());
            for (var location : declaration.locations()) {
                if (location.declaration() != null
                    && (location.uri().equals(uri) || location.declaration().documentId().equals(documentId))
                    && (!location.uri().equals(uri) || !location.declaration().documentId().equals(documentId)
                        || !location.declaration().documentVersion().equals(documentVersion)))
                    throw new IllegalArgumentException("Declaration location belongs to another document revision");
            }
        }
        for (var reference : references) {
            if (!documentId.equals(reference.location().documentId())
                || !documentVersion.equals(reference.location().documentVersion()))
                throw new IllegalArgumentException("Reference location belongs to another document revision");
        }
    }

    /**
     * Rejects queries mixing facts with a different capture instead of silently returning stale ranges.
     *
     * @param workspace captured workspace against which document identity, revision and ranges are checked
     */
    public void validateAgainst(WorkspaceSnapshot workspace) {
        var document = workspace.document(documentId)
            .orElseThrow(() -> new IllegalArgumentException("Shard document is absent from workspace"));
        if (!document.version().equals(documentVersion) || !document.uri().equals(uri))
            throw new IllegalArgumentException("Shard document revision or address differs from workspace");
        references.forEach(reference -> reference.location().validateAgainst(document));
        for (var symbol : declarations) {
            for (var location : symbol.locations()) {
                if (location.uri().equals(uri) && location.declaration() != null) {
                    location.declaration().validateAgainst(document);
                    if (location.selection() != null) {
                        location.selection().validateAgainst(document);
                    }
                }
            }
        }
    }
}
