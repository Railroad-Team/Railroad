package dev.railroadide.railroad.utility;

import dev.railroadide.core.utility.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class CommandUtils {
    private CommandUtils() {
        throw new UnsupportedOperationException("Instantiated utility class");
    }

    public static boolean canRunCommand(long timeoutMs, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            if (timeoutMs != -1L) {
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return false;
                }
            }

            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<Path> findPathsOfExecutable(long timeoutMs, String executableName) {
        String[] cmd = new String[]{(OperatingSystem.isWindows() ? "where" : "which"), executableName};
        List<String> lines = runAndCollectLines(timeoutMs, cmd);

        List<Path> paths = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (!line.isBlank()) {
                try {
                    paths.add(Path.of(line));
                } catch (Exception ignored) {
                }
            }
        }

        return paths;
    }

    public static List<String> runAndCollectLines(long timeoutMs, String... command) {
        try {
            Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

            if (timeoutMs != -1L) {
                boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return Collections.emptyList();
                }
            }

            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }

                return lines;
            }
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public static Optional<Path> findPathOfExecutable(long timeoutMs, String executableName) {
        List<Path> paths = findPathsOfExecutable(timeoutMs, executableName);
        return paths.isEmpty()
            ? Optional.empty()
            : Optional.ofNullable(paths.getFirst());
    }
}
