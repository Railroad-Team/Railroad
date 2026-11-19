package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.provider.api.*;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/** Host editor presentation for a language contribution; optional features live in its session. */
public interface LanguageSupport extends LanguageContribution {
    /**
     * Host storage identity. Built-ins retain their existing persisted index names.
     *
     * @return the stable host language identifier used for storage
     */
    String languageId();

    /**
     * Declares filename suffixes recognized by the default matcher.
     *
     * @return the supported single or compound file extensions
     */
    Set<String> fileExtensions();

    @Override
    default LanguageId id() {
        String id = languageId();
        return new LanguageId(id.contains(":") ? id : "railroad:" + id);
    }

    @Override
    default LanguageMatcher matcher() {
        return LanguageMatcher.any(fileExtensions().stream().map(LanguageMatcher::extension)
            .toArray(LanguageMatcher[]::new));
    }

    /**
     * Checks the path using the language matcher with an empty content prefix.
     *
     * @param path file path to test
     * @return whether the path matches this language
     */
    default boolean supports(Path path) {
        return matcher().match(new LanguageMatchInput(path, new byte[0])) > 0;
    }

    /**
     * Checks the file using the language matcher with an empty content prefix.
     *
     * @param file file to test
     * @return whether the file matches this language
     */
    default boolean supports(File file) {
        return supports(file.toPath());
    }

    /**
     * Creates the host editor presentation for a document.
     *
     * @param project project owning the document
     * @param documentId stable identity of the document to open
     * @param file document file path
     * @return the view and optional text editor for the document
     */
    EditorOpenView open(Project project, DocumentId documentId, Path file);

    @Override
    default LanguageSession openSession(LanguageSessionContext context) {
        return new LanguageSession(CapabilityRegistry.empty());
    }
}
