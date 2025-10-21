package dev.railroadide.railroad.project.onboarding;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

@EqualsAndHashCode
@ToString
public final class OnboardingContext {
    private final ObservableMap<String, Object> data = FXCollections.observableHashMap();
    private final Map<String, Boolean> keyRefreshMap = new HashMap<>();
    private final Executor executor;

    public OnboardingContext(Executor executor) {
        this.executor = executor;
    }

    public ObservableMap<String, Object> data() {
        return data;
    }

    public Executor executor() {
        return executor;
    }

    public void markForRefresh(String key) {
        keyRefreshMap.put(key, true);
    }

    public boolean needsRefresh(String key) {
        return keyRefreshMap.getOrDefault(key, false);
    }

    public void clearRefreshMark(String key) {
        keyRefreshMap.put(key, false);
    }

    public void clearAllRefreshMarks() {
        keyRefreshMap.clear();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!data.containsKey(key))
            return null;

        return (T) data.get(key);
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }
}
