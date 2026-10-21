package dev.railroadide.railroad.localization;

/**
 * A utility class that provides access to predefined languages and initializes the language registry.
 */
public final class Languages {
    /**
     * The English (US) language instance.
     */
    public static final Language EN_US = Language.builder("English (US)")
        .languageCode("en")
        .countryCode("US")
        .build();

    /**
     * Initializes the language registry by loading the predefined languages.
     */
    public static void initialize() {
        LanguageRegistryLoader.load();
    }
}
