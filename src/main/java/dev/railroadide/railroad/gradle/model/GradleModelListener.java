package dev.railroadide.railroad.gradle.model;

/**
 * Listener interface for receiving notifications about Gradle model reload events.
 */
public interface GradleModelListener {
    /**
     * Called when a model reload is initiated.
     */
    void modelReloadStarted();

    /**
     * Called when a model reload completes successfully.
     *
     * @param model the reloaded Gradle build model
     */
    void modelReloadSucceeded(GradleBuildModel model);

    /**
     * Called when a model reload fails.
     *
     * @param error the error that caused the reload to fail
     */
    void modelReloadFailed(Throwable error);
}
