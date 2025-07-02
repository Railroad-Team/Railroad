package io.github.railroad.logging;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Move to separate library (https://github.com/Railroad-Team/RailroadLogger)
// TODO: Add support for colouring the log output
// TODO: Add support for customizing the log format
// TODO: Add support for time based log deletion (e.g., delete logs older than 7 days) - should be configurable
// TODO: Add support for customizing the log directory and file names (useful for plugins)
// TODO: Add support for logging to multiple files (e.g., latest.log and pluginName.log)
// TODO: Add support for logging to a remote server (?)
// TODO: Add support for configuring the write frequency (e.g., write every 5 seconds instead of 1 second)
// TODO: Add support for disabling log compression (e.g., for debugging purposes)
// TODO: Add support for uploading a log file to a remote server (e.g., for bug reports)
public class Logger {
    private final String name;

    private static final String BRACE_REGEX = "(?<!\\\\)\\{}";
    private static final DateTimeFormatter LOGGING_DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Path LOG_DIRECTORY = ConfigHandler.getConfigDirectory().resolve("logs");
    private static final Path LATEST_LOG = LOG_DIRECTORY.resolve("latest.log");

    private static final List<String> LOGGING_MESSAGES = new CopyOnWriteArrayList<>();

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    public Logger(String name) {
        this.name = name;
    }

    public Logger(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public static void initialise() {
        try {
            Files.createDirectories(LOG_DIRECTORY);
            if (Files.exists(LATEST_LOG)) {
                FileTime dateCreated = Files.readAttributes(LATEST_LOG, BasicFileAttributes.class).creationTime();
                Path archivedLogPath = LOG_DIRECTORY.resolve(formatFileTime(dateCreated) + ".log");

                // We copy the file instead of moving, and then set the creation time manually so that we can bypass
                // window's file-system tunneling
                Files.copy(LATEST_LOG, archivedLogPath, StandardCopyOption.REPLACE_EXISTING);
                Files.setAttribute(LATEST_LOG, "creationTime", FileTime.from(Instant.now()));
                Files.write(LATEST_LOG, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);

                compress(archivedLogPath);
                Files.deleteIfExists(archivedLogPath);
            }

            writeLog();
        } catch (IOException exception) {
            throw new RuntimeException("Failed to initialize logging", exception);
        }
    }

    public void error(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.ERROR, objects);
    }

    public void warn(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.WARN, objects);
    }

    public void info(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.INFO, objects);
    }

    public void debug(String logMessage, Object... objects) {
        log(logMessage, LoggingLevel.DEBUG, objects);
    }

    public void log(String message, LoggingLevel level, Object... objects) {
        if(message == null || message.isEmpty())
            return;

        long bracesCount = Pattern.compile(BRACE_REGEX).matcher(message).results().count();

        List<Throwable> throwables = new ArrayList<>();
        for (int i = 0; i < objects.length; i++) {
            Object object = objects[i];
            // We check if the last object is a throwable and skip replacement if so.
            // This is to allow for cases such as: Railroad.LOGGER.error("Failed to compress log file {}", exception, exception);
            if (object instanceof Throwable throwable && i >= bracesCount) {
                throwables.add(throwable);
                continue;
            }

            message = message.replaceFirst(BRACE_REGEX, Matcher.quoteReplacement(Objects.toString(object)));
        }

        var messageBuilder = new StringBuilder(getPaddedTime() + " [" + Thread.currentThread().getName() + "] " + level.name() + " " + this.name + " - " + message);
        for (Throwable throwable : throwables) {
            var stringWriter = new StringWriter();
            try (var printWriter = new PrintWriter(stringWriter)) {
                throwable.printStackTrace(printWriter);
            }

            String fullTrace = stringWriter.toString();
            messageBuilder.append("\n").append(fullTrace);
        }

        message = messageBuilder.toString();
        System.out.println(message);

        LOGGING_MESSAGES.add(message);
    }

    private static String getPaddedTime() {
        return LOGGING_DATE_FORMAT.format(LocalTime.now());
    }

    private static String formatFileTime(FileTime fileTime) {
        ZonedDateTime t = Instant.ofEpochMilli(fileTime.toMillis()).atZone(ZoneId.systemDefault());
        return LOG_DATE_FORMAT.format(t);
    }

    private static void compress(Path path) {
        Path outputPath = path.resolveSibling(path.getFileName().toString() + ".tar.gz");
        try(var inputChannel = FileChannel.open(path, StandardOpenOption.READ);
            var outChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            InputStream inputStream = Channels.newInputStream(inputChannel);
            OutputStream outputStream = Channels.newOutputStream(outChannel);
            var bufferedOutputStream = new BufferedOutputStream(outputStream);
            var gzipOutputStream = new GzipCompressorOutputStream(bufferedOutputStream)) {
            byte[] buf = new byte[8 * 1024];
            int length;
            while((length = inputStream.read(buf)) != -1) {
                gzipOutputStream.write(buf, 0, length);
            }
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to compress log file", exception);
        }
    }

    private static void writeLog() {
        SCHEDULER.scheduleAtFixedRate(() -> {
            if(LOGGING_MESSAGES.isEmpty())
                return;

            try {
                var logText = String.join("\n", LOGGING_MESSAGES);
                LOGGING_MESSAGES.clear();
                Files.writeString(LATEST_LOG, logText, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            } catch (IOException exception) {
                System.exit(-1);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }
}