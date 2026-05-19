package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JsonLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.MarkdownLanguageSupport;
import dev.railroadide.railroad.ide.language.index.ProjectLanguageIndexPersistence;

import java.util.List;

public final class LanguageSupports {
    private static final List<LanguageSupport> BUILT_INS = List.of(
        new JavaLanguageSupport(),
        new JsonLanguageSupport(),
        new MarkdownLanguageSupport()
    );

    private static boolean initialized = false;

    private LanguageSupports() {
    }

    public static void initialize() {
        if (initialized)
            return;

        LanguageSupportRegistry.registerAll(BUILT_INS);
        initialized = true;
    }

    public static void initializeLanguageIndexers() {
        for (LanguageSupport support : LanguageSupportRegistry.all()) {
            var indexer = support.createIndexer();
            if (indexer == null || Services.PROJECT_LANGUAGE_INDEX_SERVICE.hasIndexer(indexer.languageId()))
                continue;

            Services.PROJECT_LANGUAGE_INDEX_SERVICE.registerIndexer(indexer);
        }
    }

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

    static void reset() {
        initialized = false;
        LanguageSupportRegistry.clear();
    }
}
