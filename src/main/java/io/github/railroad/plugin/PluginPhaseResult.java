package io.github.railroad.plugin;

import java.util.ArrayList;
import java.util.List;

public class PluginPhaseResult {
    private final List<Error> errors = new ArrayList<>();

    public List<Error> getErrors() {
        return this.errors;
    }

    public void addError(Error error) {
        this.errors.add(error);
    }
}
