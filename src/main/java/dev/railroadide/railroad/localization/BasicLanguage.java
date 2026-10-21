package dev.railroadide.railroad.localization;

import org.jetbrains.annotations.NotNull;

/**
 * A basic implementation of the {@link Language} interface.
 *
 * @param name The name of the language.
 * @param languageCode The ISO 639-1 language code.
 * @param countryCode The ISO 3166-1 alpha-2 country code (optional).
 */
public record BasicLanguage(String name, String languageCode, String countryCode) implements Language {
    @Override
    public @NotNull String toString() {
        return name + " (" + languageCode + (countryCode != null ? "-" + countryCode : "") + ")";
    }
}
