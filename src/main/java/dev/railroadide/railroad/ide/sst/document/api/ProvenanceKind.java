package dev.railroadide.railroad.ide.sst.document.api;

/** Manner in which document content was obtained, independent of backing. */
public enum ProvenanceKind {
    /** Provenance was not supplied; no claim is made about how content was obtained. */
    UNSPECIFIED,
    /** Original content supplied by the document owner. */
    ORIGINAL,
    /** Content produced by a generator or transformation. */
    GENERATED,
    /** Source-like content reconstructed from compiled binary content. */
    DECOMPILED,
    /** Source content attached to a library or binary artifact. */
    ATTACHED_SOURCE
}
