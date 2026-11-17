package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;

public record DefinitionTarget(
    TextDocumentSnapshot snapshot,
    Location.TextLocation location
) {
}
