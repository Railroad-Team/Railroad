package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.index.*;
import dev.railroadide.railroad.ide.sst.provider.api.CapabilityKey;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ide.ui.codeeditor.SyntaxHighlightingProvider;

/** Host integration contracts; portable language features belong to SstCapabilities. */
public final class LanguageCapabilities {
    /**
     * Host project supplied as a session context service.
     */
    public static final CapabilityKey<Project> PROJECT = new CapabilityKey<>("railroad:project", Project.class);
    /**
     * Session service that builds and updates a project language index.
     */
    public static final CapabilityKey<ProjectLanguageIndexer> INDEXER = new CapabilityKey<>("railroad:project-indexer",
        ProjectLanguageIndexer.class);
    /**
     * Session service that loads and saves a language index.
     */
    public static final CapabilityKey<ProjectLanguageIndexPersistence> PERSISTENCE = new CapabilityKey<>(
        "railroad:index-persistence", ProjectLanguageIndexPersistence.class);
    /**
     * Session service that resolves language-specific indexing inputs.
     */
    public static final CapabilityKey<LanguageIndexContextContributor> INDEX_CONTEXT = new CapabilityKey<>(
        "railroad:index-context", LanguageIndexContextContributor.class);
    /**
     * Session service that prepares auxiliary indexes alongside source indexing.
     */
    public static final CapabilityKey<IndexWarmup> INDEX_WARMUP = new CapabilityKey<>("railroad:index-warmup",
        IndexWarmup.class);

    /**
     * Session service that supplies syntax styles for the editor.
     */
    public static final CapabilityKey<SyntaxHighlightingProvider> HIGHLIGHTING = new CapabilityKey<>(
        "railroad:highlighting", SyntaxHighlightingProvider.class);

    private LanguageCapabilities() {
    }

    /**
     * Prepares auxiliary indexes using the captured project indexing context.
     */
    @FunctionalInterface
    public interface IndexWarmup {
        /**
         * Warms auxiliary indexes required by the language session.
         *
         * @param context project indexing inputs and cancellation controls
         */
        void warm(ProjectIndexContext context);
    }
}
