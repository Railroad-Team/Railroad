package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.language.impl.ImageLanguageSupport;
import dev.railroadide.railroad.ide.language.impl.PlainTextLanguageSupport;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.utility.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class LanguageSupportRegistry {
    public static final Registry<LanguageSupport> REGISTRY =
        RegistryManager.createOrderedRegistry("railroad:language_support", LanguageSupport.class);

    private LanguageSupportRegistry() {
    }

    public static void register(LanguageSupport support) {
        if (support == null)
            throw new IllegalArgumentException("Language support cannot be null.");

        REGISTRY.register(support.languageId(), support);
    }

    public static void registerAll(Collection<? extends LanguageSupport> supports) {
        if (supports == null)
            throw new IllegalArgumentException("Language supports cannot be null.");

        for (LanguageSupport support : supports) {
            register(support);
        }
    }

    public static Optional<LanguageSupport> get(String languageId) {
        if (languageId == null)
            throw new IllegalArgumentException("Language id cannot be null.");

        return Optional.ofNullable(REGISTRY.get(languageId));
    }

    public static boolean contains(String languageId) {
        if (languageId == null)
            throw new IllegalArgumentException("Language id cannot be null.");

        return REGISTRY.contains(languageId);
    }

    public static Optional<LanguageSupport> find(Path path) {
        return REGISTRY.values().stream()
            .filter(support -> support.supports(path))
            .findFirst();
    }

    public static String resolveLanguageId(Path path) {
        if (path == null)
            throw new IllegalArgumentException("Path cannot be null.");

        return find(path)
            .map(LanguageSupport::languageId)
            .orElseGet(() -> {
                if (ImageLanguageSupport.INSTANCE.supports(path))
                    return ImageLanguageSupport.INSTANCE.languageId();

                if (Files.isRegularFile(path) && FileUtils.isBinaryFile(path))
                    return "binary";

                return PlainTextLanguageSupport.INSTANCE.languageId();
            });
    }

    public static List<LanguageSupport> all() {
        return REGISTRY.values();
    }

    static void clear() {
        for (String languageId : REGISTRY.keys()) {
            REGISTRY.unregister(languageId);
        }
    }
}
