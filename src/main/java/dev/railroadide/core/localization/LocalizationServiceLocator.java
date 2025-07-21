package dev.railroadide.core.localization;

/**
 * A service locator for the LocalizationService.
 * This class allows you to set and retrieve a LocalizationService instance.
 * It ensures that the instance is set before it can be retrieved.
 */
public class LocalizationServiceLocator {
    private static volatile LocalizationService instance;

    private LocalizationServiceLocator() {
        // NO-OP: Prevent instantiation
    }

    /**
     * Sets the LocalizationService instance.
     * This method should be called once to set the implementation of LocalizationService.
     *
     * @param impl The LocalizationService implementation to set.
     * @throws IllegalArgumentException if impl is null.
     */
    public static void setInstance(LocalizationService impl) {
        if (impl == null)
            throw new IllegalArgumentException("LocalizationService implementation cannot be null");

        instance = impl;
    }

    /**
     * Retrieves the LocalizationService instance.
     * This method should be called after setInstance() has been called.
     *
     * @return The LocalizationService instance.
     * @throws IllegalStateException if the instance has not been set.
     */
    public static LocalizationService getInstance() {
        LocalizationService service = instance;
        if (service == null)
            throw new IllegalStateException("LocalizationService has not been set. Please call setInstance() before using getInstance().");

        return service;
    }
}
