package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.ide.language.LanguageSupport;
import dev.railroadide.railroad.ide.language.LanguageSupportRegistry;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.FileUtils;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DefaultProjectIndexContextResolver implements ProjectIndexContextResolver {
    @Override
    public ProjectIndexContext resolve(Project project) {
        Objects.requireNonNull(project, "project");

        Map<String, LanguageIndexContext> contexts = LanguageSupportRegistry.all().stream()
            .map(LanguageSupport::createIndexContextContributor)
            .filter(Objects::nonNull)
            .map(contributor -> contributor.resolve(project))
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(LanguageIndexContext::languageId, Function.identity()));

        return new ProjectIndexContext(
            project,
            FileUtils.normalizePath(project.path()),
            contexts);
    }
}
