package dev.railroadide.railroad.utility;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ShutdownHooks {
    private static final CopyOnWriteArrayList<Runnable> HOOKS = new CopyOnWriteArrayList<>();

    private ShutdownHooks() {
    }

    public static void addHook(Runnable hook) {
        registerHook(hook);
    }

    public static Registration registerHook(Runnable hook) {
        Objects.requireNonNull(hook, "Shutdown hook cannot be null");
        HOOKS.add(hook);
        return new Registration(() -> HOOKS.remove(hook));
    }

    public static void runHooks() {
        HOOKS.forEach(Runnable::run);
    }

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
