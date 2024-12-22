package io.github.railroad.plugin;

import lombok.Getter;

import java.util.Collections;
import java.util.EventObject;

@Getter
public class PluginManagerErrorEvent extends EventObject {
    private final Plugin plugin;
    private final PluginPhaseResult phaseResult;
    private final String message;

    public PluginManagerErrorEvent(Object source, Plugin plugin, String message, PluginPhaseResult phaseResult) {
        super(source);
        this.message = message;
        this.plugin = plugin;
        this.phaseResult = new PluginPhaseResult();
        for (Error error : phaseResult.getErrors()) {
            this.phaseResult.addError(error);
        }
    }

}
