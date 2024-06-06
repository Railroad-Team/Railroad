package io.github.railroad.project.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.utility.JsonSerializable;
import io.github.railroad.vcs.Repository;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class Project implements JsonSerializable<JsonObject> {
    private final ObjectProperty<Path> path = new ReadOnlyObjectWrapper<>();
    private final StringProperty alias = new SimpleStringProperty();
    private final ObjectProperty<Image> icon = new SimpleObjectProperty<>();
    private final LongProperty lastOpened = new SimpleLongProperty();
    private final ObjectProperty<Repository> repository = new SimpleObjectProperty<>();
    private final StringProperty id = new SimpleStringProperty();

    public Project(Path path) {
        this(path, path.getFileName().toString());
    }

    public Project(Path path, String alias) {
        this(path, alias, null);
    }

    public Project(Path path, String alias, Image icon) {
        this.path.set(path);
        this.alias.set(alias);
        this.icon.set(icon == null ? createIcon(this) : icon);
    }

    private static Image createIcon(Project project) {
        var color = new Color(Math.abs(project.path.get().toAbsolutePath().toString().hashCode()) % 0xFFFFFF);
        String abbreviation = getAbbreviation(project.alias.get()).toUpperCase(Locale.ROOT);
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

    public String getPathString() {
        return this.path.get().toString();
    }

    public void setIcon(Image icon) {
        this.icon.set(icon == null ? createIcon(this) : icon);
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened.set(lastOpened);
    }

    // TODO: Extract for reusability purposes
    public static String getLastOpenedFriendly(@NotNull Number time) {
        var instant = Instant.ofEpochMilli(time.longValue());
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

    public void open() {
        Railroad.LOGGER.debug("Opening project: {}", getPathString());
        setLastOpened(System.currentTimeMillis());
        Railroad.PROJECT_MANAGER.updateProjectInfo(this);
    }

    @Override
    public String toString() {
        return alias.get() + " - " + lastOpened.get();
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

    public String getId() {
        if (this.id.get() == null || this.id.get().isEmpty()) {
            this.id.set(UUID.randomUUID().toString());
        }

        return id.get();
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("Path", getPathString());
        json.addProperty("Alias", alias.get());
        json.addProperty("LastOpened", lastOpened.get());
        json.addProperty("Id", getId());
        json.addProperty("Icon", this.icon.map(Image::getUrl).orElse("").getValue());

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if(json == null)
            return;

        if(json.has("Path")) {
            JsonElement pathElement = json.get("Path");
            if(pathElement.isJsonPrimitive()) {
                JsonPrimitive pathPrimitive = pathElement.getAsJsonPrimitive();
                if(pathPrimitive.isString())
                    this.path.set(Path.of(pathElement.getAsString()));
            }
        }

        if(json.has("Alias")) {
            JsonElement aliasElement = json.get("Alias");
            if(aliasElement.isJsonPrimitive()) {
                JsonPrimitive aliasPrimitive = aliasElement.getAsJsonPrimitive();
                if(aliasPrimitive.isString())
                    this.alias.set(aliasElement.getAsString());
            }
        }

        if(json.has("LastOpened")) {
            JsonElement lastOpenedElement = json.get("LastOpened");
            if(lastOpenedElement.isJsonPrimitive()) {
                JsonPrimitive lastOpenedPrimitive = lastOpenedElement.getAsJsonPrimitive();
                if(lastOpenedPrimitive.isNumber())
                    this.lastOpened.set(lastOpenedElement.getAsLong());
            }
        }

        if(json.has("Id")) {
            JsonElement idElement = json.get("Id");
            if(idElement.isJsonPrimitive()) {
                JsonPrimitive idPrimitive = idElement.getAsJsonPrimitive();
                if(idPrimitive.isString())
                    this.id.set(idElement.getAsString());
            }
        }

        boolean hasIcon = false;
        if(json.has("Icon")) {
            JsonElement iconElement = json.get("Icon");
            if(iconElement.isJsonPrimitive()) {
                JsonPrimitive iconPrimitive = iconElement.getAsJsonPrimitive();
                if(iconPrimitive.isString() && !iconElement.getAsString().isBlank()) {
                    this.icon.set(new Image(iconElement.getAsString()));
                    hasIcon = true;
                }
            }
        }

        if(!hasIcon)
            this.icon.set(createIcon(this));
    }

    public static Optional<Project> createFromJson(JsonObject json) {
        if(!json.has("Path"))
            return Optional.empty();

        JsonElement pathElement = json.get("Path");
        if(!pathElement.isJsonPrimitive())
            return Optional.empty();

        JsonPrimitive pathPrimitive = pathElement.getAsJsonPrimitive();
        if(!pathPrimitive.isString())
            return Optional.empty();

        var project = new Project(Path.of(pathElement.getAsString()));
        project.fromJson(json);

        return Optional.of(project);
    }

    public String getAlias() {
        return alias.get();
    }

    public long getLastOpened() {
        return lastOpened.get();
    }

    public Image getIcon() {
        return icon.get();
    }

    public ObjectProperty<Image> iconProperty() {
        return icon;
    }

    public StringProperty aliasProperty() {
        return alias;
    }

    public ObjectProperty<Repository> repositoryProperty() {
        return repository;
    }

    public LongProperty lastOpenedProperty() {
        return lastOpened;
    }
}