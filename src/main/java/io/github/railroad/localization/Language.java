package io.github.railroad.localization;

import static io.github.railroad.Railroad.LOGGER;

public enum Language {
    /*
     * Language codes use the ISO 639-1 2 letter (set-1) standard.
     * Country codes use the ISO 3166-1 2 letter (A-2) standard.
     * Language names are in the language itself.
     */
    EN_US("English"),
    ES_ES("Español"),
    FR_FR("Français"),
    DE_DE("Deutsch");

    private final String name;

    Language(String name) {
        this.name = name;
    }

    public static Language fromName(String name) {
        for (Language lang : Language.values()) {
            if (lang.getName().equalsIgnoreCase(name)) {
                return lang;
            }
        }

        LOGGER.error("Cannot find language: {}", name);
        return null;
    }

    public String getName() {
        return name;
    }
}