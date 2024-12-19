package io.github.railroad.localization;

import static io.github.railroad.Railroad.LOGGER;

public enum Language {
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