package dev.railroadide.railroad.vcs.git;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.FileAppender;
import dev.railroadide.railroad.config.ConfigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Routes Git subsystem logging to its own file without console propagation.
 */
public final class GitLog {
    private static final String LOGGER_NAME = "dev.railroadide.railroad.vcs.git";
    private static final String APPENDER_NAME = "GIT_FILE";

    /** Shared Git subsystem logger, configured to append to {@code logs/git.log} when Logback is active. */
    public static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    static {
        configure();
    }

    private GitLog() {
    }

    /**
     * Triggers the class's one-time logger configuration during application startup.
     * With Logback, Git logging goes to {@code logs/git.log} in the configuration directory without
     * propagating to parent loggers. Repeated calls do not reconfigure the logger.
     */
    public static void initialize() {
        // Triggers static logger configuration during application startup.
    }

    private static void configure() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context))
            return;

        var gitLogger = context.getLogger(LOGGER_NAME);
        if (gitLogger.getAppender(APPENDER_NAME) == null) {
            gitLogger.addAppender(createFileAppender(context));
        }
        gitLogger.setLevel(Level.DEBUG);
        gitLogger.setAdditive(false);

        var jgitLogger = context.getLogger("org.eclipse.jgit");
        if (jgitLogger.getAppender(APPENDER_NAME) == null) {
            jgitLogger.addAppender(gitLogger.getAppender(APPENDER_NAME));
        }
        jgitLogger.setLevel(Level.WARN);
        jgitLogger.setAdditive(false);
    }

    private static FileAppender<ILoggingEvent> createFileAppender(LoggerContext context) {
        var encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        var appender = new FileAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.setName(APPENDER_NAME);
        appender.setFile(ConfigHandler.getConfigDirectory().resolve("logs").resolve("git.log").toString());
        appender.setAppend(true);
        appender.setEncoder(encoder);
        appender.start();
        return appender;
    }
}
