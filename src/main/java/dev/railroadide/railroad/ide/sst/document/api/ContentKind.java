package dev.railroadide.railroad.ide.sst.document.api;

/** Representation of the immutable content carried by a document snapshot. */
public enum ContentKind {
    /** Character content exposed by {@link TextDocumentSnapshot}. */
    TEXT,
    /** Byte content exposed by {@link BinaryDocumentSnapshot}. */
    BINARY
}
