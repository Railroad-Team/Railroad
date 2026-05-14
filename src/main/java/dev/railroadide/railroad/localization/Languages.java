package dev.railroadide.railroad.localization;

public final class Languages {
    public static final Language EN_US = Language.builder("English (US)")
        .languageCode("en")
        .countryCode("US")
        .build();

    public static void initialize() {
        LanguageRegistryLoader.load();
    }
}
