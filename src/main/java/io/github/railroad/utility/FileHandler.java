package io.github.railroad.utility;

import javafx.scene.Node;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileHandler {
    public static String getExtension(String path) {
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return path.substring(i + 1);
        }

        return null;
    }

    public static void copyUrlToFile(String url, Path path) {
        try (InputStream in = new URI(url).toURL().openStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException("Failed to copy URL to file", exception);
        }
    }

    public static void updateKeyValuePairByLine(String key, String value, Path file) throws IOException {
        var stringBuilder = new StringBuilder();

        List<String> lines = Files.readAllLines(file);
        for (String line : lines) {
            if (line.startsWith(key + "=")) {
                // Replace the existing value
                line = key + "=" + value;
            }

            stringBuilder.append(line).append("\n");
        }

        Files.writeString(file, stringBuilder.toString());
    }

    public static void unzipFile(Path fileZip, Path dstDir) throws IOException {
        try (var zipInputStream = new ZipInputStream(Files.newInputStream(fileZip))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                Path newFile = newFile(dstDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFile);
                    if (!Files.isDirectory(newFile))
                        throw new IOException("Failed to create directory " + newFile);
                } else {
                    // fix for Windows-created archives
                    Path parent = newFile.getParent();
                    Files.createDirectories(parent);
                    if (!Files.isDirectory(parent))
                        throw new IOException("Failed to create directory " + parent);

                    // write file content
                    Files.copy(zipInputStream, newFile, StandardCopyOption.REPLACE_EXISTING);
                }

                zipEntry = zipInputStream.getNextEntry();
            }

            zipInputStream.closeEntry();
        }
    }

    public static Path newFile(Path destinationDir, ZipEntry zipEntry) throws IOException {
        var destFile = Paths.get(destinationDir.toString(), zipEntry.getName());

        if (!destFile.normalize().startsWith(destinationDir))
            throw new IOException("Bad zip entry: " + zipEntry.getName());

        return destFile;
    }

    public static void copyFolder(Path src, Path dst) {
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

    public static void deleteFolder(Path folder) {
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

    public static boolean isDirectoryEmpty(Path directory, Runnable onEmpty, Runnable onNotEmpty) throws IOException {
        if (Files.notExists(directory)) {
            directory.toFile().mkdirs(); // We use IO instead of NIO so that we block the thread until it's created
            onEmpty.run();
            return true;
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
    }

    public static boolean isDirectoryEmpty(Path directory, Runnable onEmpty) throws IOException {
        return isDirectoryEmpty(directory, onEmpty, () -> {
        });
    }

    public static boolean isDirectoryEmpty(Path directory) throws IOException {
        return isDirectoryEmpty(directory, () -> {
        });
    }

    public static boolean urlExists(String url) {
        try {
            return new URI(url).toURL().openConnection().getContentType() != null;
        } catch (IOException | URISyntaxException exception) {
            return false;
        }
    }

    public static Node getIcon(Path path) {
        if (Files.isDirectory(path))
            return new FontIcon(FontAwesomeRegular.FOLDER);

        String extension = getExtension(path.toString());
        if (extension == null) {
            return new FontIcon(FontAwesomeRegular.FILE);
        }

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
}
