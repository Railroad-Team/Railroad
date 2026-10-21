package dev.railroadide.railroad.project.creation;

/**
 * Receives progress, status messages, and translation arguments during project creation.
 */
public interface ProgressReporter {
    /**
     * Reports the current progress relative to the total number of steps.
     *
     * @param stepIndex the current progress value
     * @param total the total number of steps
     */
    void progress(int stepIndex, int total);

    /**
     * Reports a status message or translation key.
     *
     * @param line the status text to report
     */
    void info(String line);

    /**
     * Supplies arguments for formatting a translated status message.
     *
     * @param args the translation arguments
     */
    void setArg(Object... args);
}
