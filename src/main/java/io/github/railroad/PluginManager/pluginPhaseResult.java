package io.github.railroad.PluginManager;

import java.util.ArrayList;
import java.util.List;

public class pluginPhaseResult {
    private List<Error> errors;

    public pluginPhaseResult() {
        this.errors = new ArrayList<Error>();
    }

    public List<Error> getErrors() {
        return this.errors;
    }

    public void addError(Error error) {
        this.errors.add(error);
    }
}
