package io.github.railroad.project;

import java.util.Comparator;

public enum ProjectSort {
    NAME("Name", Comparator.comparing(Project::getAlias)),
    DATE("Date", Comparator.comparing(Project::getLastOpened).reversed()),
    NONE("None", Comparator.comparing(Project::hashCode));

    private final String name;
    private final Comparator<? super Project> comparator;

    ProjectSort(String name, Comparator<? super Project> comparator) {
        this.name = name;
        this.comparator = comparator;
    }

    public String getName() {
        return name;
    }

    public Comparator<? super Project> getComparator() {
        return comparator;
    }
}
