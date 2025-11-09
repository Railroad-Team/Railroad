package dev.railroadide.railroad.java;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.JavaVersion;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Locates locally installed JDK distributions across supported platforms and caches the results
 * for quick lookup. Provides helpers to refresh the cache and query the discovered installations.
 */
public class JDKManager {
    public static final String JAVA_EXECUTABLE_NAME = javaExecutableName();

    private static final List<String> WIN_JDK_PATHS = List.of(
        "{drive}:\\Program Files\\Java",
        "{drive}:\\Program Files (x86)\\Java",
        "{drive}:\\Program Files\\Eclipse Adoptium",
        "{drive}:\\Program Files (x86)\\Eclipse Adoptium",
        "{drive}:\\Program Files\\Azul",
        "{drive}:\\Program Files (x86)\\Azul",
        "{drive}:\\Program Files\\Zulu",
        "{drive}:\\Program Files (x86)\\Zulu",
        "{drive}:\\Program Files\\Amazon Corretto",
        "{drive}:\\Program Files (x86)\\Amazon Corretto",
        "{drive}:\\Program Files\\BellSoft",
        "{drive}:\\Program Files\\GraalVM"
    );
    private static final List<String> LINUX_JDK_PATHS = List.of(
        "/usr/lib/jvm",
        "/usr/java",
        "/opt/java",
        "/opt/jdk"
    );
    private static final List<JDK> JDKS = new CopyOnWriteArrayList<>();

    /**
     * Returns all discovered JDKs from the most recent scan.
     *
     * @return cached, unmodifiable list of JDK descriptors
     */
    public static List<JDK> getAvailableJDKs() {
        return Collections.unmodifiableList(JDKS);
    }

    /**
     * Retrieves the first discovered JDK from the cached list, or {@code null} if no JDKs are found.
     *
     * @return first available JDK or {@code null} when none are detected
     */
    public static @Nullable JDK getDefaultJDK() {
        List<JDK> jdks = getAvailableJDKs();
        if (jdks.isEmpty())
            return null;

        return jdks.getFirst();
    }

    /**
     * Re-scans all known sources (environment variables, PATH entries, and configured directories)
     * to rebuild the cached list of JDK installations.
     */
    public static void refreshJDKs() {
        JDKS.clear();
        JDKS.addAll(discoverJDKs());

        for (JDK jdk : JDKS) {
            Railroad.LOGGER.debug("Detected JDK: {} (brand: {}, version: {}, path: {})",
                jdk.name(), jdk.brand(), jdk.version(), jdk.path());
        }
    }

    /**
     * Filters cached JDKs to only those whose version falls within the provided range.
     *
     * @param minVersion inclusive lower bound; pass {@code null} to ignore the lower bound
     * @param maxVersion inclusive upper bound; pass {@code null} to ignore the upper bound
     * @return list of JDKs whose {@link JavaVersion} is within the requested bounds
     */
    public static List<JDK> getJDKsInVersionRange(JavaVersion minVersion, JavaVersion maxVersion) {
        List<JDK> filtered = new ArrayList<>();
        for (JDK jdk : JDKS) {
            if ((minVersion == null || jdk.version().compareTo(minVersion) >= 0) &&
                (maxVersion == null || jdk.version().compareTo(maxVersion) <= 0)) {
                filtered.add(jdk);
            }
        }

        return filtered;
    }

    /**
     * Scans every configured source of JDK installations and produces a deduplicated list.
     *
     * @return list of discovered {@link JDK} descriptors
     */
    private static List<JDK> discoverJDKs() {
        // Location 1a: JAVA_HOME environment variable
        List<JDK> jdks = new ArrayList<>();
        List<Path> excludedPaths = FileUtils.normalizePaths(Settings.EXCLUDED_JDK_SCAN_PATHS.getOrDefaultValue());
        List<Path> manualJdkPaths = FileUtils.normalizePaths(Settings.ADDITIONAL_JDKS.getOrDefaultValue());
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            addIfValid(jdks, JDKUtils.createJDKFromAnyPath(javaHome), excludedPaths);
        }

        // Location 1b: JDK_HOME environment variable
        String jdkHome = System.getenv("JDK_HOME");
        if (jdkHome != null && !jdkHome.isEmpty()) {
            addIfValid(jdks, JDKUtils.createJDKFromAnyPath(jdkHome), excludedPaths);
        }

        // Location 2: System PATH
        String javaPath = JDKUtils.findJavaOnPath();
        if (javaPath != null) {
            addIfValid(jdks, JDKUtils.createJDKFromAnyPath(javaPath), excludedPaths);
        }

        // Location 3: Common installation directories
        discoverJDKsInCommonDirectories(jdks, excludedPaths);

        // Location 4: User-provided JDK executables
        for (Path manualPath : manualJdkPaths) {
            addIfValid(jdks, JDKUtils.createJDKFromAnyPath(manualPath.toString()), excludedPaths);
        }

        return removeDuplicateJDKs(jdks, excludedPaths);
    }

    /**
     * Traverses known installation directories (SDKMAN, vendor folders, etc.) and registers any
     * detected JDK executables while respecting the provided exclusion list.
     *
     * @param jdks          collection being populated with discovered JDKs
     * @param excludedPaths normalized paths that should be ignored during discovery
     */
    private static void discoverJDKsInCommonDirectories(List<JDK> jdks, List<Path> excludedPaths) {
        for (Path dir : getPossibleJDKPaths()) {
            if (isExcluded(dir, excludedPaths))
                continue;

            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        if (!Files.isDirectory(entry))
                            continue;

                        if (isExcluded(entry, excludedPaths))
                            continue;

                        Path exe;
                        if (OperatingSystem.CURRENT == OperatingSystem.MAC) {
                            // Try bundle layout first
                            Path bundle = entry.resolve("Contents").resolve("Home").resolve("bin").resolve(JAVA_EXECUTABLE_NAME);
                            // Fallback: plain folder layout (SDKMAN/asdf/.gradle/jdks, etc.)
                            Path flat = entry.resolve("bin").resolve(JAVA_EXECUTABLE_NAME);
                            exe = Files.isExecutable(bundle) ? bundle : flat;
                        } else {
                            exe = entry.resolve("bin").resolve(JAVA_EXECUTABLE_NAME);
                        }

                        addIfValid(jdks, JDKUtils.createJDKFromAnyPath(exe.toString()), excludedPaths);
                    }
                } catch (IOException exception) {
                    Railroad.LOGGER.warn("Failed to read JDKs from directory: {}", dir, exception);
                }
            }
        }
    }

    /**
     * Adds the supplied JDK to the running list when it is non-null and not excluded.
     *
     * @param jdks          aggregate list being populated
     * @param jdk           potential discovery result
     * @param excludedPaths paths that should be filtered out
     */
    private static void addIfValid(List<JDK> jdks, JDK jdk, List<Path> excludedPaths) {
        if (jdk == null)
            return;

        Path jdkPath = jdk.path();
        if (isExcluded(jdkPath, excludedPaths))
            return;

        jdks.add(jdk);
    }

    /**
     * Inserts the JDK into the map keyed by path when the location is not excluded.
     *
     * @param uniqueJDKs    map keyed by canonical JDK path
     * @param jdk           descriptor to consider for insertion
     * @param excludedPaths normalized paths that should be skipped
     */
    private static void addIfNotExcluded(Map<String, JDK> uniqueJDKs, JDK jdk, List<Path> excludedPaths) {
        Path jdkPath = jdk.path();
        if (isExcluded(jdkPath, excludedPaths))
            return;

        uniqueJDKs.putIfAbsent(jdk.path().toString(), jdk);
    }

    /**
     * Consolidates the provided discoveries by normalizing their paths and removing duplicates.
     *
     * @param jdks          discovered JDK entries
     * @param excludedPaths normalized paths that should be ignored
     * @return list with duplicate locations removed
     */
    private static List<JDK> removeDuplicateJDKs(List<JDK> jdks, List<Path> excludedPaths) {
        Map<String, JDK> uniqueJDKs = new LinkedHashMap<>();
        for (JDK jdk : jdks) {
            try {
                Path normalizedPath = jdk.path().toRealPath();
                if (!isExcluded(normalizedPath, excludedPaths)) {
                    uniqueJDKs.putIfAbsent(normalizedPath.toString(), new JDK(normalizedPath, jdk.name(), jdk.version()));
                }
            } catch (IOException | InvalidPathException ignored) {
                addIfNotExcluded(uniqueJDKs, jdk, excludedPaths);
            }
        }

        return new ArrayList<>(uniqueJDKs.values());
    }

    /**
     * Determines whether the candidate path should be skipped based on the exclusion list.
     *
     * @param candidate     path to evaluate
     * @param excludedPaths normalized paths to exclude
     * @return {@code true} when the candidate is equal to or inside any excluded path
     */
    private static boolean isExcluded(Path candidate, List<Path> excludedPaths) {
        if (candidate == null || excludedPaths == null || excludedPaths.isEmpty())
            return false;

        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        for (Path excluded : excludedPaths) {
            if (excluded == null)
                continue;

            if (normalizedCandidate.equals(excluded) || normalizedCandidate.startsWith(excluded))
                return true;
        }

        return false;
    }

    /**
     * Produces a platform-aware list of default directories that commonly contain JDK installations,
     * augmented with user-level managers such as SDKMAN and Gradle caches.
     *
     * @return candidate directories to probe for JDKs
     */
    private static List<Path> getPossibleJDKPaths() {
        Set<Path> candidates = new LinkedHashSet<>();

        switch (OperatingSystem.CURRENT) {
            case WINDOWS -> {
                for (String basePath : WIN_JDK_PATHS) {
                    for (char drive = 'C'; drive <= 'Z'; drive++) { // Start from C, A/B are usually floppy
                        Path driveRoot = Path.of(drive + ":\\");
                        if (Files.exists(driveRoot)) {
                            String path = basePath.replace("{drive}", String.valueOf(drive));
                            candidates.add(FileUtils.normalizePath(Path.of(path)));
                        }
                    }
                }
            }
            case MAC -> {
                // System-wide JDK bundles
                candidates.add(FileUtils.normalizePath(Path.of("/Library/Java/JavaVirtualMachines")));

                // User-scoped JDK bundles (JetBrains downloader often ends up here)
                String home = System.getProperty("user.home");
                if (home != null && !home.isBlank()) {
                    candidates.add(FileUtils.normalizePath(Path.of(home, "Library", "Java", "JavaVirtualMachines")));
                }

                // Homebrew (Apple Silicon + Intel)
                candidates.add(FileUtils.normalizePath(Path.of("/opt/homebrew/opt")));   // ARM
                candidates.add(FileUtils.normalizePath(Path.of("/usr/local/opt")));      // Intel
            }
            case LINUX -> LINUX_JDK_PATHS.forEach(path -> candidates.add(FileUtils.normalizePath(Path.of(path))));
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            candidates.add(FileUtils.normalizePath(Path.of(userHome, ".sdkman", "candidates", "java")));
            candidates.add(FileUtils.normalizePath(Path.of(userHome, ".asdf", "installs", "java")));
            candidates.add(FileUtils.normalizePath(Path.of(userHome, ".jdks")));
            candidates.add(FileUtils.normalizePath(Path.of(userHome, ".gradle", "jdks")));
        }

        String gradleUserHome = System.getenv("GRADLE_USER_HOME");
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            candidates.add(FileUtils.normalizePath(Path.of(gradleUserHome, "jdks")));
        }

        for (Path path : FileUtils.normalizePaths(Settings.ADDITIONAL_JDK_SCAN_PATHS.getOrDefaultValue())) {
            candidates.add(FileUtils.normalizePath(path));
        }

        return new ArrayList<>(candidates);
    }

    /**
     * Resolves the platform-specific filename for a Java executable.
     *
     * @return {@code java.exe} on Windows; otherwise {@code java}
     */
    private static String javaExecutableName() {
        return OperatingSystem.CURRENT == OperatingSystem.WINDOWS ? "java.exe" : "java";
    }
}
