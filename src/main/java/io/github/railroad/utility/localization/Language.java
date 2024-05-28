package io.github.railroad.utility.localization;

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

    public String getName() {
        return name;
    }

    public static Language fromName(String name) {
        for(Language lang : Language.values()){
            LOGGER.info("LANG {} NAME {}", lang.getName(), name);
            if(lang.getName().equalsIgnoreCase(name)){
                return lang;
            }
        }

        LOGGER.error("Cannot find language: {}", name);
        return null;
    }
}