package dev.railroadide.railroad.gradle.service;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.model.GradleModelListener;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the background import of Gradle models and exposes task metadata.
 */
public interface GradleModelService {
    /**
     * Refreshes the Gradle model, optionally forcing a rebuild even if cached data exists.
     *
     * @param force whether to bypass caches when refreshing the model
     * @return a future that completes with the refreshed model
     */
    CompletableFuture<GradleBuildModel> refreshModel(boolean force);

    /**
     * Returns the last successful Gradle build model that was loaded, if any.
     *
     * @return the last successful Gradle build model that was loaded
     */
    Optional<GradleBuildModel> getCachedModel();

    /**
     * Adds a listener to be notified of model changes.
     *
     * @param listener the listener to add
     */
    void addListener(GradleModelListener listener);

    /**
     * Removes a previously added listener.
     *
     * @param listener the listener to remove
     */
    void removeListener(GradleModelListener listener);
}
