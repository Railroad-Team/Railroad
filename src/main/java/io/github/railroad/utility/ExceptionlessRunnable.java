package io.github.railroad.utility;

@FunctionalInterface
public interface ExceptionlessRunnable extends Runnable {
    @Override
    default void run() {
        try {
            onRun();
        } catch (Exception ignored) {
        }
    }

    void onRun() throws Exception;
}
