package dev.railroadide.railroad.ide.language;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LanguageSupportRegistry {
    private static final Map<String, LanguageSupport> REGISTERED = new HashMap<>();

    private LanguageSupportRegistry() {
    }

    public static void register(LanguageSupport support) {
        if (REGISTERED.containsKey(support.languageId()))
            throw new IllegalArgumentException("Language support for '" + support.languageId() + "' is already registered.");

        REGISTERED.put(support.languageId(), support);
    }

    public static Optional<LanguageSupport> find(Path path) {
        return REGISTERED.values().stream()
            .filter(support -> support.supports(path))
            .findFirst();
    }

    public static List<LanguageSupport> all() {
        return List.copyOf(REGISTERED.values());
    }
}
