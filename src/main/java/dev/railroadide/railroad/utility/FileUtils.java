package dev.railroadide.railroad.utility;

import javafx.scene.Node;
import org.kordamp.ikonli.fontawesome6.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtils {
    private FileUtils() {
        // Utility class, no instantiation
    }

    /**
     * Gets the file extension from a given path.
     *
     * @param path the path to the file
     * @return the file extension, or null if there is no extension
     */
    public static String getExtension(Path path) {
        return getExtension(path.toString());
    }

    /**
     * Gets the file extension from a given path as a string.
     *
     * @param path the path to the file as a string
     * @return the file extension, or null if there is no extension
     */
    public static String getExtension(String path) {
        int lastDotIndex = path.lastIndexOf('.');
        return lastDotIndex > 0 ? path.substring(lastDotIndex + 1) : null;
    }

    /**
     * Copies the content of a URL to a file.
     *
     * @param url  the URL to copy from
     * @param path the path to the file to copy to
     * @throws RuntimeException if an error occurs during copying
     */
    public static void copyUrlToFile(String url, Path path) throws RuntimeException {
        try (InputStream in = new URI(url).toURL().openStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException("Failed to copy URL to file", exception);
        }
    }

    /**
     * Updates a key-value pair in a properties file.
     *
     * @param key   the key to update
     * @param value the new value for the key
     * @param file  the properties file to update
     * @throws IOException if an error occurs during file operations
     */
    public static void updateKeyValuePair(String key, String value, Path file) throws IOException {
        var stringBuilder = new StringBuilder();

        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            if (line.startsWith(key + "=")) {
                line = key + "=" + value;
            }

            stringBuilder.append(line).append("\n");
        }

        Files.writeString(file, stringBuilder.toString());
    }

    /**
     * Unzips a ZIP file to a specified directory.
     *
     * @param fileZip the path to the ZIP file
     * @param dstDir  the destination directory where the contents will be extracted
     * @throws IOException if an error occurs during unzipping
     */
    public static void unzipFile(Path fileZip, Path dstDir) throws IOException {
        try (var zipInputStream = new ZipInputStream(Files.newInputStream(fileZip))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                Path newFile = resolveZipEntryPath(dstDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFile);
                    if (!Files.isDirectory(newFile))
                        throw new IOException("Failed to create directory " + newFile);
                } else {
                    Path parent = newFile.getParent();
                    Files.createDirectories(parent);
                    if (!Files.isDirectory(parent))
                        throw new IOException("Failed to create directory " + parent);

                    Files.copy(zipInputStream, newFile, StandardCopyOption.REPLACE_EXISTING);
                }

                zipEntry = zipInputStream.getNextEntry();
            }

            zipInputStream.closeEntry();
        }
    }

    /**
     * Resolves the path for a ZIP entry to ensure it does not escape the destination directory.
     *
     * @param destinationDir the destination directory
     * @param zipEntry       the ZIP entry to resolve
     * @return the resolved path for the ZIP entry
     * @throws IOException if the resolved path is outside the destination directory
     */
    public static Path resolveZipEntryPath(Path destinationDir, ZipEntry zipEntry) throws IOException {
        var destFile = Path.of(destinationDir.toString(), zipEntry.getName());

        if (!destFile.normalize().startsWith(destinationDir))
            throw new IOException("Bad zip entry: " + zipEntry.getName());

        return destFile;
    }

    /**
     * Copies a folder from source to destination.
     *
     * @param src the source folder path
     * @param dst the destination folder path
     * @throws RuntimeException if an error occurs during copying
     */
    public static void copyFolder(Path src, Path dst) throws RuntimeException {
        try (Stream<Path> files = Files.walk(src)) {
            files.forEach(source -> {
                try {
                    Path destination = dst.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to copy folder", exception);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException("Failed to copy folder", exception);
        }
    }

    /**
     * Deletes a folder and all its contents.
     *
     * @param folder the folder to delete
     * @throws RuntimeException if an error occurs during deletion
     */
    public static void deleteFolder(Path folder) throws RuntimeException {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to delete folder", exception);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException("Failed to delete folder", exception);
        }
    }

    /**
     * Checks if a directory is empty, including its subdirectories.
     *
     * @param directory  the directory to check
     * @param onEmpty    action to perform if the directory is empty
     * @param onNotEmpty action to perform if the directory is not empty
     * @return true if the directory is empty, false otherwise
     */
    public static boolean isDirectoryEmpty(Path directory, Runnable onEmpty, Runnable onNotEmpty) {
        try {
            if (Files.notExists(directory)) {
                if (directory.toFile().mkdirs()) { // We use IO instead of NIO so that we block the thread until it's created
                    onEmpty.run();
                    return true;
                }

                onNotEmpty.run();
                return false;
            }
            try (Stream<Path> paths = Files.list(directory)) {
                List<Path> pathList = paths.toList();
                if (pathList.isEmpty()) {
                    onEmpty.run();
                    return true;
                } else {
                    for (Path path : pathList) {
                        if (Files.isDirectory(path)) {
                            if (!isDirectoryEmpty(path)) {
                                onNotEmpty.run();
                                return false;
                            }
                        } else {
                            onNotEmpty.run();
                            return false;
                        }
                    }
                }
            }

            onEmpty.run();
            return true;
        } catch (IOException exception) {
            return false; // If we can't read the directory, we assume it's not empty
        }
    }

    /**
     * Checks if a directory is empty, including its subdirectories.
     *
     * @param directory the directory to check
     * @param onEmpty   action to perform if the directory is empty
     * @return true if the directory is empty, false otherwise
     */
    public static boolean isDirectoryEmpty(Path directory, Runnable onEmpty) {
        return isDirectoryEmpty(directory, onEmpty, () -> {
        });
    }

    /**
     * Checks if a directory is empty, including its subdirectories.
     *
     * @param directory the directory to check
     * @return true if the directory is empty, false otherwise
     */
    public static boolean isDirectoryEmpty(Path directory) {
        return isDirectoryEmpty(directory, () -> {
        });
    }

    /**
     * Gets an icon representing the file type based on its path.
     *
     * @param path the path to the file or directory
     * @return a Node representing the icon for the file type
     */
    public static Node getIcon(Path path) {
        if (Files.isDirectory(path))
            return new FontIcon(FontAwesomeRegular.FOLDER);

        String extension = getExtension(path.toString());
        if (extension == null)
            return new FontIcon(FontAwesomeRegular.FILE);

        return switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> new FontIcon(FontAwesomeRegular.FILE_IMAGE);
            case "mp4", "webm", "avi", "mov", "flv", "wmv", "mkv" -> new FontIcon(FontAwesomeRegular.FILE_VIDEO);
            case "mp3", "wav", "flac", "ogg", "m4a", "wma", "aac" -> new FontIcon(FontAwesomeRegular.FILE_AUDIO);
            case "zip", "rar", "7z", "tar", "gz", "xz", "bz2" -> new FontIcon(FontAwesomeRegular.FILE_ARCHIVE);
            case "csv", "tsv", "xls", "xlsx", "ods", "dbf", "sql", "json", "xml", "yaml", "yml" ->
                    new FontIcon(FontAwesomeRegular.FILE_EXCEL);
            case "pdf" -> new FontIcon(FontAwesomeRegular.FILE_PDF);
            case "doc", "docx", "odt", "rtf", "txt", "md" -> new FontIcon(FontAwesomeRegular.FILE_WORD);
            case "ppt", "pptx", "odp" -> new FontIcon(FontAwesomeRegular.FILE_POWERPOINT);
            case "html", "htm", "css", "js", "ts", "java", "py", "c", "cpp", "h", "hpp", "cs", "php", "rb", "go", "rs",
                 "kt", "swift", "dart", "groovy", "gradle", "kts", "sh", "bat", "cmd", "ps1" ->
                    new FontIcon(FontAwesomeRegular.FILE_CODE);
            default -> new FontIcon(FontAwesomeRegular.FILE_ALT);
        };
    }

    /**
     * Checks if a file is a binary file.
     *
     * @param path the path to the file
     * @return true if the file is binary, false otherwise
     * @throws RuntimeException if an error occurs during file operations
     */
    public static boolean isBinaryFile(Path path) throws RuntimeException {
        try (var stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024];
            int read = stream.read(buffer);
            for (int i = 0; i < read; i++) {
                if (buffer[i] == 0) {
                    return true;
                }
            }

            return false;
        } catch (IOException exception) {
            throw new RuntimeException("Failed to check if file is binary", exception);
        }
    }

    /**
     * Checks if a file is an image file based on its extension.
     *
     * @param path the path to the file
     * @return true if the file is an image, false otherwise
     */
    public static boolean isImageFile(Path path) {
        String extension = getExtension(path.toString());
        return extension != null && switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> true;
            default -> false;
        };
    }

    /**
     * Opens a file or directory in the system's file explorer.
     *
     * @param path the path to the file or directory
     * @throws RuntimeException if an error occurs while opening the file or directory
     */
    public static void openInExplorer(Path path) throws RuntimeException {
        openInDefaultApplication(Files.isDirectory(path) ? path : path.getParent());
    }

    /**
     * Opens a file or directory in the default application associated with its type.
     *
     * @param path the path to the file or directory
     * @throws RuntimeException if an error occurs while opening the file or directory
     */
    public static void openInDefaultApplication(Path path) throws RuntimeException {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException exception) {
                throw new RuntimeException("Failed to open in default application", exception);
            }
        }
    }

    /**
     * Gets the size of a file or directory.
     *
     * @param path the path to the file or directory
     * @return the size in bytes
     * @throws IOException if an error occurs while reading the file or directory
     */
    public static long getSize(Path path) throws IOException {
        return Files.size(path);
    }

    /**
     * Gets a human-readable representation of the size of a file or directory.
     *
     * @param path the path to the file or directory
     * @return a string representing the size in a human-readable format
     */
    public static String humanReadableByteCount(Path path) {
        try {
            return humanReadableByteCount(getSize(path));
        } catch (IOException exception) {
            return "0 B";
        }
    }

    /**
     * Converts a size in bytes to a human-readable format.
     *
     * @param size the size in bytes
     * @return a string representing the size in a human-readable format
     */
    private static String humanReadableByteCount(long size) {
        if (size <= 0)
            return "0 B";


        int unit = 1024;
        int exp = (int) (Math.log(size) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", size / Math.pow(unit, exp), pre);
    }

    public static void copyDirectoryContents(Path src, Path dst, CopyOption... options) {
        try(Stream<Path> files = Files.walk(src)) {
            files.forEach(source -> {
                try {
                    Path destination = dst.resolve(src.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(destination);
                    } else {
                        Files.copy(source, destination, options);
                    }
                } catch (IOException exception) {
                    throw new RuntimeException("Failed to copy directory contents", exception);
                }
            });
        } catch (IOException exception) {
            throw new RuntimeException("Failed to copy directory contents", exception);
        }
    }
}
