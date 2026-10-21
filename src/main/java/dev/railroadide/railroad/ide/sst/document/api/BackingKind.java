package dev.railroadide.railroad.ide.sst.document.api;

/** Storage or addressing mechanism for a document, independent of provenance. */
public enum BackingKind {
    /** Filesystem file, including an unsaved editor revision of that file. */
    FILE,
    /** Content held in memory without a persistent backing file. */
    MEMORY,
    /** Entry addressed inside an archive. */
    ARCHIVE_ENTRY,
    /** Provider-owned virtual location. */
    VIRTUAL
}
