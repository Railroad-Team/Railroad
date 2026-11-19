package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.impl.*;
import java.util.List;

/** Installs built-ins into the same owned runtime used for plugin contributions. */
public final class LanguageSupports {
    private static final List<LanguageSupport> BUILT_INS = List.of(new JavaLanguageSupport(),
        new JsonLanguageSupport(), new MarkdownLanguageSupport(), PlainTextLanguageSupport.INSTANCE,
        ImageLanguageSupport.INSTANCE);

    private LanguageSupports() {
    }

    /**
     * Registers built-in languages whose IDs are not already present in the shared runtime.
     */
    public static synchronized void initialize() {
        for (LanguageSupport support : BUILT_INS) {
            if (LanguageSupportRegistry.runtime().get(support.id()).isEmpty()) {
                LanguageSupportRegistry.runtime().register("railroad", support);
            }
        }
    }

    /**
     * Clears all language registrations and closes their sessions in the shared runtime.
     */
    public static void reset() {
        LanguageSupportRegistry.clear();
    }
}
