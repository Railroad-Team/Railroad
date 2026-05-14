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
    private static CefApp cefApp;

    private CeffxManager() {
    }

    public static synchronized CefApp getCefApp() {
        if (cefApp == null) {
            Path runtimePath = getRuntimePath();
            LOGGER.info("Using CEFFX runtime at {}", runtimePath);
            configureNativeLoader(runtimePath);
            CefApp.startup(new String[0]);
            cefApp = CefApp.getInstance(createSettings());
        }

        return cefApp;
    }

    private static CefSettings createSettings() {
        CefSettings settings = new CefSettings();
        settings.windowless_rendering_enabled = true;
        settings.multi_threaded_message_loop = true;
        settings.external_message_pump = false;
        settings.command_line_args_disabled = false;
        return settings;
    }

    private static Path getRuntimePath() {
        String configuredPath = System.getProperty(RUNTIME_PATH_PROPERTY);
        Path runtimePath = configuredPath == null || configuredPath.isBlank()
            ? findBundledRuntimePath()
            : Path.of(configuredPath).toAbsolutePath().normalize();

        if (!isRuntimePath(runtimePath)) {
            throw new IllegalStateException("CEFFX runtime not found at " + runtimePath
                + ". Set -D" + RUNTIME_PATH_PROPERTY + " to a directory containing ceffx.dll, "
                + "ceffx_helper.exe, libcef.dll, and CEF resources.");
        }

        System.setProperty("java.library.path", runtimePath.toString());
        return runtimePath;
    }

    private static Path findBundledRuntimePath() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(Path.of(".railroad", "ceffx", "win"));
            if (isRuntimePath(candidate)) {
                return candidate;
            }

            current = current.getParent();
        }

        return Path.of(".railroad", "ceffx", "win").toAbsolutePath().normalize();
    }

    private static boolean isRuntimePath(Path path) {
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
}
