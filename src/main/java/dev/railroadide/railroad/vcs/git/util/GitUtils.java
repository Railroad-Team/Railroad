package dev.railroadide.railroad.vcs.git.util;

import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility methods for loading and initializing git-related IDE settings.
 */
public final class GitUtils {
    private GitUtils() {
    }

    /**
     * Loads and persists a detected git executable path into settings when unset.
     */
    public static void loadGitExecutableIntoSettings() {
        Optional<Path> optionalPath = Settings.GIT_EXECUTABLE_PATH.getOptional();
        if (optionalPath.isEmpty()) {
            GitLocator.findGitExecutable().ifPresent(value -> {
                Settings.GIT_EXECUTABLE_PATH.setValue(value);
                SettingsHandler.saveSettings();
            });
        }
    }
}
