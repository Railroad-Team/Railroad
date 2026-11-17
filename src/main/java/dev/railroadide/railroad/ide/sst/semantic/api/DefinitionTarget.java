package dev.railroadide.railroad.ide.sst.semantic.api;

import dev.railroadide.railroad.ide.sst.document.api.Location;
import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;

/**
 * A navigable declaration range validated against the captured destination revision that contains it.
 *
 * @param snapshot captured destination revision
 * @param location range in that revision
 */
public record DefinitionTarget(
    TextDocumentSnapshot snapshot,
    Location.TextLocation location
) {
}
