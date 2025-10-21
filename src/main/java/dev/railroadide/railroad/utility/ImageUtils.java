package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.Railroad;
import javafx.scene.image.*;
import javafx.scene.paint.Color;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.color.ColorSpace;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public final class ImageUtils {
    private ImageUtils() {
        // Utility class, prevent instantiation
    }

    /**
     * Gets the color depth of the image.
     *
     * @param image The image to analyze.
     * @return The color depth in bits per pixel, or -1 if unknown.
     */
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

    /**
     * Gets the color space of the image.
     *
     * @param image The image to analyze.
     * @return The color space as a string, or "Unknown" if it cannot be determined.
     */
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
            Railroad.LOGGER.error("Failed to get color space for image: {}", image.getUrl(), exception);
            return "Unknown";
        }
    }

    /**
     * Gets the number of colors in the image.
     *
     * @param image The image to analyze.
     * @return The number of unique colors in the image.
     */
    public static String getNumberOfColors(Image image) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                colors.add(image.getPixelReader().getArgb(x, y));
            }
        }

        return String.valueOf(colors.size());
    }

    /**
     * Gets the number of frames in a GIF image.
     *
     * @param imagePath The path to the GIF image.
     * @return The number of frames in the GIF, or "Unknown" if it cannot be determined.
     */
    public static String getNumberOfFrames(Path imagePath) {
        try {
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(ImageIO.createImageInputStream(imagePath.toFile()));

            return String.valueOf(reader.getNumImages(true));
        } catch (IOException exception) {
            return "Unknown";
        }
    }

    /**
     * Creates a checkerboard image.
     *
     * @param width      The width of the image.
     * @param height     The height of the image.
     * @param squareSize The size of each square in the checkerboard.
     * @param color1     The first color for the checkerboard squares.
     * @param color2     The second color for the checkerboard squares.
     * @return A WritableImage representing the checkerboard pattern.
     */
    public static Image createCheckerboard(int width, int height, int squareSize, Color color1, Color color2) {
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

    /**
     * Checks if the image has any transparent pixels.
     *
     * @param image The image to check.
     * @return True if the image has transparent pixels, false otherwise.
     */
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
