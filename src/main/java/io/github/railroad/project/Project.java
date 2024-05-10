package io.github.railroad.project;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.util.UUID;


import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;


public class Project {
    private final Path path;
    private String alias;
    private Optional<Image> icon;
    private long lastOpened;

    private String id;
    private ProjectManager manager;

    public Project(Path path) {
        this(path, path.getFileName().toString());
    }

    public Project(Path path, String alias) {
        this(path, alias, null);
    }

    public Project(Path path, String alias, Image icon) {
        this.path = path;
        this.alias = alias;
        this.icon = icon == null ? Optional.of(createIcon(this)) : Optional.of(icon);
    }

    private static Image createIcon(Project project) {
        var color = new Color(Math.abs(project.path.toAbsolutePath().toString().hashCode()) % 0xFFFFFF);
        String abbreviation = getAbbreviation(project.alias).toUpperCase(Locale.ROOT);
        abbreviation = abbreviation.isBlank() ? "?" : abbreviation;
        abbreviation = abbreviation.length() > 4 ? abbreviation.substring(0, 4) : abbreviation;

        var image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRoundRect(0, 0, 128, 128, 32, 32);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font("Arial", Font.BOLD, 64 - (abbreviation.length() * 6)));
        var metrics = graphics.getFontMetrics();
        var x = (128 - metrics.stringWidth(abbreviation)) / 2;
        var y = ((128 - metrics.getHeight()) / 2) + metrics.getAscent();
        graphics.drawString(abbreviation, x, y);

        graphics.dispose();

        return SwingFXUtils.toFXImage(image, null);
    }

    // Take the first character of each word in the alias
    private static String getAbbreviation(String alias) {
        var abbreviation = new StringBuilder();
        for (String word : alias.split(" ")) {
            if (word.isBlank())
                continue;

            abbreviation.append(word.charAt(0));
        }

        return abbreviation.toString();
    }

    // TODO: Delete this once projects can be saved
    private static String generateRandomName() {
        char[] chars = "abcdefghijklmnopqrs t u v w x y z ".toCharArray();
        var builder = new StringBuilder();
        int length = ThreadLocalRandom.current().nextInt(10, 20);
        for (int i = 0; i < length; i++) {
            char c = chars[ThreadLocalRandom.current().nextInt(chars.length)];

            while (i == 0 && c == ' ')
                c = chars[ThreadLocalRandom.current().nextInt(chars.length)];

            if (i == 0 || builder.charAt(i - 1) == ' ')
                c = Character.toUpperCase(c);

            while (i != 0 && c == ' ' && builder.charAt(i - 1) == ' ') {
                c = chars[ThreadLocalRandom.current().nextInt(chars.length)];
            }

            if (c == ' ' && i == length - 1)
                continue;

            builder.append(c);
        }

        return builder.toString();
    }

    public Path getPath() {
        return path;
    }

    public String getPathStr() {return getPath().toString(); }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public Optional<Image> getIcon() {
        return icon;
    }

    public void setIcon(Image icon) {
        this.icon = Optional.of(icon);
    }

    public long getLastOpened() {
        return lastOpened;
    }

    public String getLastOpenedFriendly() {
        var instant = Instant.ofEpochMilli(this.getLastOpened());
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());
        var currentTime = ZonedDateTime.now();
        long daysDifference = ChronoUnit.DAYS.between(zonedDateTime, currentTime);
        long secondsDifference = ChronoUnit.SECONDS.between(zonedDateTime, currentTime);
        if (daysDifference > 4000) {
            return "You forgot me ;(";
        } else {
            if (secondsDifference < 60) {
                return secondsDifference + " seconds ago";
            } else if (secondsDifference < 3600) {
                long minutes = secondsDifference / 60;
                return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
            } else if (secondsDifference < 86400) {
                long hours = secondsDifference / 3600;
                return hours + (hours == 1 ? " hour ago" : " hours ago");
            } else if (secondsDifference < 604800) {
                long days = secondsDifference / 86400;
                return days + (days == 1 ? " day ago" : " days ago");
            } else if (secondsDifference < 2419200) {
                long weeks = secondsDifference / 604800;
                return weeks + (weeks == 1 ? " week ago" : " weeks ago");
            } else if (secondsDifference < 29030400) {
                long months = secondsDifference / 2419200;
                return months + (months == 1 ? " month ago" : " months ago");
            } else {
                long years = secondsDifference / 29030400;
                return years + (years == 1 ? " year ago" : " years ago");
            }
        }
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened = lastOpened;
        if (this.manager != null) {
            this.manager.updateProjectInfo(this);
        }
    }

    public void open() {
        System.out.println("Opening project: " + path);
        setLastOpened(System.currentTimeMillis());
    }

    @Override
    public String toString() {
        return alias + " - " + getLastOpened();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Project project = (Project) obj;
        return path.equals(project.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    public void setManager(ProjectManager manager) {
        this.manager = manager;
    }

    public String getId() {
        if (this.id == null || this.id.isEmpty()) {
            this.id = UUID.randomUUID().toString();
        }

        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}