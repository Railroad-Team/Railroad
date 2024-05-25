package io.github.railroad.utility.localization;

public enum Languages {
    ENGLISH("en_us", "English"),
    SPANISH("es_es", "Español"),
    FRENCH("fr_fr", "Français"),
    GERMAN("de_de", "Deutsch");

    private final String locale;
    private final String language;

    Languages(String locale, String language) {
        this.locale = locale;
        this.language = language;
    }

    public String getLocale() {
        return locale;
    }

    public String getLanguage() {
        return language;
    }

    public static Languages fromLocale(String locale) {
        for (Languages lang : values()) {
            if (lang.getLocale().equals(locale)) {
                return lang;
            }
        }

        return null;
    }

    public static Languages fromLanguage(String language) {
        for (Languages lang : values()) {
            if (lang.getLanguage().equals(language)) {
                return lang;
            }
        }

        return null;
    }
}