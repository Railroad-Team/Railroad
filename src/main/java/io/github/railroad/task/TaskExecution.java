package io.github.railroad.task;

import javafx.beans.property.IntegerProperty;

@FunctionalInterface
public interface TaskExecution {
    void execute(IntegerProperty progress);

    default void cancel() {}
}
