package dev.railroadide.railroad.utility.function;

@FunctionalInterface
public interface ExceptionlessRunnable extends Runnable {
    @Override
    default void run() {
        try {
            onRun();
        } catch (Exception _) {
        }
    }

    void onRun() throws Exception;
}
