package io.github.railroad.plugin;

import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Getter
public class PluginPhaseResult implements Serializable {
    private final List<Error> errors = new ArrayList<>();

    public void addError(Error error) {
        this.errors.add(error);
    }
}
