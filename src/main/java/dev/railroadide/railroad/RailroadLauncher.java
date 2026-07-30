package dev.railroadide.railroad;

import javafx.application.Application;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RailroadLauncher {
    private static final String CEFFX_RUNTIME_PROPERTY = "railroad.ceffx.runtime";
    private static final String CEFFX_PLATFORM = getCeffxPlatform();

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
                Path bundled = current.resolve(Path.of(".railroad", "ceffx", CEFFX_PLATFORM));
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
        if (CEFFX_PLATFORM.startsWith("mac")) {
            return Files.exists(path.resolve("libceffx.dylib"))
                && Files.exists(path.resolve("ceffx Helper.app"))
                && Files.exists(path.resolve("Chromium Embedded Framework.framework"));
        }

        if (CEFFX_PLATFORM.equals("linux")) {
            return Files.exists(path.resolve("libceffx.so"))
                && Files.exists(path.resolve("ceffx_helper"))
                && Files.exists(path.resolve("libcef.so"))
                && Files.exists(path.resolve("icudtl.dat"))
                && Files.exists(path.resolve("resources.pak"));
        }

        return Files.exists(path.resolve("ceffx.dll"))
            && Files.exists(path.resolve("ceffx_helper.exe"))
            && Files.exists(path.resolve("libcef.dll"))
            && Files.exists(path.resolve("icudtl.dat"))
            && Files.exists(path.resolve("resources.pak"));
    }

    private static String getCeffxPlatform() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac")) {
            return System.getProperty("os.arch", "").equalsIgnoreCase("aarch64")
                ? "mac-aarch64"
                : "mac";
        }
        if (osName.contains("win")) {
            return "win";
        }
        return "linux";
    }
}
