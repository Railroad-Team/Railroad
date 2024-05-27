package io.github.railroad.project;

import io.github.railroad.project.data.Project;

import java.util.Comparator;

public enum ProjectSort {
    NAME("railroad.home.welcome.sort.name", Comparator.comparing(Project::getAlias)),
    DATE("railroad.home.welcome.sort.date", Comparator.comparing(Project::getLastOpened).reversed()),
    NONE("railroad.home.welcome.sort.none", Comparator.comparing(Project::hashCode));

    private final String key;
    private final Comparator<? super Project> comparator;

    ProjectSort(String key, Comparator<? super Project> comparator) {
        this.key = key;
        this.comparator = comparator;
    }

    public String getName() {
        return key;
    }

    public Comparator<? super Project> getComparator() {
        return comparator;
    }
}
