package dev.railroadide.railroad.plugin.spi.inspection;

import java.nio.file.Path;

/**
 * Identifies the document snapshot being evaluated by language inspection rules.
 */
public interface LanguageRuleContext {
    /**
     * Returns the language of the inspected document.
     *
     * @return language identifier used by inspection providers
     */
    String languageId();

    /**
     * Returns the location of the inspected document.
     *
     * @return source file path
     */
    Path filePath();

    /**
     * Returns the source text captured for this inspection.
     *
     * @return complete document text
     */
    String documentText();
}
