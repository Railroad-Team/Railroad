package dev.railroadide.railroad;

import javafx.application.Application;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class RailroadLauncher {
    private static final String SLF4J_PROVIDER_PROPERTY = "slf4j.provider";
    private static final String LOGBACK_PROVIDER = "ch.qos.logback.classic.spi.LogbackServiceProvider";

    private RailroadLauncher() {
    }

    public static void main(String[] args) {
        launchWithPreloader(args);
    }

    public static void launchWithPreloader(String[] args) {
        configureLinuxAwt();
        bindApplicationLogging();

        String preloader = System.getProperty("javafx.preloader");
        if (preloader == null || preloader.isBlank()) {
            System.setProperty("javafx.preloader", RailroadPreloader.class.getName());
        }
        Application.launch(Railroad.class, args);
    }

    private static void bindApplicationLogging() {
        System.setProperty(SLF4J_PROVIDER_PROPERTY, LOGBACK_PROVIDER);
        try {
            LoggerFactory.getILoggerFactory();
        } finally {
            System.clearProperty(SLF4J_PROVIDER_PROPERTY);
        }
    }

    private static void configureLinuxAwt() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("linux") && System.getProperty("java.awt.headless") == null) {
            // Keeping AWT headless prevents the X11 toolkit from replacing the process-wide X error handler while GTK
            // has an error trap active.
            System.setProperty("java.awt.headless", "true");
        }
    }
}
