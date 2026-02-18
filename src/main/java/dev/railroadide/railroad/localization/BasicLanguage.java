package dev.railroadide.railroad.localization;

import org.jetbrains.annotations.NotNull;

record BasicLanguage(String name, String languageCode, String countryCode) implements Language {
    @Override
    public @NotNull String toString() {
        return name + " (" + languageCode + (countryCode != null ? "-" + countryCode : "") + ")";
    }
}
