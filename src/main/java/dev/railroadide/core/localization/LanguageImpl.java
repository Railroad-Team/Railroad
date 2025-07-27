package dev.railroadide.core.localization;

import org.jetbrains.annotations.NotNull;

record LanguageImpl(String name, String languageCode, String countryCode) implements Language {
    @Override
    public @NotNull String toString() {
        return name + " (" + languageCode + (countryCode != null ? "-" + countryCode : "") + ")";
    }
}