package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;

@Getter
public class Setting<T> {
    private final Class<T> type;
    private final String id;
    private final T defaultValue;

    private final EventType eventType;
    private final EventHandler eventHandler;

    private SimpleObjectProperty<T> value = new SimpleObjectProperty<>();
    //TODO allow multiple event handlers
    public Setting(String id, Class<T> type, T defaultValue, EventType eventType, EventHandler<?> eventHandler) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
        this.eventType = eventType;
        this.eventHandler = eventHandler;

        //TODO maybe instead of a default value, use the config value :skull:

        //TODO instead of one event handler, pass in a Map of Event to EventHandler, and loop through them and add them.
        //TODO also move the on action event handler to just a node.onAction(() -> {}) to avoid unnecessary calls
        setValue(defaultValue);
    }

    public T getValue() {
        return value.getValue();
    }

    //FIXME uh this is not my best piece of work
    public void setValue(Object value) {
        Railroad.LOGGER.debug("Setting value of {} to {}", id, value);
        //TODO here, set the value in the config file
        this.value.setValue((T) value);
    }
}