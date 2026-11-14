package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.DocumentOrigin;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;
import dev.railroadide.railroad.ide.sst.document.api.Location;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One source, binary, generated, attached-source or decompiled view of a declaration.
 * The URI retains archive entry identity. Ranges are optional for binaries with no offset mapping;
 * a URI alone may still be navigable. Locations carry document revisions and must be validated
 * against the destination snapshot before selecting text or bytes.
 */
public record SymbolLocation(
    DocumentUri uri,
    DocumentOrigin origin,
    @Nullable Location<?> declaration,
    @Nullable Location<?> selection,
    boolean navigable
) {
    public SymbolLocation {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(origin, "origin");
        if (selection != null) {
            if (declaration == null || !selection.documentId().equals(declaration.documentId())
                || !selection.documentVersion().equals(declaration.documentVersion())
                || !selection.range().getClass().equals(declaration.range().getClass())
                || selection.range().start < declaration.range().start
                || selection.range().end > declaration.range().end)
                throw new IllegalArgumentException("Selection must lie within the same declaration revision and range");
        }
    }
}
