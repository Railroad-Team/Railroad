package dev.railroadide.railroad.project;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.ide.ui.IDEPane;
import dev.railroadide.railroad.ide.ui.editor.EditorTabSessionState;
import dev.railroadide.railroad.ide.ui.editor.EditorWorkspaceSessionState;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.plugin.spi.dto.Document;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.plugin.spi.events.ProjectAliasChangedEvent;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetManager;
import dev.railroadide.railroad.project.facet.FacetType;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.ProjectPathIdentityUtils;
import dev.railroadide.railroad.utility.StringUtils;
import dev.railroadide.railroad.vcs.Repository;
import dev.railroadide.railroad.vcs.git.GitClient;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.execution.GitProcessRunner;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a project managed by Railroad, including its path, metadata,
 * facets, build tools, version-control integration, and persisted session state.
 */
public class RailroadProject implements Project {
    private static final String PROJECT_CONFIG_LOCATION = "project.json";

    private final ObjectProperty<Path> path = new ReadOnlyObjectWrapper<>();
    private final StringProperty alias = new SimpleStringProperty();
    private final ObjectProperty<Image> icon = new SimpleObjectProperty<>();
    private final LongProperty lastOpened = new SimpleLongProperty(-1);
    private final ObjectProperty<Repository> repository = new SimpleObjectProperty<>();
    private final StringProperty id = new SimpleStringProperty();
    private final ObservableSet<Facet<?>> facets = FXCollections.observableSet();
    private final StringProperty description = new SimpleStringProperty();
    private final ObjectProperty<License> license = new SimpleObjectProperty<>();
    @Getter
    private final ProjectDataStore dataStore;
    @Getter
    private final RunConfigurationManager runConfigManager;
    @Getter
    private final GradleManager gradleManager;
    @Getter
    private final GitManager gitManager;
    @Getter
    private final DebuggingManager debuggingManager;

    /**
     * Creates a project using the final path component as its alias.
     *
     * @param path the project directory
     */
    public RailroadProject(Path path) {
        this(path, path.getFileName().toString());
    }

    /**
     * Creates a project with a path and display alias.
     *
     * @param path the project directory
     * @param alias the display alias for the project
     */
    public RailroadProject(Path path, String alias) {
        this(path, alias, null);
    }

    /**
     * Creates a project with a path, display alias, and optional icon.
     *
     * @param path the project directory
     * @param alias the display alias for the project
     * @param icon the project icon, or {@code null} to generate a default icon
     */
    public RailroadProject(Path path, String alias, Image icon) {
        this.path.set(ProjectPathIdentityUtils.normalize(path));
        this.alias.set(alias);
        this.icon.set(icon == null ? createIcon() : icon);
        this.dataStore = new ProjectDataStore(this);
        this.runConfigManager = new RunConfigurationManager(this);
        this.gradleManager = new GradleManager(this);
        Path gitExecutable = Settings.GIT_EXECUTABLE_PATH.getOrDefaultValue();
        this.gitManager = new GitManager(this, new GitClient(new GitProcessRunner(gitExecutable)));
        Settings.GIT_EXECUTABLE_PATH.addListener((oldPath, newPath) -> this.gitManager
            .setGitExecutablePath(newPath != null ? newPath : Settings.GIT_EXECUTABLE_PATH.getDefaultValue()));
        this.debuggingManager = new DebuggingManager(this);
    }

    private BufferedImage createIconImage() {
        var color = new Color(Math.abs(getPath().toAbsolutePath().toString().hashCode() % 0xFFFFFF));
        String abbreviation = StringUtils.getAbbreviation(getAlias()).toUpperCase(Locale.ROOT);
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

        return image;
    }

    private Image createIcon() {
        BufferedImage iconImage = createIconImage();
        Path iconPath = ConfigHandler.getConfigDirectory().resolve("project-icons").resolve(getPathBase64() + ".png");
        try {
            Files.createDirectories(iconPath.getParent());
            ImageIO.write(iconImage, "png", iconPath.toFile());
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to create project icon for: {}", getPath(), exception);
            return SwingFXUtils.toFXImage(iconImage, null);
        }

        return new Image(iconPath.toUri().toString());
    }

    /**
     * Creates a project from its JSON representation.
     *
     * @param json the JSON object to deserialize
     * @return the deserialized project, or an empty optional when no valid path is present
     */
    public static Optional<RailroadProject> createFromJson(JsonObject json) {
        if (!json.has("Path"))
            return Optional.empty();

        JsonElement pathElement = json.get("Path");
        if (!pathElement.isJsonPrimitive())
            return Optional.empty();

        JsonPrimitive pathPrimitive = pathElement.getAsJsonPrimitive();
        if (!pathPrimitive.isString())
            return Optional.empty();

        var project = new RailroadProject(Path.of(pathElement.getAsString()));
        project.fromJson(json);

        return Optional.of(project);
    }

    /**
     * Returns the project path encoded as Base64 using UTF-8.
     *
     * @return the Base64-encoded project path
     */
    public String getPathBase64() {
        return Base64.getEncoder().encodeToString(getPathString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes a UTF-8 project path from its Base64 representation.
     *
     * @param base64Path the Base64-encoded path
     * @return the decoded project path
     */
    public Path getPathFromBase64(String base64Path) {
        return Path.of(new String(Base64.getDecoder().decode(base64Path), StandardCharsets.UTF_8));
    }

    private void discoverFacets() {
        this.facets.clear();
        FacetManager.scan(this).thenAccept(discoveredFacets -> Platform.runLater(() -> {
            for (Facet<?> facet : discoveredFacets) {
                if (facet != null) {
                    this.facets.add(facet);
                    Railroad.EVENT_BUS.publish(new FacetDetectedEvent(this, facet));
                } else {
                    Railroad.LOGGER.warn("Discovered null facet for project: {}", getPathString());
                }
            }
        })).exceptionally(ex -> {
            Railroad.LOGGER.error("Failed to discover facets for project: {}", getPathString(), ex);
            return null;
        });
    }

    @Override
    public boolean hasFacet(FacetType<?> type) {
        for (Facet<?> facet : facets) {
            if (facet.getType().equals(type))
                return true;
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D> Optional<Facet<D>> getFacet(FacetType<D> type) {
        for (Facet<?> facet : facets) {
            if (facet.getType().equals(type))
                return Optional.of((Facet<D>) facet);
        }

        return Optional.empty();
    }

    @Override
    public Path getPath() {
        return this.path.get();
    }

    @Override
    public void open(@Nullable Stage stage) {
        Project currentProject = Railroad.PROJECT_MANAGER.getOpenProject();
        if (currentProject != null && !ProjectPathIdentityUtils.matches(currentProject.getPath(), getPath())) {
            currentProject.close();
        }

        Railroad.LOGGER.debug("Opening project: {}", getPathString());
        setLastOpened(System.currentTimeMillis());
        Project project = Railroad.PROJECT_MANAGER.updateProjectInfo(this);
        project.getGitManager().detectRepository();
        IDESetup.switchToIDE(project, stage);
        if (project instanceof RailroadProject railroadProject) {
            railroadProject.discoverFacets();
        }

        ProjectDataStore dataStore = project.getDataStore();
        ProjectConfig projectConfig = dataStore.readJson(PROJECT_CONFIG_LOCATION, ProjectConfig.class)
            .orElseGet(ProjectConfig::new);
        List<Path> openDocumentPaths = projectConfig.getOpenDocuments();
        Path activeDocumentPath = projectConfig.getActiveDocument();
        List<EditorTabSessionState> editorTabs = projectConfig.getEditorTabs();
        EditorWorkspaceSessionState editorWorkspace = projectConfig.getEditorWorkspace();
        Platform.runLater(() -> {
            if (editorWorkspace != null && editorWorkspace.isSupported()) {
                Railroad.LOGGER.debug("Restoring complete editor workspace with {} tabs for project {}",
                    editorWorkspace.tabs().size(), project.getPathString());
                Services.EDITOR_TAB_MANAGER.restoreWorkspaceSession(editorWorkspace);
            } else if (editorTabs == null) {
                Railroad.LOGGER.debug("Restoring {} legacy editor tabs for project {}",
                    openDocumentPaths == null ? 0 : openDocumentPaths.size(),
                    project.getPathString());
                Services.EDITOR_TAB_MANAGER.restore(
                    openDocumentPaths == null ? List.of() : openDocumentPaths,
                    activeDocumentPath);
            } else {
                Railroad.LOGGER.debug("Restoring {} editor tabs for project {}", editorTabs.size(),
                    project.getPathString());
                Services.EDITOR_TAB_MANAGER.restoreSession(editorTabs);
            }
            Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
                .ifPresent(idePane -> idePane.restoreLayoutState(projectConfig.getIdeLayoutState()));
        });
    }

    @Override
    public void close() {
        Railroad.LOGGER.debug("Closing project: {}", getPathString());
        Project currentProject = Railroad.PROJECT_MANAGER.getOpenProject();
        if (currentProject != null && ProjectPathIdentityUtils.matches(currentProject.getPath(), getPath())) {
            Document activeDocument = Services.IDE_STATE.getActiveDocument();

            ProjectDataStore dataStore = getDataStore();
            ProjectConfig projectConfig = dataStore.readJson(PROJECT_CONFIG_LOCATION, ProjectConfig.class)
                .orElseGet(ProjectConfig::new);
            EditorWorkspaceSessionState editorWorkspace = Services.EDITOR_TAB_MANAGER.captureWorkspaceSession();
            List<EditorTabSessionState> editorTabs = editorWorkspace.tabs();
            Railroad.LOGGER.debug("Persisting {} editor tabs for project {}", editorTabs.size(), getPathString());
            projectConfig.setEditorWorkspace(editorWorkspace);
            projectConfig.setEditorTabs(editorTabs);
            projectConfig.setOpenDocuments(editorTabs.stream()
                .map(EditorTabSessionState::path)
                .filter(Objects::nonNull)
                .toList());
            projectConfig.setActiveDocument(activeDocument == null
                ? null
                : activeDocument.getUri().filePath().orElse(null));
            Services.UI_MANAGER.lookup(UIIds.IDE.IDE)
                .map(IDEPane::captureLayoutState)
                .ifPresent(projectConfig::setIdeLayoutState);
            dataStore.writeJson(PROJECT_CONFIG_LOCATION, projectConfig);

            Railroad.PROJECT_MANAGER.setCurrentProject(null);
        }
    }

    @Override
    public String toString() {
        return alias.get() + " - " + lastOpened.get();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (obj == null || getClass() != obj.getClass())
            return false;

        RailroadProject project = (RailroadProject) obj;
        return ProjectPathIdentityUtils.matches(getPath(), project.getPath());
    }

    @Override
    public int hashCode() {
        return ProjectPathIdentityUtils.key(getPath()).hashCode();
    }

    @Override
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
        json.addProperty("Description", description.get());
        json.addProperty("License", license.get() != null ? license.get().getSpdxId() : null);

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
                    this.path.set(ProjectPathIdentityUtils.normalize(Path.of(pathElement.getAsString())));
                } else if (pathPrimitive.isNumber()) {
                    try {
                        this.path
                            .set(ProjectPathIdentityUtils
                                .normalize(Path.of(String.valueOf(pathPrimitive.getAsNumber()))));
                    } catch (Exception exception) {
                        Railroad.LOGGER.warn("Project JSON 'Path' is not a valid path: {}", pathElement, exception);
                    }
                } else {
                    Railroad.LOGGER.warn("Project JSON 'Path' is not a string or number: {}", pathElement);
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'Path' is not a string: {}", pathElement);
            }
        }

        if (json.has("Alias")) {
            JsonElement aliasElement = json.get("Alias");
            if (aliasElement.isJsonPrimitive()) {
                JsonPrimitive aliasPrimitive = aliasElement.getAsJsonPrimitive();
                if (aliasPrimitive.isString()) {
                    this.alias.set(aliasElement.getAsString());
                } else if (aliasPrimitive.isNumber()) {
                    this.alias.set(String.valueOf(aliasPrimitive.getAsNumber()));
                } else {
                    Railroad.LOGGER.warn("Project JSON 'Alias' is not a string or number: {}", aliasElement);
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'Alias' is not a string: {}", aliasElement);
            }
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
                        Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a valid number: {}", lastOpenedElement,
                            exception);
                    }
                } else {
                    Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a number or string: {}", lastOpenedElement);
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'LastOpened' is not a primitive: {}", lastOpenedElement);
            }
        }

        if (json.has("Id")) {
            JsonElement idElement = json.get("Id");
            if (idElement.isJsonPrimitive()) {
                JsonPrimitive idPrimitive = idElement.getAsJsonPrimitive();
                if (idPrimitive.isString()) {
                    this.id.set(idElement.getAsString());
                } else if (idPrimitive.isNumber()) {
                    this.id.set(String.valueOf(idPrimitive.getAsNumber()));
                } else {
                    Railroad.LOGGER.warn("Project JSON 'Id' is not a string or number: {}", idElement);
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'Id' is not a string: {}", idElement);
            }
        }

        boolean hasIcon = false;
        if (json.has("Icon")) {
            JsonElement iconElement = json.get("Icon");
            if (iconElement.isJsonPrimitive()) {
                JsonPrimitive iconPrimitive = iconElement.getAsJsonPrimitive();
                if (iconPrimitive.isString() && !iconElement.getAsString().isBlank()) {
                    this.icon.set(new Image(iconElement.getAsString()));
                    hasIcon = true;
                } else if (!iconPrimitive.isString()) {
                    Railroad.LOGGER.warn("Project JSON 'Icon' is not a string: {}", iconElement);
                }
            } else if (iconElement.isJsonNull()) {
                Railroad.LOGGER.warn("Project JSON 'Icon' is null, using default icon.");
            } else {
                Railroad.LOGGER.warn("Project JSON 'Icon' is not a primitive: {}", iconElement);
            }
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
                                } else {
                                    Railroad.LOGGER.warn("Invalid project facet type: {}", typeId);
                                }
                            } else {
                                Railroad.LOGGER.warn("Invalid project facet JSON: {}", facetJson);
                            }
                        } else {
                            Railroad.LOGGER.warn("Project facet JSON missing 'Type' or 'Data': {}", facetJson);
                        }
                    } else {
                        Railroad.LOGGER.warn("Invalid project facet JSON element: {}", facetElement);
                    }
                }
            } else {
                Railroad.LOGGER.warn("Project facets JSON is not an array: {}", facetsElement);
            }
        }

        if (json.has("Description")) {
            JsonElement descriptionElement = json.get("Description");
            if (descriptionElement.isJsonPrimitive()) {
                JsonPrimitive descriptionPrimitive = descriptionElement.getAsJsonPrimitive();
                if (descriptionPrimitive.isString()) {
                    this.description.set(descriptionElement.getAsString());
                } else if (descriptionPrimitive.isNumber()) {
                    this.description.set(String.valueOf(descriptionPrimitive.getAsNumber()));
                } else {
                    Railroad.LOGGER.warn("Project JSON 'Description' is not a string or number: {}",
                        descriptionElement);
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'Description' is not a primitive: {}", descriptionElement);
            }
        }

        if (json.has("License")) {
            JsonElement licenseElement = json.get("License");
            if (licenseElement.isJsonPrimitive()) {
                JsonPrimitive licensePrimitive = licenseElement.getAsJsonPrimitive();
                if (licensePrimitive.isString()) {
                    License license = License.fromSpdxId(licenseElement.getAsString());
                    if (license != null) {
                        this.license.set(license);
                    } else {
                        Railroad.LOGGER.warn("Project JSON 'License' has invalid SPDX ID: {}",
                            licenseElement.getAsString());
                    }
                }
            } else {
                Railroad.LOGGER.warn("Project JSON 'License' is not a primitive: {}", licenseElement);
            }
        }

        if (!hasIcon) {
            this.icon.set(createIcon());
        }
    }

    @Override
    public String getAlias() {
        return alias.get();
    }

    @Override
    public void setAlias(@NotNull String alias) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        if (alias.isBlank())
            throw new IllegalArgumentException("Alias cannot be blank");

        String originalAlias = this.alias.get();
        this.alias.set(alias);
        Railroad.EVENT_BUS.publish(new ProjectAliasChangedEvent(this, originalAlias, alias));
    }

    @Override
    public long getLastOpened() {
        return lastOpened.get();
    }

    @Override
    public void setLastOpened(long lastOpened) {
        this.lastOpened.set(lastOpened);
    }

    @Override
    public String getDescription() {
        return description.get();
    }

    @Override
    public void setDescription(String description) {
        this.description.set(description);
    }

    @Override
    public License getLicense() {
        return license.get();
    }

    @Override
    public void setLicense(License license) {
        this.license.set(license);
    }

    @Override
    public Image getIcon() {
        return icon.get();
    }

    @Override
    public void setIcon(Image icon) {
        this.icon.set(icon == null ? createIcon() : icon);
    }

    /**
     * Returns the observable property containing the project icon.
     *
     * @return the project icon property
     */
    public ObjectProperty<Image> iconProperty() {
        return icon;
    }

    /**
     * Returns an observable read-only view of the project's facets.
     *
     * @return the project facets property
     */
    public SetProperty<Facet<?>> facetsProperty() {
        return new ReadOnlySetWrapper<>(facets);
    }

    /**
     * Returns a snapshot of the facets currently associated with the project.
     *
     * @return the project's facets
     */
    public List<Facet<?>> getFacets() {
        return List.copyOf(facets);
    }

    /**
     * Returns the observable property containing the project alias.
     *
     * @return the project alias property
     */
    public StringProperty aliasProperty() {
        return alias;
    }

    /**
     * Returns the observable property containing the project's repository.
     *
     * @return the project repository property
     */
    public ObjectProperty<Repository> repositoryProperty() {
        return repository;
    }

    /**
     * Returns the observable property containing the last-opened timestamp.
     *
     * @return the last-opened property
     */
    public LongProperty lastOpenedProperty() {
        return lastOpened;
    }

    /**
     * Starts an asynchronous project build using the supplied JDK.
     *
     * <p>
     * The returned future completes with a task that can be used to observe
     * or control the build when the project's build facet supports it.
     * </p>
     *
     * @param jdk the JDK to use for the build
     * @return a future completed with the build task, or exceptionally when the project cannot be built
     */
    public CompletableFuture<Runnable> build(JDK jdk) {
        CompletableFuture<Runnable> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            if (hasFacet(FacetManager.GRADLE)) {
                gradleManager.runBuildTaskAsync("build", jdk, future);
            } else if (hasFacet(FacetManager.MAVEN)) {
                // TODO: Implement Maven build support
                future.completeExceptionally(
                    new UnsupportedOperationException("Maven build support is not implemented yet."));
            } else {
                future.completeExceptionally(new IllegalStateException("Project does not have a build facet."));
            }
        });

        return future;
    }
}
