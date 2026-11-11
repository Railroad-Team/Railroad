package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * Groups a project, its normalized root, and an immutable map of language indexing contexts.
 *
 * @param project the project whose files and configuration are used
 * @param projectRoot the project root directory
 * @param languages the language contexts keyed by language identifier
 * @param session live index ownership, or {@code null} for a standalone context
 * @param configurationGeneration captured session configuration revision, or zero for a standalone context
 */
public record ProjectIndexContext(
    Project project,
    Path projectRoot,
    Map<String, LanguageIndexContext> languages,
    ProjectLanguageIndexService.Session session,
    long configurationGeneration
) {
    /**
     * Creates a project context, normalizing its root and copying the language context map.
     *
     * @param project the project whose files and configuration are used
     * @param projectRoot the project root directory
     * @param languages the language contexts keyed by language identifier
     */
    public ProjectIndexContext(
        Project project,
        Path projectRoot,
        Map<String, LanguageIndexContext> languages
    ) {
        this(project, projectRoot, languages, null, 0);
    }

    /**
     * Captures the session's current configuration generation.
     *
     * @param project the project whose files and configuration are used
     * @param projectRoot the project root directory
     * @param languages the language contexts keyed by language identifier
     * @param session live index ownership, or {@code null} for a standalone context
     */
    public ProjectIndexContext(
        Project project,
        Path projectRoot,
        Map<String, LanguageIndexContext> languages,
        ProjectLanguageIndexService.Session session
    ) {
        this(project, projectRoot, languages, session, session == null ? 0 : session.configurationGeneration());
    }

    /**
     * Creates a context with an explicit configuration generation, normalizing its root and copying its language map.
     *
     * @param project the project whose files and configuration are used
     * @param projectRoot the project root directory
     * @param languages the language contexts keyed by language identifier
     * @param session live index ownership, or {@code null} for a standalone context
     * @param configurationGeneration captured session configuration revision, or zero for a standalone context
     */
    public ProjectIndexContext {
        project = Objects.requireNonNull(project, "project");
        projectRoot = normalize(projectRoot);
        languages = Map.copyOf(Objects.requireNonNull(languages, "languages"));
    }

    /**
     * Looks up the indexing context registered for a language.
     *
     * @param languageId the stable language identifier
     * @return the language context, or {@code null} if absent
     */
    public @Nullable LanguageIndexContext language(String languageId) {
        return languages.get(languageId);
    }

    /**
     * Looks up a language context and verifies that it has the requested runtime type.
     *
     * @param <T> the expected language context type
     * @param languageId the stable language identifier
     * @param type the expected runtime context type
     * @return the language context, or {@code null} if absent
     * @throws IllegalStateException if the context has a different runtime type
     */
    public <T extends LanguageIndexContext> @Nullable T language(String languageId, Class<T> type) {
        LanguageIndexContext context = language(languageId);
        if (context == null)
            return null;

        if (!type.isInstance(context))
            throw new IllegalStateException(
                "Language context for language " + languageId + " is not of expected type " + type.getName());

        return type.cast(context);
    }

    private static Path normalize(Path path) {
        return FileUtils.normalizePath(Objects.requireNonNull(path, "path"));
    }
}
