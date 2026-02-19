package dev.railroadide.railroad.plugin.spi.dto;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.java.JDK;
import dev.railroadide.railroad.project.License;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetType;
import dev.railroadide.railroad.utility.json.JsonSerializable;
import dev.railroadide.railroad.vcs.git.GitManager;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a project in the Railroad plugin API.
 * This interface provides methods to retrieve the project's alias, and path.
 */
public interface Project extends JsonSerializable<JsonObject> {
    /**
     * Gets the alias of the project.
     *
     * @return the alias of the project
     */
    String getAlias();

    /**
     * Gets the path of the project.
     *
     * @return the path of the project
     */
    Path getPath();

    /**
     * Sets the alias of the project.
     *
     * @param alias the new alias for the project
     */
    void setAlias(String alias);

    /**
     * Check if the project has a facet of the specified type.
     *
     * @param type The facet type to check.
     * @return True if the project has the facet, false otherwise.
     */
    boolean hasFacet(FacetType<?> type);

    /**
     * Get the facet of the specified type.
     *
     * @param type The facet type to get.
     * @param <D>  The data type of the facet.
     * @return An Optional containing the facet if found, otherwise empty.
     */
    <D> Optional<Facet<D>> getFacet(FacetType<D> type);

    /**
     * Open the project in the IDE.
     */
    void open();

    /**
     * Get the unique identifier of the project.
     *
     * @return the unique identifier of the project
     */
    String getId();

    /**
     * Get the timestamp of when the project was last opened.
     *
     * @return the timestamp of when the project was last opened
     */
    long getLastOpened();

    /**
     * Set the timestamp of when the project was last opened.
     *
     * @param timestamp the new timestamp for when the project was last opened
     */
    void setLastOpened(long timestamp);

    /**
     * Get a list of all facets associated with the project.
     *
     * @return a list of facets associated with the project
     */
    List<Facet<?>> getFacets();

    /**
     * Build the project using the specified JDK.
     *
     * @param jdk the JDK to use for building the project
     * @return a CompletableFuture that completes with a Runnable to execute after the build is finished
     */
    CompletableFuture<Runnable> build(JDK jdk);

    /**
     * Get a description of the project.
     *
     * @return a description of the project
     */
    String getDescription();

    /**
     * Set the description of the project.
     *
     * @param description the new description for the project
     */
    void setDescription(String description);

    /**
     * Get the license of the project.
     *
     * @return the license of the project
     */
    License getLicense();

    /**
     * Set the license of the project.
     *
     * @param license the new license for the project
     */
    void setLicense(License license);

    /**
     * Get the GitManager for the project.
     *
     * @return the GitManager for the project
     */
    GitManager getGitManager();

    /**
     * Get the RunConfigurationManager for the project.
     *
     * @return the RunConfigurationManager for the project
     */
    RunConfigurationManager getRunConfigManager();

    /**
     * Get the DebuggingManager for the project.
     *
     * @return the DebuggingManager for the project
     */
    DebuggingManager getDebuggingManager();

    /**
     * Get the ProjectDataStore for the project.
     *
     * @return the ProjectDataStore for the project
     */
    ProjectDataStore getDataStore();

    /**
     * Get the GradleManager for the project.
     *
     * @return the GradleManager for the project
     */
    GradleManager getGradleManager();

    /**
     * Get the icon representing the project.
     *
     * @return the icon representing the project
     */
    Image getIcon();

    /**
     * Set the icon representing the project.
     *
     * @param icon the new icon for the project
     */
    void setIcon(Image icon);

    /**
     * Get the absolute path of the project as a string.
     *
     * @return the absolute path of the project as a string
     */
    default String getPathString() {
        return getPath().toAbsolutePath().toString();
    }


}
