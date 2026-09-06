package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.RunCommands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Defines how a specific category of run configuration executes run/debug/stop actions.
 * Subclasses override the lifecycle methods and consume options stored on RunConfiguration.
 *
 * @param <D> the type-specific run configuration data
 */
public abstract class RunConfigurationType<D extends RunConfigurationData> {
    /**
     * Ordered registry of available run configuration types keyed by registration ID.
     */
    public static final Registry<RunConfigurationType<?>> REGISTRY = RegistryManager
        .createOrderedRegistry("railroad:run_configuration_type", RunConfigurationType.class);

    private final String localizationKey;
    private final Ikon icon;
    private final Paint iconColor;

    protected RunConfigurationType(String localizationKey, Ikon icon, Paint iconColor) {
        this.localizationKey = requireNonBlank(localizationKey, "localizationKey");
        this.icon = Objects.requireNonNull(icon, "icon cannot be null");
        this.iconColor = Objects.requireNonNull(iconColor, "iconColor cannot be null");
    }

    /**
     * Returns the translation key for this configuration type's display name.
     *
     * @return the type-name localization key
     */
    public final String getLocalizationKey() {
        return localizationKey;
    }

    /**
     * Returns the icon used to identify this configuration type.
     *
     * @return the type icon
     */
    public final Ikon getIcon() {
        return icon;
    }

    /**
     * Returns the paint applied to this configuration type's icon.
     *
     * @return the type icon color
     */
    public final Paint getIconColor() {
        return iconColor;
    }

    /**
     * Run this configuration using the provided project and configuration options.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     * @return a future tracking the run operation and reporting execution failure
     */
    public abstract CompletableFuture<Void> run(Project project, RunConfiguration<D> configuration);

    /**
     * Debug this configuration.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     * @return a future tracking the debug operation and reporting failures or unsupported debugging
     */
    public abstract CompletableFuture<Void> debug(Project project, RunConfiguration<D> configuration);

    /**
     * Stop execution for this configuration.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     * @return a future tracking the stop request; implementations may complete it before the process exits
     */
    public abstract CompletableFuture<Void> stop(Project project, RunConfiguration<D> configuration);

    /**
     * Tests whether the given configuration is currently executing.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     * @return whether the configuration has an active execution
     */
    public abstract boolean isRunning(Project project, RunConfiguration<D> configuration);

    /**
     * Tests whether this type supports debugging the configuration; the default implementation returns false.
     *
     * @param project the project owning the configuration
     * @param configuration the run configuration to operate on
     * @return whether debugging is supported for the configuration
     */
    public boolean isDebuggingSupported(Project project, RunConfiguration<D> configuration) {
        return false;
    }

    private static String requireNonBlank(String value, String parameterName) {
        Objects.requireNonNull(value, parameterName);
        if (value.isBlank())
            throw new IllegalArgumentException(parameterName + " cannot be blank");

        return value;
    }

    /**
     * Creates the configuration's edit, pin, and delete menu; the pin action is currently a placeholder.
     *
     * @param project the project owning the configuration
     * @param runConfiguration the configuration targeted by menu actions
     * @return the newly created context menu
     */
    public ContextMenu createContextMenu(Project project, RunConfiguration<D> runConfiguration) {
        var menu = new ContextMenu();

        var editItem = new LocalizedMenuItem("railroad.run_configuration.edit");
        CommandMenuItems.bind(editItem, RunCommands.EDIT,
            () -> CommandContext.withArgument(project, null, runConfiguration));

        var pinItem = new LocalizedMenuItem("railroad.run_configuration.pin");
        pinItem.setDisable(true);

        var deleteItem = new LocalizedMenuItem("railroad.run_configuration.delete");
        CommandMenuItems.bind(deleteItem, RunCommands.DELETE,
            () -> CommandContext.withArgument(project, null, runConfiguration));

        menu.getItems().addAll(editItem, pinItem, deleteItem);
        return menu;
    }

    /**
     * Wraps type-specific options in a new run configuration with a fresh identity.
     *
     * @param data the type-specific options
     * @return the new run configuration
     */
    public RunConfiguration<D> createConfigurationInstance(D data) {
        return new RunConfiguration<>(this, data);
    }

    /**
     * Creates initial type-specific options for a project.
     *
     * @param project the project owning the configuration
     * @return the new configuration options
     */
    public abstract D createDataInstance(Project project);

    /**
     * Returns the runtime class used to deserialize this type's options.
     *
     * @return the configuration data class
     */
    public abstract Class<D> getDataClass();

    /**
     * Creates a new configuration using this type's initial options for the project.
     *
     * @param project the project owning the configuration
     * @return the newly initialized run configuration
     */
    public RunConfiguration<D> createDefaultConfiguration(Project project) {
        D data = createDataInstance(project);
        return createConfigurationInstance(data);
    }
}
