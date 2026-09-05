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

    public String getId() {
        return id;
    }

    public String getLocalizationKey() {
        return localizationKey;
    }

    public Ikon getGraphic() {
        return graphic;
    }

    public String getAcceleratorId() {
        return acceleratorId;
    }

    public boolean isAvailable(Project project) {
        return project != null && availability.test(project);
    }

    public ObservableBooleanValue createUnavailableBinding(Project project) {
        return unavailableBindingFactory.apply(project);
    }

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

    public static Optional<WorkspaceMode> fromId(String id) {
        if (id == null || id.isBlank())
            return Optional.empty();

        String normalizedId = id.trim();
        WorkspaceMode stableMatch = REGISTRY.get(normalizedId);
        if (stableMatch != null)
            return Optional.of(stableMatch);

        return Optional.empty();
    }

    public static WorkspaceMode defaultMode() {
        if (REGISTRY.values().isEmpty())
            throw new IllegalStateException("No workspace modes are registered");
        return REGISTRY.values().getFirst();
    }
}
