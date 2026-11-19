package dev.railroadide.railroad.ide.sst.semantic.api;

/**
 * A language-defined semantic type. Implementations are immutable and safe for concurrent reads.
 * Languages supply their own kinds and representation; consumers must tolerate unfamiliar kinds.
 */
public interface Type {
    /**
     * Classifies the semantic type using the language's category vocabulary.
     *
     * @return the language-defined type category
     */
    Kind kind();

    /**
     * Formats the semantic type for display to the user.
     *
     * @return a human-readable name suitable for diagnostics
     */
    String displayName();

    /**
     * An open type category. Use a stable namespaced ID and a finite vocabulary per language.
     */
    public interface Kind {
        /**
         * Identifies this type category across provider boundaries.
         *
         * @return the namespaced category identifier
         */
        String id();
    }
}
