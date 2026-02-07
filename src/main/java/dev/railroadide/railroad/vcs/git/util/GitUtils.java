package dev.railroadide.railroad.vcs.git.util;

import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;

import java.nio.file.Path;
import java.util.Optional;

public final class GitUtils {
    private GitUtils() {
    }

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
