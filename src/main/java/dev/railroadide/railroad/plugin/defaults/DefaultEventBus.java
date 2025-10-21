package dev.railroadide.railroad.plugin.defaults;

import dev.railroadide.railroadpluginapi.event.Event;
import dev.railroadide.railroadpluginapi.event.EventBus;
import dev.railroadide.railroadpluginapi.event.EventListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultEventBus implements EventBus {
    private final Map<Class<? extends Event>, CopyOnWriteArrayList<EventListener<? extends Event>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(Event event) {
        Class<?> eventType = event.getClass();
        for (Map.Entry<Class<? extends Event>, CopyOnWriteArrayList<EventListener<? extends Event>>> entry : subscribers.entrySet()) {
            if (entry.getKey().isAssignableFrom(eventType)) {
                CopyOnWriteArrayList<EventListener<? extends Event>> listeners = entry.getValue();
                for (EventListener<? extends Event> listener : listeners) {
                    // Suppress unchecked cast warning, as we know the type is correct
                    @SuppressWarnings("unchecked")
                    EventListener<Event> typedListener = (EventListener<Event>) listener;
                    typedListener.handle(event);
                }
            }
        }
    }

    @Override
    public <T extends Event> void subscribe(Class<T> eventType, EventListener<T> listener) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
}
