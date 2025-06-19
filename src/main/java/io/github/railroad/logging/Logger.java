package io.github.railroad.logging;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.regex.Matcher;

// TODO: Ability to define a logger to a class instead of a name
// TODO: Allow for additional argument (java.lang.Throwable) that will put the throwable in the log too
// TODO: Consider creating a log event stream, so that we can batch write to the log file
public class Logger {
    private final String name;

    private static final DateTimeFormatter LOGGING_DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter LOG_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
    private static final Path LOG_DIRECTORY = ConfigHandler.getConfigDirectory().resolve("logs");
    private static final Path LATEST_LOG = LOG_DIRECTORY.resolve("latest.log");

    public Logger(String name) {
        this.name = name;
    }

    public static void initialise() {
        try {
            Files.createDirectories(LOG_DIRECTORY);
            if (Files.notExists(LATEST_LOG))
                return;

            FileTime dateCreated = Files.readAttributes(LATEST_LOG, BasicFileAttributes.class).creationTime();
            Path archivedLogPath = LOG_DIRECTORY.resolve(formatFileTime(dateCreated) + ".log");

            // We copy the file instead of moving, and then set the creation time manually so that we can bypass
            // window's file-system tunneling
            Files.copy(LATEST_LOG, archivedLogPath, StandardCopyOption.REPLACE_EXISTING);
            Files.setAttribute(LATEST_LOG, "creationTime", FileTime.from(Instant.now()));
            Files.write(LATEST_LOG, new byte[0], StandardOpenOption.WRITE);

            compress(archivedLogPath);
            Files.deleteIfExists(archivedLogPath);
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
        for (Object object : objects) {
            message = message.replaceFirst("(?<!\\\\)\\{\\}", Matcher.quoteReplacement(object.toString()));
        }

        message = getPaddedTime() + " [" + Thread.currentThread().getName() + "] " + level.name() + " " + this.name + " - " + message;

        System.out.println(message);

        try {
            Files.writeString(LATEST_LOG, message + "\n", StandardOpenOption.APPEND, StandardOpenOption.CREATE);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to log message", exception);
        }
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
}