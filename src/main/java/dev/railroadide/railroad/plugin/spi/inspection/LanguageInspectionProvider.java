package dev.railroadide.railroad.plugin.spi.inspection;

/**
 * Base SPI for language-specific inspection providers.
 * <p>
 * Language integrations can define richer provider contracts that extend this type while
 * sharing a common registration model.
 */
public interface LanguageInspectionProvider {
    /**
     * Stable provider id.
     *
     * @return stable provider id
     */
    String id();

    /**
     * Language id this provider contributes inspections for.
     *
     * @return target language id
     */
    String languageId();
}
