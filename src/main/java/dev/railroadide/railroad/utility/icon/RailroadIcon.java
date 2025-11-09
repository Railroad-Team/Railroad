package dev.railroadide.railroad.utility.icon;

import org.kordamp.ikonli.Ikon;

public enum RailroadIcon implements Ikon {
    JAR_FILE("rr-jar-file", 0xECA6);

    private final String description;
    private final int code;

    RailroadIcon(String description, int code) {
        this.description = description;
        this.code = code;
    }

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
