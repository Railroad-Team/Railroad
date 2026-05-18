package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.impl.JavaLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.JsonLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.MarkdownLanguageSupport;

public final class LanguageSupports {
    private static boolean initialized = false;

    private LanguageSupports() {
    }

    public static void initialize() {
        if (initialized)
            return;
        initialized = true;

        LanguageSupportRegistry.register(new JavaLanguageSupport());
        LanguageSupportRegistry.register(new JsonLanguageSupport());
        LanguageSupportRegistry.register(new MarkdownLanguageSupport());
    }
}
