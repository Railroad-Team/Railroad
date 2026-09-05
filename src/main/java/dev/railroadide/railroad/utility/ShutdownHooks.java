package dev.railroadide.railroad.utility;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A utility class for managing shutdown hooks.
 * <p>
 * This class allows you to register and run shutdown hooks, which are tasks that will be executed when the application
 * is shutting down.
 * It provides methods to add hooks, register hooks with automatic removal, and run all registered hooks.
 */
public final class ShutdownHooks {
    private static final CopyOnWriteArrayList<Runnable> HOOKS = new CopyOnWriteArrayList<>();

    private ShutdownHooks() {
    }

    /**
     * Adds a shutdown hook to be executed when the application is shutting down.
     *
     * @param hook the shutdown hook to add
     * @throws NullPointerException if the hook is null
     */
    public static void addHook(Runnable hook) {
        registerHook(hook);
    }

    /**
     * Registers a shutdown hook and returns a {@link Registration} object that can be used to remove the hook later.
     *
     * @param hook the shutdown hook to register
     * @return a {@link Registration} object for removing the hook
     * @throws NullPointerException if the hook is null
     */
    public static Registration registerHook(Runnable hook) {
        Objects.requireNonNull(hook, "Shutdown hook cannot be null");
        HOOKS.add(hook);
        return new Registration(() -> HOOKS.remove(hook));
    }

    /**
     * Runs all registered shutdown hooks.
     * <p>
     * This method should be called when the application is shutting down to execute all registered hooks.
     */
    public static void runHooks() {
        HOOKS.forEach(Runnable::run);
    }

    /**
     * A registration object that allows for the removal of a registered shutdown hook.
     * <p>
     * This class implements {@link AutoCloseable}, allowing it to be used in try-with-resources statements for
     * automatic removal of the hook.
     */
    public static final class Registration implements AutoCloseable {
        private Runnable removal;

        private Registration(Runnable removal) {
            this.removal = removal;
        }

        @Override
        public void close() {
            if (removal == null)
                return;

            removal.run();
            removal = null;
        }
    }
}
