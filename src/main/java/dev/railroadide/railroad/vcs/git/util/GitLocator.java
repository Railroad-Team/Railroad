package dev.railroadide.railroad.vcs.git.util;

import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.utility.CommandUtils;
import dev.railroadide.railroad.utility.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates a usable git executable from PATH or common install locations.
 */
public class GitLocator {
    /**
     * Finds a usable git executable path.
     *
     * @return executable path when found
     */
    public static Optional<Path> findGitExecutable() {
        Long timeoutMs = Settings.GIT_BINARY_SEARCH_COMMAND_TIMEOUT_MS.getValue();
        if (timeoutMs == null) {
            Railroad.LOGGER.error("Git binary search timeout setting is null, using default of 5000ms");
            timeoutMs = 5000L;
        }

        if (CommandUtils.canRunCommand(timeoutMs, "git", "--version")) {
            Optional<Path> resolved = CommandUtils.findPathOfExecutable(timeoutMs, "git");
            if (resolved.isPresent())
                return resolved;
        }

        return resolveFromCommonLocations();
    }

    private static Optional<Path> resolveFromCommonLocations() {
        List<Path> paths = candidatePaths();
        for (Path path : paths) {
            if (path != null && Files.exists(path) && Files.isRegularFile(path) && Files.isExecutable(path))
                return Optional.of(path);
        }

        return Optional.empty();
    }

    private static List<Path> candidatePaths() {
        List<Path> paths = new ArrayList<>();

        if (OperatingSystem.isWindows()) {
            String[] basePaths = new String[]{
                "{drive}:\\Program Files\\Git\\bin\\git.exe",
                "{drive}:\\Program Files\\Git\\cmd\\git.exe",
                "{drive}:\\Program Files (x86)\\Git\\bin\\git.exe",
                "{drive}:\\Program Files (x86)\\Git\\cmd\\git.exe",
                "{drive}:\\ProgramData\\chocolatey\\bin\\git.exe"
            };

            for (char drive = 'C'; drive <= 'Z'; drive++) { // Start from C, A/B are usually floppy
                Path driveRoot = Path.of(drive + ":\\");
                if (Files.exists(driveRoot)) {
                    for (String basePath : basePaths) {
                        String path = basePath.replace("{drive}", String.valueOf(drive));
                        paths.add(FileUtils.normalizePath(Path.of(path)));
                    }
                }
            }

            String userHome = System.getProperty("user.home");
            paths.add(Path.of(userHome, "scoop", "apps", "git", "current", "bin", "git.exe"));
            paths.add(Path.of(userHome, "scoop", "apps", "git", "current", "cmd", "git.exe"));
        } else {
            paths.add(Path.of("/usr/bin/git"));
            paths.add(Path.of("/usr/local/bin/git"));
            paths.add(Path.of("/opt/homebrew/bin/git"));
            paths.add(Path.of("/snap/bin/git"));
        }

        return paths;
    }
}
