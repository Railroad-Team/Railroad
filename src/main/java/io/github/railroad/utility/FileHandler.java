package io.github.railroad.utility;

import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileHandler {
    public static String getExtension(Path path) {
        return getExtension(path.toString());
    }

    public static String getExtension(String path) {
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return path.substring(i + 1);
        }

        return null;
    }

    public static void copyUrlToFile(String url, Path path) throws RuntimeException {
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

    public static boolean isDirectoryEmpty(Path directory, Runnable onEmpty, Runnable onNotEmpty) throws IOException {
        if (Files.notExists(directory)) {
            if(directory.toFile().mkdirs()) { // We use IO instead of NIO so that we block the thread until it's created
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

    public static boolean is404(String url) {
        try {
            return ((HttpURLConnection) new URI(url).toURL().openConnection()).getResponseCode() == 404;
        } catch (IOException | URISyntaxException exception) {
            return false;
        }
    }

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

    public static void openInExplorer(Path path) throws RuntimeException {
        openInDefaultApplication(Files.isDirectory(path) ? path : path.getParent());
    }

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

    public static boolean isImageFile(Path path) {
        String extension = getExtension(path.toString());
        return extension != null && switch (extension) {
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> true;
            default -> false;
        };
    }

    public static void openInDefaultApplication(Path path) throws RuntimeException {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(path.toFile());
            } catch (IOException exception) {
                throw new RuntimeException("Failed to open in default application", exception);
            }
        }
    }

    public static long getSize(Path path) throws IOException {
        return Files.size(path);
    }

    public static String humanReadableByteCount(Path path) {
        try {
            return humanReadableByteCount(getSize(path));
        } catch (IOException exception) {
            return "0 B";
        }
    }

    private static String humanReadableByteCount(long size) {
        if (size <= 0)
            return "0 B";

        int unit = 1024;
        int exp = (int) (Math.log(size) / Math.log(unit));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %sB", size / Math.pow(unit, exp), pre);
    }

    public static int getColorDepth(Image image) {
        PixelFormat<?> pixelFormat = image.getPixelReader().getPixelFormat();

        // Estimate color depth based on the PixelFormat
        if (pixelFormat.getType() == PixelFormat.Type.INT_ARGB) {
            return 32; // 8 bits per channel + alpha channel
        } else if (pixelFormat.getType() == PixelFormat.Type.BYTE_BGRA ||
                pixelFormat.getType() == PixelFormat.Type.BYTE_BGRA_PRE) {
            return 32; // 8 bits per channel + alpha channel
        } else if (pixelFormat.getType() == PixelFormat.Type.BYTE_RGB) {
            return 24; // 8 bits per channel, no alpha channel
        } else if (pixelFormat.getType() == PixelFormat.Type.BYTE_INDEXED) {
            return 8;  // Typically 8 bits for indexed color (palette-based)
        }

        // If the format is unknown or not covered above, return -1 (or any indication of unknown depth)
        return -1;
    }

    public static String getColorSpace(Image image) {
        try {
            ImageReader reader = ImageIO.getImageReadersByFormatName(image.getUrl().substring(image.getUrl().lastIndexOf('.') + 1)).next();
            reader.setInput(ImageIO.createImageInputStream(Files.newInputStream(Path.of(URLDecoder.decode(image.getUrl().substring("file:/".length()), StandardCharsets.ISO_8859_1)))));
            return switch (reader.getImageTypes(0).next().getColorModel().getColorSpace().getType()) {
                case ColorSpace.TYPE_XYZ -> "XYZ";
                case ColorSpace.TYPE_Lab -> "Lab";
                case ColorSpace.TYPE_Luv -> "Luv";
                case ColorSpace.TYPE_YCbCr -> "YCbCr";
                case ColorSpace.TYPE_Yxy -> "Yxy";
                case ColorSpace.TYPE_RGB -> "RGB";
                case ColorSpace.TYPE_GRAY, ColorSpace.CS_GRAY -> "GRAY";
                case ColorSpace.TYPE_HSV -> "HSV";
                case ColorSpace.TYPE_HLS -> "HLS";
                case ColorSpace.TYPE_CMYK -> "CMYK";
                case ColorSpace.TYPE_CMY -> "CMY";
                case ColorSpace.TYPE_2CLR -> "2CLR";
                case ColorSpace.TYPE_3CLR -> "3CLR";
                case ColorSpace.TYPE_4CLR -> "4CLR";
                case ColorSpace.TYPE_5CLR -> "5CLR";
                case ColorSpace.TYPE_6CLR -> "6CLR";
                case ColorSpace.TYPE_7CLR -> "7CLR";
                case ColorSpace.TYPE_8CLR -> "8CLR";
                case ColorSpace.TYPE_9CLR -> "9CLR";
                case ColorSpace.TYPE_ACLR -> "ACLR";
                case ColorSpace.TYPE_BCLR -> "BCLR";
                case ColorSpace.TYPE_CCLR -> "CCLR";
                case ColorSpace.TYPE_DCLR -> "DCLR";
                case ColorSpace.TYPE_ECLR -> "ECLR";
                case ColorSpace.TYPE_FCLR -> "FCLR";
                case ColorSpace.CS_sRGB -> "sRGB";
                case ColorSpace.CS_LINEAR_RGB -> "LINEAR_RGB";
                case ColorSpace.CS_CIEXYZ -> "CIEXYZ";
                case ColorSpace.CS_PYCC -> "PYCC";
                default -> "Unknown";
            };
        } catch (IOException exception) {
            exception.printStackTrace();
            return "Unknown";
        }
    }

    public static String getNumberOfColors(Image image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getPixelReader().getArgb(x, y));
            }
        }

        return String.valueOf(colors.size());
    }

    public static String getNumberOfFrames(Path imagePath) {
        try {
            // get gif metadata
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(ImageIO.createImageInputStream(imagePath.toFile()));

            return String.valueOf(reader.getNumImages(true));
        } catch (IOException exception) {
            return "Unknown";
        }
    }

    public static Image createCheckerboard(int width, int height, int squareSize, Color color1, Color color2) {
        // create a writable image
        var image = new WritableImage(width, height);
        for (int x = 0; x < width; x += squareSize) {
            for (int y = 0; y < height; y += squareSize) {
                int squareSizeX = Math.min(squareSize, width - x);
                int squareSizeY = Math.min(squareSize, height - y);
                fillArea(image.getPixelWriter(), x, y, x + squareSizeX, y + squareSizeY, (x / squareSize + y / squareSize) % 2 == 0 ? color1 : color2);
            }
        }

        return image;
    }

    private static void fillArea(PixelWriter pixelWriter, int startX, int startY, int endX, int endY, Color color) {
        for (int x = startX; x < endX; x++) {
            for (int y = startY; y < endY; y++) {
                pixelWriter.setColor(x, y, color);
            }
        }
    }

    public static boolean isImageTransparent(Image image) {
        PixelReader pixelReader = image.getPixelReader();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (pixelReader.getColor(x, y).getOpacity() < 1) {
                    return true;
                }
            }
        }

        return false;
    }
}
