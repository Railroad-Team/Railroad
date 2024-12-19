package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.EventHandler;
import javafx.event.EventType;
import lombok.Getter;

import java.util.Map;

@Getter
public class Setting<T> {
    private final Class<T> type;
    private final String id;
    private final T defaultValue;

    private final Map<EventType, EventHandler> eventHandlers;

    private SimpleObjectProperty<T> value = new SimpleObjectProperty<>();
    public Setting(String id, Class<T> type, T defaultValue, Map<EventType, EventHandler> eventHandlers) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
        this.eventHandlers = eventHandlers;
    }

    public T getValue() {
        return value.getValue();
    }

    //FIXME uh this is not my best piece of work
    public void setValue(Object value) {
        Railroad.LOGGER.debug("Setting value of {} to {}", id, value);
        this.value.setValue((T) value);
        //TODO: maybe create a apply settings button? rather than updating the file every time a setting is changed
        Railroad.SETTINGS_MANAGER.saveSettings();
    }
}