package dev.railroadide.railroad;

import javafx.application.Application;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RailroadLauncher {
    private static final String CEFFX_RUNTIME_PROPERTY = "railroad.ceffx.runtime";

    private RailroadLauncher() {
    }

    public static void main(String[] args) {
        launchWithPreloader(args);
    }

    public static void launchWithPreloader(String[] args) {
        configureCeffxRuntime();
        String preloader = System.getProperty("javafx.preloader");
        if (preloader == null || preloader.isBlank()) {
            System.setProperty("javafx.preloader", RailroadPreloader.class.getName());
        }
        Application.launch(Railroad.class, args);
    }

    private static void configureCeffxRuntime() {
        if (System.getProperty(CEFFX_RUNTIME_PROPERTY) != null
                && !System.getProperty(CEFFX_RUNTIME_PROPERTY).isBlank()) {
            return;
        }

        Path runtimePath = locateCeffxRuntime();
        if (runtimePath != null) {
            System.setProperty(CEFFX_RUNTIME_PROPERTY, runtimePath.toString());
        }
    }

    private static Path locateCeffxRuntime() {
        Path[] roots = new Path[] {
            Path.of("").toAbsolutePath().normalize(),
            getLauncherRoot()
        };

        for (Path root : roots) {
            if (root == null) {
                continue;
            }

            Path current = root;
            while (current != null) {
                Path bundled = current.resolve(Path.of(".railroad", "ceffx", "win"));
                if (isRuntimeDirectory(bundled)) {
                    return bundled;
                }

                Path flat = current.resolve("ceffx");
                if (isRuntimeDirectory(flat)) {
                    return flat;
                }

                current = current.getParent();
            }
        }

        return null;
    }

    private static Path getLauncherRoot() {
        try {
            Path location = Path.of(RailroadLauncher.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            return Files.isDirectory(location) ? location : location.getParent();
        } catch (URISyntaxException | SecurityException exception) {
            return null;
        }
    }

    private static boolean isRuntimeDirectory(Path path) {
        return Files.exists(path.resolve("ceffx.dll"))
            && Files.exists(path.resolve("ceffx_helper.exe"))
            && Files.exists(path.resolve("libcef.dll"))
            && Files.exists(path.resolve("icudtl.dat"))
            && Files.exists(path.resolve("resources.pak"));
    }
}
