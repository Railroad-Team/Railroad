package dev.railroadide.core.localization;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;

import java.util.Locale;
import java.util.Optional;

/**
 * Represents a language with its name, ISO 639-1 code, and ISO 3166-1 country code.
 * This interface is used to define the structure for language implementations.
 */
public interface Language {
    /**
     * The registry for managing Language instances.
     * This registry allows for the registration and retrieval of languages by their codes.
     */
    Registry<Language> REGISTRY = RegistryManager.createRegistry("languages", Language.class);

    /**
     * Creates a new LanguageBuilder for constructing a Language instance.
     *
     * @param name the name of the language
     * @return a new LanguageBuilder instance
     */
    static LanguageBuilder builder(String name) {
        return new LanguageBuilder(name);
    }

    /**
     * Returns the name of the language in its native form.
     *
     * @return the name of the language
     */
    String name();

    /**
     * Returns the ISO 639-1 language code.
     *
     * @return the ISO 639-1 language code
     */
    String languageCode();

    /**
     * Returns the ISO 3166-1 country code.
     *
     * @return the ISO 3166-1 country code
     */
    String countryCode();

    /**
     * Returns the full language code in the format "languageCode_countryCode".
     * This is typically used for localization purposes.
     *
     * @return the full language code
     */
    default String getFullCode() {
        return (languageCode() + "_" + countryCode()).toUpperCase(Locale.ROOT);
    }

    /**
     * Retrieves a Language instance from the registry using its ISO 639-1 code.
     * The code is converted to uppercase to ensure case-insensitivity.
     *
     * @param code the ISO 639-1 language code
     * @return an Optional containing the Language if found, or empty if not found
     */
    static Optional<Language> fromCode(String code) {
        return Optional.ofNullable(REGISTRY.get(code.toUpperCase(Locale.ROOT)));
    }
}
