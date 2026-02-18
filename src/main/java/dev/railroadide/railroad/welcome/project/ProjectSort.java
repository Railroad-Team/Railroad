package dev.railroadide.railroad.welcome.project;

import dev.railroadide.railroad.project.RailroadProject;
import lombok.Getter;

import java.util.Comparator;

@Getter
public enum ProjectSort {
    NAME("railroad.home.welcome.sort.name", Comparator.comparing(RailroadProject::getAlias)),
    DATE("railroad.home.welcome.sort.date", Comparator.comparing(RailroadProject::getLastOpened).reversed()),
    NONE("railroad.home.welcome.sort.none", Comparator.comparing(RailroadProject::hashCode));

    private final String key;
    private final Comparator<? super RailroadProject> comparator;

    ProjectSort(String key, Comparator<? super RailroadProject> comparator) {
        this.key = key;
        this.comparator = comparator;
    }
}
