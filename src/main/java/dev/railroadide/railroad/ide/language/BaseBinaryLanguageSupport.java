package dev.railroadide.railroad.ide.language;

import java.util.Set;
import dev.railroadide.railroad.ide.sst.document.api.ContentKind;

/**
 * Provides language identity and extensions for binary editors without text language features.
 */
public abstract class BaseBinaryLanguageSupport implements LanguageSupport {
    private final String languageId;
    private final String displayName;
    private final Set<String> fileExtensions;

    /**
     * Initializes binary language metadata and copies the supported extensions.
     *
     * @param languageId the stable language identifier
     * @param displayName the language name shown to users
     * @param fileExtensions the recognized file extensions without leading dots
     */
    public BaseBinaryLanguageSupport(String languageId, String displayName, String... fileExtensions) {
        this.languageId = languageId;
        this.displayName = displayName;
        this.fileExtensions = Set.of(fileExtensions);
    }

    @Override
    public String languageId() {
        return languageId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public Set<String> fileExtensions() {
        return fileExtensions;
    }

    @Override
    public ContentKind contentKind() {
        return ContentKind.BINARY;
    }

}
