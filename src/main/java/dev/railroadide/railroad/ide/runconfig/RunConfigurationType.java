package dev.railroadide.railroad.ide.runconfig;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.core.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.project.Project;
import javafx.scene.control.ContextMenu;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Defines how a specific category of run configuration executes run/debug/stop actions.
 * Subclasses override the lifecycle methods and consume options stored on RunConfiguration.
 */
public abstract class RunConfigurationType<D extends RunConfigurationData> {
    public static final Registry<RunConfigurationType<?>> REGISTRY =
        RegistryManager.createRegistry("railroad:run_configuration_type", RunConfigurationType.class);

    private final String localizationKey;
    private final Ikon icon;
    private final Paint iconColor;

    protected RunConfigurationType(String localizationKey, Ikon icon, Paint iconColor) {
        this.localizationKey = requireNonBlank(localizationKey, "localizationKey");
        this.icon = Objects.requireNonNull(icon, "icon cannot be null");
        this.iconColor = Objects.requireNonNull(iconColor, "iconColor cannot be null");
    }

    public final String getLocalizationKey() {
        return localizationKey;
    }

    public final Ikon getIcon() {
        return icon;
    }

    public final Paint getIconColor() {
        return iconColor;
    }

    /**
     * Run this configuration using the provided project and configuration options.
     */
    public abstract CompletableFuture<Void> run(Project project, RunConfiguration<D> configuration);

    /**
     * Debug this configuration.
     */
    public abstract CompletableFuture<Void> debug(Project project, RunConfiguration<D> configuration);

    /**
     * Stop execution for this configuration.
     */
    public abstract CompletableFuture<Void> stop(Project project, RunConfiguration<D> configuration);

    public boolean isDebuggingSupported(Project project, RunConfiguration<D> configuration) {
        return false;
    }

    private static String requireNonBlank(String value, String parameterName) {
        Objects.requireNonNull(value, parameterName);
        if (value.isBlank())
            throw new IllegalArgumentException(parameterName + " cannot be blank");

        return value;
    }

    public ContextMenu createContextMenu(Project project, RunConfiguration<D> runConfiguration) {
        var menu = new ContextMenu();

        var editItem = new LocalizedMenuItem("railroad.run_configuration.edit");
        editItem.setOnAction(event -> IDESetup.showEditRunConfigurationsWindow(project, runConfiguration));

        var pinItem = new LocalizedMenuItem("railroad.run_configuration.pin");
        pinItem.setOnAction(event -> {

        });

        var deleteItem = new LocalizedMenuItem("railroad.run_configuration.delete");
        deleteItem.setOnAction(event -> project.getRunConfigManager().removeConfiguration(runConfiguration));

        menu.getItems().addAll(editItem, pinItem, deleteItem);
        return menu;
    }

    public RunConfiguration<D> createConfigurationInstance(D data) {
        return new RunConfiguration<>(this, data);
    }

    public abstract D createDataInstance(Project project);

    public abstract Class<D> getDataClass();

    public RunConfiguration<D> createDefaultConfiguration(Project project) {
        D data = createDataInstance(project);
        return createConfigurationInstance(data);
    }
}
