package dev.railroadide.railroad.welcome.project;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import lombok.Getter;

import java.util.Comparator;

/** Project ordering choices and their localized display labels. */
@Getter
public enum ProjectSort {
    /** Orders projects by alias using natural, case-sensitive string ordering. */
    NAME("railroad.home.welcome.sort.name", Comparator.comparing(Project::getAlias)),
    /** Orders projects by last-opened time, most recent first. */
    DATE(
        "railroad.home.welcome.sort.date", Comparator.comparing(Project::getLastOpened).reversed()),
    /** Default ordering, implemented by ascending project hash code rather than insertion order. */
    NONE(
        "railroad.home.welcome.sort.none", Comparator.comparing(Project::hashCode));

    /**
     * Translation key for this ordering choice.
     *
     * @return the display label's translation key
     */
    private final String key;
    /**
     * Comparator used to order projects for this choice.
     *
     * @return the project comparator
     */
    private final Comparator<? super Project> comparator;

    ProjectSort(String key, Comparator<? super Project> comparator) {
        this.key = key;
        this.comparator = comparator;
    }
}
