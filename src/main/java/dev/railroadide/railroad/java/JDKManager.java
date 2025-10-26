package dev.railroadide.railroad.java;

import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.railroad.utility.JavaVersion;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JDKManager {

    private static final List<String> WIN_JDK_PATHS = List.of(
        "C:\\Program Files\\Java",
        "C:\\Program Files (x86)\\Java",
        "C:\\Program Files\\Eclipse Adoptium"
    );
    private static final List<String> MAC_JDK_PATHS = List.of(
        "/Library/Java/JavaVirtualMachines"
    );
    private static final List<String> LINUX_JDK_PATHS = List.of(
        "/usr/lib/jvm",
        "/usr/java"
    );
    private static final String JAVA_EXECUTABLE = getJavaExecutable();

    private static List<JDK> jdks;

    public static void init() {
        jdks = discoverJDKs();
    }

    public static List<JDK> getAvailableJDKs() {
        return jdks;
    }

    public static List<JDK> getJDKsInVersionRange(JavaVersion minVersion, JavaVersion maxVersion) {
        List<JDK> filtered = new ArrayList<>();
        for (JDK jdk : jdks) {
            if ((minVersion == null || jdk.version().compareTo(minVersion) >= 0) &&
                (maxVersion == null || jdk.version().compareTo(maxVersion) <= 0)) {
                filtered.add(jdk);
            }
        }
        return filtered;
    }

    private static List<JDK> discoverJDKs() {
        // Location 1: JAVA_HOME environment variable
        List<JDK> jdks = new ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isEmpty()) {
            JDK jdk = createJDKFromJavaExecutable(javaHome);
            if (jdk != null) jdks.add(jdk);
        }

        // Location 2: System PATH
        String javaPath = findJavaOnPath();
        if (javaPath != null) {
            JDK jdk = createJDKFromJavaExecutable(javaPath);
            if (jdk != null && jdks.stream().noneMatch(existing -> existing.path().equals(jdk.path()))) {
                jdks.add(jdk);
            }
        }

        // Location 3: Common installation directories
        for (Path dir : getPossibleJDKPaths()) {
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            JDK jdk = createJDKFromJavaExecutable(entry + "/bin/" + JAVA_EXECUTABLE);
                            if (jdk != null) jdks.add(jdk);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Map<String, JDK> uniqueJDKs = new LinkedHashMap<>();
        for (JDK jdk : jdks) {
            try {
                String normalizedPath = Paths.get(jdk.path()).toRealPath().toString();
                uniqueJDKs.putIfAbsent(normalizedPath, new JDK(normalizedPath, jdk.name(), jdk.version()));
            } catch (IOException e) {
                // fallback to raw path if normalization fails
                uniqueJDKs.putIfAbsent(jdk.path(), jdk);
            }
        }

        return new ArrayList<>(uniqueJDKs.values());
    }


    private static JavaVersion getJavaVersion(String javaHomeOrPath) {
        String javaExecutable = javaHomeOrPath;
        File f = new File(javaHomeOrPath, "bin/" + JAVA_EXECUTABLE);
        if (f.exists() && f.canExecute()) {
            javaExecutable = f.getAbsolutePath();
        }

        try {
            Process p = new ProcessBuilder(javaExecutable, "-version")
                .redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                Pattern pattern = Pattern.compile("\"(\\d+)\\.(\\d+).*\"");
                while ((line = br.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        int major = Integer.parseInt(matcher.group(1));
                        int minor = Integer.parseInt(matcher.group(2));
                        return new JavaVersion(major, minor);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }
        return null;
    }

    private static JDK createJDKFromJavaExecutable(String javaExecutablePath) {
        File javaExe = new File(javaExecutablePath);
        if (!javaExe.exists()) return null;

        // Get home directory: parent of bin
        File binDir = javaExe.getParentFile();
        if (binDir == null) return null;
        File javaHomeDir = binDir.getParentFile();
        if (javaHomeDir == null) return null;

        String javaHome = javaHomeDir.getAbsolutePath();
        JavaVersion version = getJavaVersion(javaHome);
        if (version != null) {
            String name = javaHomeDir.getName(); // use folder name
            return new JDK(javaHome, name, version);
        }
        return null;
    }

    private static String findJavaOnPath() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        String[] paths = pathEnv.split(File.pathSeparator);
        for (String p : paths) {
            File javaFile = new File(p, JAVA_EXECUTABLE);
            if (javaFile.exists() && javaFile.canExecute()) return javaFile.getAbsolutePath();
        }
        return null;
    }

    private static List<Path> getPossibleJDKPaths() {
        List<String> paths = switch (OperatingSystem.CURRENT) {
            case WINDOWS -> WIN_JDK_PATHS;
            case MAC -> MAC_JDK_PATHS;
            case LINUX -> LINUX_JDK_PATHS;
            case UNKNOWN -> List.of();
        };
        return paths.stream().map(Paths::get).toList();
    }

    private static String getJavaExecutable() {
        return OperatingSystem.CURRENT == OperatingSystem.WINDOWS ? "java.exe" : "java";
    }
}
