package dev.railroadide.railroad.browser.impl;

import com.techsenger.ceffx.core.CefApp;
import com.techsenger.ceffx.core.CefSettings;
import com.techsenger.ceffx.core.SystemBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

public final class CeffxManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CeffxManager.class);
    private static final String RUNTIME_PATH_PROPERTY = "railroad.ceffx.runtime";
    private static final String CEFFX_PLATFORM = getCeffxPlatform();
    private static CefApp cefApp;

    private CeffxManager() {
    }

    public static synchronized CefApp getCefApp() {
        if (cefApp == null) {
            Path runtimePath = getRuntimePath();
            LOGGER.info("Using CEFFX runtime at {}", runtimePath);
            configureNativeLoader(runtimePath);
            CefApp.startup(createStartupArgs(runtimePath));
            cefApp = CefApp.getInstance(createSettings(runtimePath));
        }

        return cefApp;
    }

    private static CefSettings createSettings(Path runtimePath) {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.multi_threaded_message_loop = true;
        settings.external_message_pump = false;
        settings.command_line_args_disabled = false;
        if (CEFFX_PLATFORM.startsWith("mac")) {
            settings.browser_subprocess_path = runtimePath
                .resolve(Path.of("ceffx Helper.app", "Contents", "MacOS", "ceffx Helper"))
                .toString();
        }
        return settings;
    }

    private static String[] createStartupArgs(Path runtimePath) {
        if (CEFFX_PLATFORM.startsWith("mac")) {
            return new String[] {
                "--framework-dir-path="
                    + runtimePath.resolve("Chromium Embedded Framework.framework")
            };
        }
        return new String[0];
    }

    private static Path getRuntimePath() {
        String configuredPath = System.getProperty(RUNTIME_PATH_PROPERTY);
        Path runtimePath = configuredPath == null || configuredPath.isBlank()
            ? findBundledRuntimePath()
            : Path.of(configuredPath).toAbsolutePath().normalize();

        if (!isRuntimePath(runtimePath)) {
            throw new IllegalStateException("CEFFX runtime not found at " + runtimePath
                + ". Set -D" + RUNTIME_PATH_PROPERTY + " to a complete " + CEFFX_PLATFORM
                + " CEFFX runtime directory.");
        }

        System.setProperty("java.library.path", runtimePath.toString());
        return runtimePath;
    }

    private static Path findBundledRuntimePath() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(Path.of(".railroad", "ceffx", CEFFX_PLATFORM));
            if (isRuntimePath(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        return Path.of(".railroad", "ceffx", CEFFX_PLATFORM).toAbsolutePath().normalize();
    }

    private static boolean isRuntimePath(Path path) {
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

    private static void configureNativeLoader(Path runtimePath) {
        SystemBootstrap.setLoader(libraryName -> {
            Path library = runtimePath.resolve(System.mapLibraryName(libraryName));
            if (!Files.exists(library)) {
                library = runtimePath.resolve(libraryName + ".dll");
            }
            System.load(library.toString());
        });
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
