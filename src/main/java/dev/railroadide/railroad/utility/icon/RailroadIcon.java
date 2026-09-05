package dev.railroadide.railroad.utility.icon;

import org.kordamp.ikonli.Ikon;

/**
 * Application-specific glyphs supplied by the Railroad icon font.
 *
 * <p>Each constant records the description understood by Ikonli and the glyph's
 * Unicode private-use code point.</p>
 */
public enum RailroadIcon implements Ikon {
    /** The {@code rr-jar-file} file-type icon at glyph {@code U+ECA6}. */
    JAR_FILE("rr-jar-file", 0xECA6);

    private final String description;
    private final int code;

    RailroadIcon(String description, int code) {
        this.description = description;
        this.code = code;
    }

    /**
     * Finds the Railroad icon registered under an Ikonli description.
     *
     * @param description description key, such as {@code rr-jar-file}
     * @return the icon with the requested description
     * @throws IllegalArgumentException if the description is not registered
     */
    public static RailroadIcon findByDescription(String description) {
        for (RailroadIcon icon : values()) {
            if (icon.getDescription().equals(description))
                return icon;
        }

        throw new IllegalArgumentException("Unknown icon: " + description);
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public int getCode() {
        return code;
    }
}
