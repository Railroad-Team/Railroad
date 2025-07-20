package io.github.railroad.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.core.utility.JsonSerializable;
import io.github.railroad.core.vcs.Repository;
import io.github.railroad.project.facet.Facet;
import io.github.railroad.project.facet.FacetManager;
import io.github.railroad.project.facet.FacetType;
import io.github.railroad.railroadpluginapi.events.ProjectAliasChangedEvent;
import io.github.railroad.utility.StringUtils;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class Project implements JsonSerializable<JsonObject>, io.github.railroad.railroadpluginapi.dto.Project {
    private final ObjectProperty<Path> path = new ReadOnlyObjectWrapper<>();
    private final StringProperty alias = new SimpleStringProperty();
    private final ObjectProperty<Image> icon = new SimpleObjectProperty<>();
    private final LongProperty lastOpened = new SimpleLongProperty(-1);
    private final ObjectProperty<Repository> repository = new SimpleObjectProperty<>();
    private final StringProperty id = new SimpleStringProperty();
    private final ObservableSet<Facet<?>> facets = FXCollections.observableSet();

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

    private void discoverFacets() {
        this.facets.clear();
        FacetManager.scan(this).thenAcceptAsync(facets -> {
            for (Facet<?> facet : facets) {
                if (facet != null) {
                    this.facets.add(facet);
                } else {
                    Railroad.LOGGER.warn("Discovered null facet for project: {}", getPathString());
                }
            }
        }).exceptionally(ex -> {
            Railroad.LOGGER.error("Failed to discover facets for project: {}", getPathString(), ex);
            return null;
        });
    }

    private static Image createIcon(Project project) {
        var color = new Color(Math.abs(project.path.get().toAbsolutePath().toString().hashCode() % 0xFFFFFF));
        String abbreviation = StringUtils.getAbbreviation(project.alias.get()).toUpperCase(Locale.ROOT);
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

    public static Optional<Project> createFromJson(JsonObject json) {
        if (!json.has("Path"))
            return Optional.empty();

        JsonElement pathElement = json.get("Path");
        if (!pathElement.isJsonPrimitive())
            return Optional.empty();

        JsonPrimitive pathPrimitive = pathElement.getAsJsonPrimitive();
        if (!pathPrimitive.isString())
            return Optional.empty();

        var project = new Project(Path.of(pathElement.getAsString()));
        project.fromJson(json);

        return Optional.of(project);
    }

    @Override
    public Path getPath() {
        return this.path.get();
    }

    public String getPathString() {
        return getPath().toAbsolutePath().toString();
    }

    public void open() {
        Railroad.LOGGER.debug("Opening project: {}", getPathString());
        setLastOpened(System.currentTimeMillis());
        Railroad.PROJECT_MANAGER.updateProjectInfo(this);
        Railroad.switchToIDE(this);
        discoverFacets();
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
        if (!this.facets.isEmpty()) {
            var facetsArray = new JsonArray();
            for (Facet<?> facet : this.facets) {
                var facetJson = new JsonObject();
                facetJson.addProperty("Type", facet.getType().id());
                facetJson.add("Data", Railroad.GSON.toJsonTree(facet));
                facetsArray.add(facetJson);
            }

            json.add("Facets", facetsArray);
        }

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if (json == null)
            return;

        if (json.has("Path")) {
            JsonElement pathElement = json.get("Path");
            if (pathElement.isJsonPrimitive()) {
                JsonPrimitive pathPrimitive = pathElement.getAsJsonPrimitive();
                if (pathPrimitive.isString()) {
                    this.path.set(Path.of(pathElement.getAsString()));
                } else if (pathPrimitive.isNumber()) {
                    try {
                        this.path.set(Path.of(String.valueOf(pathPrimitive.getAsNumber())));
                    } catch (Exception exception) {
                        Railroad.LOGGER.warn("Project JSON 'Path' is not a valid path: {}", pathElement, exception);
                    }
                } else Railroad.LOGGER.warn("Project JSON 'Path' is not a string or number: {}", pathElement);
            } else Railroad.LOGGER.warn("Project JSON 'Path' is not a string: {}", pathElement);
        }

        if (json.has("Alias")) {
            JsonElement aliasElement = json.get("Alias");
            if (aliasElement.isJsonPrimitive()) {
                JsonPrimitive aliasPrimitive = aliasElement.getAsJsonPrimitive();
                if (aliasPrimitive.isString()) {
                    this.alias.set(aliasElement.getAsString());
                } else if (aliasPrimitive.isNumber()) {
                    this.alias.set(String.valueOf(aliasPrimitive.getAsNumber()));
                } else Railroad.LOGGER.warn("Project JSON 'Alias' is not a string or number: {}", aliasElement);
            } else Railroad.LOGGER.warn("Project JSON 'Alias' is not a string: {}", aliasElement);
        }

        if (json.has("LastOpened")) {
            JsonElement lastOpenedElement = json.get("LastOpened");
            if (lastOpenedElement.isJsonPrimitive()) {
                JsonPrimitive lastOpenedPrimitive = lastOpenedElement.getAsJsonPrimitive();
                if (lastOpenedPrimitive.isNumber()) {
                    this.lastOpened.set(lastOpenedElement.getAsLong());
                } else if (lastOpenedPrimitive.isString()) {
                    try {
                        this.lastOpened.set(Long.parseLong(lastOpenedElement.getAsString()));
                    } catch (NumberFormatException exception) {
                        Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a valid number: {}", lastOpenedElement, exception);
                    }
                } else {
                    Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a number or string: {}", lastOpenedElement);
                }
            } else Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a primitive: {}", lastOpenedElement);
        }

        if (json.has("Id")) {
            JsonElement idElement = json.get("Id");
            if (idElement.isJsonPrimitive()) {
                JsonPrimitive idPrimitive = idElement.getAsJsonPrimitive();
                if (idPrimitive.isString()) {
                    this.id.set(idElement.getAsString());
                } else if (idPrimitive.isNumber()) {
                    this.id.set(String.valueOf(idPrimitive.getAsNumber()));
                } else Railroad.LOGGER.warn("Project JSON 'Id' is not a string or number: {}", idElement);
            } else Railroad.LOGGER.warn("Project JSON 'Id' is not a string: {}", idElement);
        }

        boolean hasIcon = false;
        if (json.has("Icon")) {
            JsonElement iconElement = json.get("Icon");
            if (iconElement.isJsonPrimitive()) {
                JsonPrimitive iconPrimitive = iconElement.getAsJsonPrimitive();
                if (iconPrimitive.isString() && !iconElement.getAsString().isBlank()) {
                    this.icon.set(new Image(iconElement.getAsString()));
                    hasIcon = true;
                } else if (iconPrimitive.isNumber()) {
                    try {
                        this.icon.set(new Image(String.valueOf(iconPrimitive.getAsNumber())));
                        hasIcon = true;
                    } catch (Exception exception) {
                        Railroad.LOGGER.warn("Project JSON 'Icon' is not a valid URL or number: {}", iconElement, exception);
                    }
                } else Railroad.LOGGER.warn("Project JSON 'Icon' is not a string or number: {}", iconElement);
            } else if (iconElement.isJsonNull()) {
                Railroad.LOGGER.warn("Project JSON 'Icon' is null, using default icon.");
            } else Railroad.LOGGER.warn("Project JSON 'Icon' is not a primitive: {}", iconElement);
        }

        if (json.has("Facets")) {
            JsonElement facetsElement = json.get("Facets");
            if (facetsElement.isJsonArray()) {
                JsonArray facetsArray = facetsElement.getAsJsonArray();
                for (JsonElement facetElement : facetsArray) {
                    if (facetElement.isJsonObject()) {
                        JsonObject facetJson = facetElement.getAsJsonObject();
                        if (facetJson.has("Type") && facetJson.has("Data")) {
                            JsonElement typeElement = facetJson.get("Type");
                            JsonElement dataElement = facetJson.get("Data");

                            if (typeElement.isJsonPrimitive()) {
                                String typeId = typeElement.getAsString();
                                FacetType<?> type = FacetManager.getType(typeId);
                                if (type != null) {
                                    this.facets.add(Railroad.GSON.fromJson(dataElement, Facet.class));
                                } else Railroad.LOGGER.warn("Invalid project facet type: {}", typeId);
                            } else Railroad.LOGGER.warn("Invalid project facet JSON: {}", facetJson);
                        } else Railroad.LOGGER.warn("Project facet JSON missing 'Type' or 'Data': {}", facetJson);
                    } else Railroad.LOGGER.warn("Invalid project facet JSON element: {}", facetElement);
                }
            } else Railroad.LOGGER.warn("Project facets JSON is not an array: {}", facetsElement);
        }

        if (!hasIcon)
            this.icon.set(createIcon(this));

        if (facets.isEmpty())
            discoverFacets();
    }

    public String getAlias() {
        return alias.get();
    }

    @Override
    public void setAlias(@NotNull String alias) {
        if (alias == null || alias.isBlank())
            throw new IllegalArgumentException("Alias cannot be null or blank");

        String originalAlias = this.alias.get();
        this.alias.set(alias);
        Railroad.EVENT_BUS.publish(new ProjectAliasChangedEvent(this, originalAlias, alias));
    }

    public long getLastOpened() {
        return lastOpened.get();
    }

    public void setLastOpened(long lastOpened) {
        this.lastOpened.set(lastOpened);
    }

    public Image getIcon() {
        return icon.get();
    }

    public void setIcon(Image icon) {
        this.icon.set(icon == null ? createIcon(this) : icon);
    }

    public ObjectProperty<Image> iconProperty() {
        return icon;
    }

    public SetProperty<Facet<?>> facetsProperty() {
        return new ReadOnlySetWrapper<>(facets);
    }

    public List<Facet<?>> getFacets() {
        return List.copyOf(facets);
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