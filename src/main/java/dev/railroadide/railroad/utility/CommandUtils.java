package dev.railroadide.railroad.utility;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for executing commands and finding executables in the system.
 */
public final class CommandUtils {
    private CommandUtils() {
        throw new UnsupportedOperationException("Instantiated utility class");
    }

    /**
     * Checks if a command can be run successfully within a specified timeout.
     *
     * @param timeoutMs the timeout in milliseconds; use -1 for no timeout
     * @param command   the command to execute
     * @return true if the command runs successfully, false otherwise
     */
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
        } catch (Exception _) {
            return false;
        }
    }

    /**
     * Finds all paths of an executable in the system's PATH.
     *
     * @param timeoutMs      the timeout in milliseconds; use -1 for no timeout
     * @param executableName the name of the executable to find
     * @return a list of paths where the executable is found
     */
    public static List<Path> findPathsOfExecutable(long timeoutMs, String executableName) {
        String[] cmd = new String[]{(OperatingSystem.isWindows() ? "where" : "which"), executableName};
        List<String> lines = runAndCollectLines(timeoutMs, cmd);

        List<Path> paths = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (!line.isBlank()) {
                try {
                    paths.add(Path.of(line));
                } catch (Exception _) {
                }
            }
        }

        return paths;
    }

    /**
     * Runs a command and collects its output lines.
     *
     * @param timeoutMs the timeout in milliseconds; use -1 for no timeout
     * @param command   the command to execute
     * @return a list of output lines from the command
     */
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

            try (var reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                List<String> lines = new ArrayList<>();
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }

                return lines;
            }
        } catch (Exception _) {
            return Collections.emptyList();
        }
    }

    /**
     * Finds the first path of an executable in the system's PATH.
     *
     * @param timeoutMs      the timeout in milliseconds; use -1 for no timeout
     * @param executableName the name of the executable to find
     * @return an Optional containing the first path if found, or empty if not found
     */
    public static Optional<Path> findPathOfExecutable(long timeoutMs, String executableName) {
        List<Path> paths = findPathsOfExecutable(timeoutMs, executableName);
        return paths.isEmpty()
            ? Optional.empty()
            : Optional.ofNullable(paths.getFirst());
    }
}
