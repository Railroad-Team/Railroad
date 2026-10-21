package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.beans.value.ObservableBooleanValue;
import org.kordamp.ikonli.Ikon;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Registered workspace-mode descriptor. The plugin SPI refers to modes only by stable ID. */
public final class WorkspaceMode {
    /**
     * Ordered registry of workspace mode descriptors.
     */
    public static final Registry<WorkspaceMode> REGISTRY = RegistryManager
        .createOrderedRegistry("railroad:workspace_mode", WorkspaceMode.class);

    private final String id;
    private final String localizationKey;
    private final Ikon graphic;
    private final String acceleratorId;
    private final Predicate<Project> availability;
    private final Function<Project, ObservableBooleanValue> unavailableBindingFactory;

    private WorkspaceMode(
        String id,
        String localizationKey,
        Ikon graphic,
        String acceleratorId,
        Predicate<Project> availability,
        Function<Project, ObservableBooleanValue> unavailableBindingFactory
    ) {
        this.id = id;
        this.localizationKey = localizationKey;
        this.graphic = graphic;
        this.acceleratorId = acceleratorId;
        this.availability = availability;
        this.unavailableBindingFactory = unavailableBindingFactory;
    }

    /**
     * Returns the stable workspace mode identifier.
     *
     * @return registry identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the translation key for the mode label.
     *
     * @return localization key
     */
    public String getLocalizationKey() {
        return localizationKey;
    }

    /**
     * Returns the icon used to represent this mode.
     *
     * @return mode icon
     */
    public Ikon getGraphic() {
        return graphic;
    }

    /**
     * Returns the shortcut identifier associated with this mode.
     *
     * @return accelerator identifier
     */
    public String getAcceleratorId() {
        return acceleratorId;
    }

    /**
     * Checks mode availability for a nonnull project.
     *
     * @param project project associated with the workspace
     * @return whether the project supports this mode
     */
    public boolean isAvailable(Project project) {
        return project != null && availability.test(project);
    }

    /**
     * Creates an observable unavailable state using the mode factory.
     *
     * @param project project associated with the workspace
     * @return unavailable binding, or null if the factory supplies none
     */
    public ObservableBooleanValue createUnavailableBinding(Project project) {
        return unavailableBindingFactory.apply(project);
    }

    /**
     * Validates and registers a workspace mode descriptor.
     *
     * @param id stable identifier
     * @param localizationKey translation key for the mode label
     * @param graphic mode icon
     * @param acceleratorId keyboard shortcut identifier
     * @param availability predicate deciding whether a mode can be activated
     * @param unavailableBindingFactory factory for an observable unavailable state
     * @return registered mode
     */
    public static WorkspaceMode register(
        String id,
        String localizationKey,
        Ikon graphic,
        String acceleratorId,
        Predicate<Project> availability,
        Function<Project, ObservableBooleanValue> unavailableBindingFactory
    ) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Workspace-mode ID cannot be null or blank");
        if (localizationKey == null || localizationKey.isBlank())
            throw new IllegalArgumentException("Workspace-mode localization key cannot be null or blank");
        if (availability == null)
            throw new IllegalArgumentException("Workspace-mode availability cannot be null");
        if (unavailableBindingFactory == null)
            throw new IllegalArgumentException("Workspace-mode unavailable binding factory cannot be null");

        return REGISTRY.register(id, new WorkspaceMode(
            id,
            localizationKey,
            graphic,
            acceleratorId,
            availability,
            unavailableBindingFactory));
    }

    /**
     * Looks up a registered mode after trimming its identifier.
     *
     * @param id stable identifier
     * @return matching mode, or empty for an unknown, blank, or null identifier
     */
    public static Optional<WorkspaceMode> fromId(String id) {
        if (id == null || id.isBlank())
            return Optional.empty();

        String normalizedId = id.trim();
        WorkspaceMode stableMatch = REGISTRY.get(normalizedId);
        if (stableMatch != null)
            return Optional.of(stableMatch);

        return Optional.empty();
    }

    /**
     * Returns the first mode in registry order.
     *
     * @return default mode
     */
    public static WorkspaceMode defaultMode() {
        if (REGISTRY.values().isEmpty())
            throw new IllegalStateException("No workspace modes are registered");
        return REGISTRY.values().getFirst();
    }
}
