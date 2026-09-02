package dev.railroadide.railroad.ide.language.index;

/**
 * Thrown when a language capability is used without proper support.
 */
public class LanguageNotSupportedException extends IllegalStateException {

    public LanguageNotSupportedException(String languageId) {
        super("Language not supported: " + languageId);
    }
}
