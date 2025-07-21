package dev.railroadide.core.localization;

/**
 * Builder class for creating instances of {@link Language}.
 * This class allows setting the name, language code, and country code of the language.
 */
public class LanguageBuilder {
    private final String name;
    private String languageCode;
    private String countryCode;

    /**
     * Constructs a LanguageBuilder with the specified name.
     *
     * @param name the name of the language
     */
    public LanguageBuilder(String name) {
        this.name = name;
    }

    /**
     * Sets the language code for the language being built.
     *
     * @param languageCode the ISO 639-1 code of the language
     * @return this builder instance for method chaining
     */
    public LanguageBuilder languageCode(String languageCode) {
        this.languageCode = languageCode;
        return this;
    }

    /**
     * Sets the country code for the language being built.
     *
     * @param countryCode the ISO 3166-1 alpha-2 code of the country
     * @return this builder instance for method chaining
     */
    public LanguageBuilder countryCode(String countryCode) {
        this.countryCode = countryCode;
        return this;
    }

    /**
     * Builds and returns a new instance of {@link Language}.
     *
     * @return a new Language instance with the specified properties
     * @throws IllegalStateException if the language code or country code is not set
     */
    public Language build() {
        if (languageCode == null || countryCode == null)
            throw new IllegalStateException("Language code and country code must be set");

        return new LanguageImpl(name, languageCode, countryCode);
    }
}
