package dev.railroadide.railroad.project.onboarding;

import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Shared observable values, refresh markers, and executor for an onboarding session.
 */
@EqualsAndHashCode
@ToString
public final class OnboardingContext {
    private final ObservableMap<String, Object> data = FXCollections.observableHashMap();
    private final Map<String, Boolean> keyRefreshMap = new HashMap<>();
    private final Executor executor;

    /**
     * Creates an empty context using the supplied executor.
     *
     * @param executor executor available to steps for background work
     */
    public OnboardingContext(Executor executor) {
        this.executor = executor;
    }

    /**
     * Returns the mutable observable map shared by all steps.
     *
     * @return the live context map
     */
    public ObservableMap<String, Object> data() {
        return data;
    }

    /**
     * Returns the executor available for background onboarding work.
     *
     * @return the supplied executor
     */
    public Executor executor() {
        return executor;
    }

    /**
     * Marks a key so a form step skips restoring its cached context value on entry.
     *
     * @param key context key to inspect or update
     */
    public void markForRefresh(String key) {
        keyRefreshMap.put(key, true);
    }

    /**
     * Checks whether a key is marked for refresh.
     *
     * @param key context key to inspect or update
     * @return {@code true} if marked; {@code false} for an unmarked or unknown key
     */
    public boolean needsRefresh(String key) {
        return keyRefreshMap.getOrDefault(key, false);
    }

    /**
     * Clears the refresh flag for a single key.
     *
     * @param key context key to inspect or update
     */
    public void clearRefreshMark(String key) {
        keyRefreshMap.put(key, false);
    }

    /**
     * Clears every refresh marker without changing stored context values.
     */
    public void clearAllRefreshMarks() {
        keyRefreshMap.clear();
    }

    /**
     * Looks up a value using the caller's expected type.
     *
     * @param <T> expected type of the stored value; the caller is responsible for choosing a compatible type
     * @param key context key to inspect or update
     * @return the stored value, or {@code null} if the key is absent or maps to null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (!data.containsKey(key))
            return null;

        return (T) data.get(key);
    }

    /**
     * Associates a value with a context key, replacing any previous value.
     *
     * @param key context key to inspect or update
     * @param value value to associate with the key, possibly {@code null}
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Checks whether the context contains a mapping for a key.
     *
     * @param key context key to inspect or update
     * @return {@code true} if the key is present, including a mapping to {@code null}
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }
}
