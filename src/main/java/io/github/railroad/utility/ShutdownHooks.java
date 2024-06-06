package io.github.railroad.utility;

import java.util.ArrayList;
import java.util.List;

public final class ShutdownHooks {
    private static final List<Runnable> HOOKS = new ArrayList<>();

    public static void addHook(Runnable hook) {
        HOOKS.add(hook);
    }

    public static void runHooks() {
        HOOKS.forEach(Runnable::run);
    }
}
