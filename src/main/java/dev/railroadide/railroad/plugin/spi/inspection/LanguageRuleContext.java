package dev.railroadide.railroad.plugin.spi.inspection;

import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;

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
     * Returns the physical or virtual location of the inspected document.
     * Existing file-based contexts derive it from {@link #filePath()}; virtual contexts
     * override this method to supply their document URI directly.
     *
     * @return document URI
     */
    default DocumentUri documentUri() {
        return DocumentUri.fromPath(filePath());
    }

    /**
     * Returns the source text captured for this inspection.
     *
     * @return complete document text
     */
    String documentText();
}
