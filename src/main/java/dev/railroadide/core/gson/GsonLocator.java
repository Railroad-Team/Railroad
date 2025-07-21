package dev.railroadide.core.gson;

import com.google.gson.Gson;

/**
 * A utility class to manage a singleton instance of Gson.
 * This allows for centralized configuration and access to Gson throughout the application.
 */
public class GsonLocator {
    private static volatile Gson instance;

    private GsonLocator() {
        // NO-OP: Prevent instantiation
    }

    /**
     * Sets the singleton instance of Gson.
     * This should be called once during application initialization.
     *
     * @param impl The Gson implementation to set.
     * @throws IllegalArgumentException if the provided Gson instance is null.
     */
    public static void setInstance(Gson impl) {
        if (impl == null)
            throw new IllegalArgumentException("Gson implementation cannot be null");

        instance = impl;
    }

    /**
     * Retrieves the singleton instance of Gson.
     * This should be called after setInstance() has been called.
     *
     * @return The singleton Gson instance.
     * @throws IllegalStateException if the Gson instance has not been set.
     */
    public static Gson getInstance() {
        Gson gson = instance;
        if (gson == null)
            throw new IllegalStateException("Gson has not been set. Please call setInstance() before using getInstance().");

        return gson;
    }
}
