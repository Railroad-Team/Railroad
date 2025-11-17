package dev.railroadide.railroad.java.cli;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public interface CLIBuilder<R, T extends CLIBuilder<R, T>> {
    T addArgument(String arg);

    T setWorkingDirectory(Path path);

    T setEnvironmentVariable(String key, String value);

    T useSystemEnvironmentVariables(boolean useSystemVars);

    default T setTimeout(long seconds) {
        return setTimeout(seconds, TimeUnit.SECONDS);
    }

    T setTimeout(long duration, TimeUnit unit);

    R run();
}
