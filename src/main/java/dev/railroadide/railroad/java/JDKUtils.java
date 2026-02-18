package dev.railroadide.railroad.java;

import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.utility.JavaVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility helpers for working with installed JDK distributions. Provides functionality for
 * resolving JDK home directories and parsing version metadata sourced from release files or
 * java executables.
 */
public final class JDKUtils {
    private static final Pattern JAVA_VERSION_PATTERN =
        Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.\\d+)?(?:_\\d+)?$");

    private JDKUtils() {
        // static helper class
    }

    /**
     * Loads the {@code release} metadata file from the supplied Java home directory, if present.
     *
     * @param javaHome root directory of the JDK installation
     * @return properties parsed from the file; empty if the file is missing or unreadable
     */
    public static Properties readReleaseProperties(Path javaHome) {
        var props = new Properties();
        Path release = javaHome.resolve("release");
        if (Files.isRegularFile(release)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(release)) {
                props.load(bufferedReader);
            } catch (IOException ignored) {
            }
        }

        return props;
    }

    /**
     * Reads the JDK version metadata from the supplied home directory.
     *
     * @param javaHome           root directory of the JDK installation
     * @param javaExecutableName preferred executable filename for the current OS (e.g., {@code java.exe})
     * @return parsed {@link JavaVersion} or {@code null} if detection failed
     */
    public static JavaVersion readJavaVersion(Path javaHome, String javaExecutableName) {
        var properties = readReleaseProperties(javaHome);
        String versionStr = stripQuotes(properties.getProperty("JAVA_VERSION"));
        JavaVersion version = parseJavaVersionString(versionStr);
        if (version != null)
            return version;

        String exe = javaHome.resolve("bin").resolve(javaExecutableName).toString();
        return getJavaVersionFromProcess(exe);
    }

    /**
     * Parses a Java version string into a {@link JavaVersion} descriptor.
     *
     * @param version raw version string from the metadata or {@code java -version} output
     * @return structured version, or {@code null} when the input cannot be parsed
     */
    public static JavaVersion parseJavaVersionString(String version) {
        if (version == null)
            return null;

        Matcher matcher = JAVA_VERSION_PATTERN.matcher(version);
        if (!matcher.find())
            return null;

        try {
            int major = Integer.parseInt(matcher.group(1));
            Integer minor = null;
            if (matcher.group(2) != null) {
                try {
                    minor = Integer.parseInt(matcher.group(2));
                } catch (NumberFormatException exception) {
                    Railroad.LOGGER.warn("Failed to parse minor version from Java version string: {}", version, exception);
                }
            }

            if (major == 1 && minor != null) {
                major = minor;
                minor = 0;
            }
            return new JavaVersion(major, minor != null ? minor : 0);
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse Java version string: {}", version, exception);
            return null;
        }
    }

    /**
     * Removes wrapping double quotes from the supplied string.
     *
     * @param str string that may contain surrounding quotes
     * @return unquoted string if applicable
     */
    public static String stripQuotes(String str) {
        if (str == null)
            return null;

        return str.length() >= 2 && str.startsWith("\"") && str.endsWith("\"") ?
            str.substring(1, str.length() - 1) :
            str;
    }

    /**
     * Attempts to resolve the Java home directory given a file or directory anywhere within the JDK.
     *
     * @param candidate          path that may reference the JDK root, {@code bin/java}, or other known layouts
     * @param javaExecutableName preferred executable filename for the current OS (e.g., {@code java.exe})
     * @return resolved home directory if one could be derived; otherwise {@code null}
     */
    public static Path resolveJavaHome(Path candidate, String javaExecutableName) {
        Path resolved = maybeRealPath(candidate);
        if (Files.isDirectory(resolved)) {
            if (Files.isExecutable(resolved.resolve("bin").resolve(javaExecutableName))) {
                return isMacSystemJavaStub(resolved) ?
                    resolveMacJavaHome(javaExecutableName) :
                    resolved;

            }

            if (Files.isExecutable(resolved.resolve("jre").resolve("bin").resolve(javaExecutableName)))
                return resolved;

            Path macHome = resolved.resolve("Contents").resolve("Home");
            if (Files.isExecutable(macHome.resolve("bin").resolve(javaExecutableName)))
                return macHome;

            if (resolved.getFileName() != null && resolved.getFileName().toString().equalsIgnoreCase("bin")) {
                Path parent = resolved.getParent();
                if (parent != null) {
                    if (isMacSystemJavaStub(parent))
                        return resolveMacJavaHome(javaExecutableName);

                    if (Files.isExecutable(parent.resolve("bin").resolve(javaExecutableName)))
                        return parent;
                }
            }

            return null;
        }

        Path parent = resolved.getParent();
        if (parent == null)
            return null;

        String parentName = parent.getFileName() != null ? parent.getFileName().toString() : "";
        if (parentName.equals("bin") && parent.getParent() != null &&
            parent.getParent().getFileName() != null &&
            parent.getParent().getFileName().toString().equals("Home")) {
            Path home = parent.getParent();
            if (home.getParent() != null && home.getParent().getFileName() != null &&
                home.getParent().getFileName().toString().equals("Contents")) {
                return home;
            }
        }

        if (parentName.equalsIgnoreCase("bin")) {
            Path home = parent.getParent();
            if (home != null) {
                if (isMacSystemJavaStub(home))
                    return resolveMacJavaHome(javaExecutableName);

                if (Files.isExecutable(home.resolve("bin").resolve(javaExecutableName)))
                    return home;
            }
        }

        if (parentName.equalsIgnoreCase("bin") && parent.getParent() != null &&
            parent.getParent().getFileName() != null &&
            parent.getParent().getFileName().toString().equalsIgnoreCase("jre")) {
            return parent.getParent().getParent();
        }

        return null;
    }

    /**
     * Scans the current {@code PATH} for a Java executable matching the platform-specific name.
     *
     * @return absolute path to the first matching executable, or {@code null} when none are found
     */
    public static String findJavaOnPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null)
            return null;

        for (String raw : pathEnv.split(File.pathSeparator)) {
            String path = stripQuotes(raw.trim());
            if (path.isEmpty())
                continue;

            Path javaPath = Path.of(path, JDKManager.JAVA_EXECUTABLE_NAME);
            if (Files.exists(javaPath) && Files.isExecutable(javaPath))
                return javaPath.toAbsolutePath().toString();
        }

        return null;
    }

    /**
     * Attempts to construct a {@link JDK} instance by interpreting the provided path as either a JDK
     * home directory or a nested Java binary within the home.
     *
     * @param javaHomeOrExe path to inspect for a JDK installation
     * @return {@link JDK} descriptor if the path can be resolved to a valid installation; otherwise {@code null}
     */
    public static JDK createJDKFromAnyPath(String javaHomeOrExe) {
        Path path;
        try {
            path = Path.of(javaHomeOrExe);
        } catch (InvalidPathException ignored) {
            return null;
        }

        if (Files.notExists(path))
            return null;

        Path home = resolveJavaHome(path, JDKManager.JAVA_EXECUTABLE_NAME);
        if (home == null)
            return null;

        JavaVersion version = readJavaVersion(home, JDKManager.JAVA_EXECUTABLE_NAME);
        if (version == null)
            return null;

        String name = home.getFileName() != null ? home.getFileName().toString() : home.toString();
        return new JDK(home.toAbsolutePath(), name, version);
    }

    /**
     * Resolves the active JDK home reported by {@code /usr/libexec/java_home} on macOS systems.
     *
     * @param javaExecutableName platform-specific executable name (e.g. {@code java} or {@code java.exe})
     * @return resolved JDK home when available; otherwise {@code null}
     */
    private static Path resolveMacJavaHome(String javaExecutableName) {
        if (OperatingSystem.CURRENT != OperatingSystem.MAC)
            return null;

        try {
            Process process = new ProcessBuilder("/usr/libexec/java_home")
                .redirectErrorStream(true)
                .start();

            String homeDir;
            try (var bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                homeDir = bufferedReader.readLine();
            }

            process.waitFor(Settings.JAVA_VERSION_DETECTION_TIMEOUT_MS.getOrDefaultValue(), TimeUnit.MILLISECONDS);
            if (homeDir == null || homeDir.isBlank())
                return null;

            Path home = Path.of(homeDir.trim());
            return Files.isExecutable(home.resolve("bin").resolve(javaExecutableName)) ? home : null;
        } catch (IOException ignored) {
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    /**
     * Returns whether the supplied home path corresponds to the macOS system shim (e.g. {@code /usr}).
     *
     * @param home candidate home directory
     * @return {@code true} when the path represents the system stub rather than a real JDK
     */
    private static boolean isMacSystemJavaStub(Path home) {
        return OperatingSystem.CURRENT == OperatingSystem.MAC &&
            home != null &&
            home.toAbsolutePath().normalize().equals(Path.of("/usr"));
    }

    /**
     * Executes the provided Java runtime with {@code -version} and attempts to parse the output.
     *
     * @param javaExe absolute path to the Java executable
     * @return discovered {@link JavaVersion} or {@code null} if the process fails or output is unparseable
     */
    private static JavaVersion getJavaVersionFromProcess(String javaExe) {
        try {
            Process process = new ProcessBuilder(javaExe, "-version")
                .redirectErrorStream(true)
                .start();

            String line;
            try (var bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while ((line = bufferedReader.readLine()) != null) {
                    int q1 = line.indexOf('"');
                    int q2 = line.indexOf('"', q1 + 1);
                    if (q1 >= 0 && q2 > q1) {
                        String versionStr = line.substring(q1 + 1, q2);
                        JavaVersion version = parseJavaVersionString(versionStr);
                        if (version != null) {
                            process.waitFor(Settings.JAVA_VERSION_DETECTION_TIMEOUT_MS.getOrDefaultValue(), TimeUnit.MILLISECONDS);
                            return version;
                        }
                    }
                }
            }

            process.waitFor(Settings.JAVA_VERSION_DETECTION_TIMEOUT_MS.getOrDefaultValue(), TimeUnit.MILLISECONDS);
        } catch (IOException ignored) {
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return null;
    }

    /**
     * Attempts to resolve the real path for the provided candidate while tolerating resolution failures.
     *
     * @param candidate path that may contain symlinks
     * @return canonical real path when available; otherwise the original candidate
     */
    private static Path maybeRealPath(Path candidate) {
        try {
            return candidate.toRealPath();
        } catch (IOException exception) {
            return candidate;
        }
    }
}
