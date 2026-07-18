package dev.railroadide.railroad.ide.language.index;

import dev.railroadide.railroad.plugin.spi.dto.Project;

public interface LanguageIndexContextContributor {
    String languageId();

    LanguageIndexContext resolve(Project project);
}
