package dev.railroadide.railroad.ide.language.index;

/**
 * Thrown when a language capability is used without proper support.
 */
public class LanguageNotSupportedException extends IllegalStateException {

    /**
     * Creates an exception identifying the unsupported language.
     *
     * @param languageId the language identifier lacking support
     */
    public LanguageNotSupportedException(String languageId) {
        super("Language not supported: " + languageId);
    }
}
