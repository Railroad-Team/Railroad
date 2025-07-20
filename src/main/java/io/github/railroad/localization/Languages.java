package io.github.railroad.localization;

import io.github.railroad.core.localization.Language;

public final class Languages {
    public static final Language EN_US = Language.builder("English")
            .languageCode("en")
            .countryCode("US")
            .build();

    public static final Language ES_ES = Language.builder("Español")
            .languageCode("es")
            .countryCode("ES")
            .build();

    public static final Language FR_FR = Language.builder("Français")
            .languageCode("fr")
            .countryCode("FR")
            .build();

    public static final Language DE_DE = Language.builder("Deutsch")
            .languageCode("de")
            .countryCode("DE")
            .build();
}