package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record ProjectIndexContext(
    Project project,
    Path projectRoot,
    Map<String, LanguageIndexContext> languages
) {
    public ProjectIndexContext(
        Project project,
        Path projectRoot,
        Map<String, LanguageIndexContext> languages
    ) {
        this.project = Objects.requireNonNull(project, "project");
        this.projectRoot = normalize(projectRoot);
        this.languages = Map.copyOf(Objects.requireNonNull(languages, "languages"));
    }

    public @Nullable LanguageIndexContext language(String languageId) {
        return languages.get(languageId);
    }

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
