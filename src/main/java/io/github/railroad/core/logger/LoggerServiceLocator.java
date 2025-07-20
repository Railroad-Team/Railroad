package io.github.railroad.core.logger;

/**
 * A service locator for the LoggerService implementation.
 * This class allows setting and retrieving a singleton instance of LoggerService.
 * It ensures that the LoggerService is set before it can be used.
 */
public class LoggerServiceLocator {
    private static volatile LoggerService instance;

    private LoggerServiceLocator() {
        // NO-OP: Prevent instantiation
    }

    /**
     * Sets the LoggerService implementation.
     * This method should be called once to set the LoggerService instance.
     *
     * @param impl The LoggerService implementation to set.
     * @throws IllegalArgumentException if the provided implementation is null.
     */
    public static void setInstance(LoggerService impl) {
        if (impl == null)
            throw new IllegalArgumentException("LoggerService implementation cannot be null");

        instance = impl;
    }

    /**
     * Retrieves the LoggerService instance.
     * This method should be called after setInstance() has been called.
     *
     * @return The LoggerService instance.
     * @throws IllegalStateException if the LoggerService has not been set.
     */
    public static LoggerService getInstance() {
        LoggerService service = instance;
        if (service == null)
            throw new IllegalStateException("LoggerService has not been set. Please call setInstance() before using getInstance().");

        return service;
    }
}
