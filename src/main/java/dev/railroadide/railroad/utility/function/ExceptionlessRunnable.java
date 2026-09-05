package dev.railroadide.railroad.utility.function;

/**
 * A {@link Runnable} that does not throw any exceptions.
 */
@FunctionalInterface
public interface ExceptionlessRunnable extends Runnable {
    @Override
    default void run() {
        try {
            onRun();
        } catch (Exception _) {
        }
    }

    /**
     * The method to be executed by this {@link ExceptionlessRunnable}.
     *
     * @throws Exception if an error occurs during execution
     */
    void onRun() throws Exception;
}
