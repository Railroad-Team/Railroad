package io.github.railroad.utility.localization;

import java.util.Objects;

import static io.github.railroad.Railroad.LOGGER;

public enum Languages {
    en_us("English"),
    es_es("Español"),
    fr_fr("Français"),
    de_de("Deutsch");

    private final String name;

    Languages(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Languages fromName(String name) {
        for(Languages l : Languages.values()){
            if(l.name().equalsIgnoreCase(l.getName())){
                return l;
            }
        }
        LOGGER.error("Cannot find language: {}", name);
        return null;
    }
}