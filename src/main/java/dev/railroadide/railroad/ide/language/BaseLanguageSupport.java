package dev.railroadide.railroad.ide.language;

import java.util.Set;

/**
 * Provides language identity and immutable extensions with optional text features disabled by default.
 */
public abstract class BaseLanguageSupport implements LanguageSupport {
    private final String languageId;
    private final String displayName;
    private final Set<String> extensions;

    protected BaseLanguageSupport(String languageId, String displayName, Set<String> extensions) {
        this.languageId = languageId;
        this.displayName = displayName;
        this.extensions = Set.copyOf(extensions);
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
        return extensions;
    }

}
