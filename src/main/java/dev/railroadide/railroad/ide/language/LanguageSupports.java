package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JsonLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.MarkdownLanguageSupport;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;

import java.util.List;

/**
 * Initializes built-in language support and registers optional indexing services.
 */
public final class LanguageSupports {
    private static final List<LanguageSupport> BUILT_INS = List.of(
        new JavaLanguageSupport(),
        new JsonLanguageSupport(),
        new MarkdownLanguageSupport());

    private static boolean initialized = false;

    private LanguageSupports() {
    }

    /**
     * Registers the built-in Java, JSON, and Markdown support once until reset.
     */
    public static void initialize() {
        if (initialized)
            return;

        LanguageSupportRegistry.registerAll(BUILT_INS);
        initialized = true;
    }

    /**
     * Registers indexers supplied by language supports when their language has no existing indexer.
     */
    public static void initializeLanguageIndexers() {
        for (LanguageSupport support : LanguageSupportRegistry.all()) {
            var indexer = support.createIndexer();
            if (indexer == null || Services.PROJECT_LANGUAGE_INDEX_SERVICE.hasIndexer(indexer.languageId()))
                continue;

            Services.PROJECT_LANGUAGE_INDEX_SERVICE.registerIndexer(indexer);
        }
    }

    /**
     * Registers persistence adapters supplied by language supports when none is already registered.
     */
    public static void initializeLanguagePersistence() {
        for (LanguageSupport support : LanguageSupportRegistry.all()) {
            ProjectLanguageIndexPersistence<?> persistence = support.createPersistence();
            if (persistence == null)
                continue;

            if (!Services.PROJECT_LANGUAGE_INDEX_SERVICE.hasPersistence(persistence.languageId())) {
                Services.PROJECT_LANGUAGE_INDEX_SERVICE.registerPersistence(persistence);
            }
        }
    }

    /**
     * Clears language support registrations and permits built-in initialization to run again.
     */
    public static void reset() {
        initialized = false;
        LanguageSupportRegistry.clear();
    }
}
