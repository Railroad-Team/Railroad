package dev.railroadide.railroad.project.facet;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.facet.data.FabricFacetData;
import dev.railroadide.railroad.project.facet.data.GradleFacetData;
import dev.railroadide.railroad.project.facet.data.JavaFacetData;
import dev.railroadide.railroad.project.facet.data.MavenFacetData;
import dev.railroadide.railroad.project.facet.detector.FabricFacetDetector;
import dev.railroadide.railroad.project.facet.detector.GradleFacetDetector;
import dev.railroadide.railroad.project.facet.detector.JavaFacetDetector;
import dev.railroadide.railroad.project.facet.detector.MavenFacetDetector;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Utility class for managing project facets and their detection.
 * <p>
 * The FacetManager is responsible for registering facet types and detectors, scanning projects for applicable facets,
 * and providing access to all known facet types and detectors. It is not instantiable.
 * </p>
 */
public class FacetManager {
    private static final Map<String, FacetType<?>> TYPES = new ConcurrentHashMap<>();
    private static final List<FacetDetector<?>> DETECTORS = new CopyOnWriteArrayList<>();
    /**
     * The facet type for Java language support.
     */
    public static final FacetType<JavaFacetData> JAVA = registerFacet(
        new FacetType.Builder<>("java", JavaFacetData.class)
            .name("Java")
            .description("Java programming language support")
            .build(),
        new JavaFacetDetector()
    );
    /**
     * The facet type for Gradle build system support.
     */
    public static final FacetType<GradleFacetData> GRADLE = registerFacet(
        new FacetType.Builder<>("gradle", GradleFacetData.class)
            .name("Gradle")
            .description("Gradle build system support")
            .build(),
        new GradleFacetDetector()
    );
    /**
     * The facet type for Maven build system support.
     */
    public static final FacetType<MavenFacetData> MAVEN = registerFacet(
        new FacetType.Builder<>("maven", MavenFacetData.class)
            .name("Maven")
            .description("Maven build system support")
            .build(),
        new MavenFacetDetector()
    );
    /**
     * The facet type for Fabric modding platform support.
     */
    public static final FacetType<FabricFacetData> FABRIC = registerFacet(
        new FacetType.Builder<>("fabric", FabricFacetData.class)
            .name("Fabric")
            .description("Fabric modding platform support")
            .build(),
        new FabricFacetDetector()
    );

    private FacetManager() {
        throw new UnsupportedOperationException("FacetManager is a utility class and cannot be instantiated");
    }

    /**
     * Registers a new facet type.
     *
     * @param type the facet type to register
     */
    public static <D> FacetType<D> registerType(FacetType<D> type) {
        if (type == null || type.id() == null || type.id().isEmpty())
            throw new IllegalArgumentException("Facet type and its ID must not be null or empty");

        if (TYPES.containsKey(type.id()))
            throw new IllegalArgumentException("Facet type with ID '" + type.id() + "' is already registered");

        TYPES.put(type.id(), type);
        return type;
    }

    /**
     * Retrieves a facet type by its ID.
     *
     * @param id the ID of the facet type
     * @return the facet type, or null if not found
     */
    public static FacetType<?> getType(String id) {
        if (id == null || id.isEmpty())
            throw new IllegalArgumentException("Facet type ID must not be null or empty");

        return TYPES.get(id);
    }

    /**
     * Registers a new facet detector.
     *
     * @param detector the facet detector to register
     */
    public static void registerDetector(FacetDetector<?> detector) {
        if (detector == null) {
            throw new IllegalArgumentException("Facet detector must not be null");
        }

        DETECTORS.add(detector);
    }

    /**
     * Retrieves all registered facet detectors.
     *
     * @return a list of registered facet detectors
     */
    public static List<FacetDetector<?>> getDetectors() {
        return List.copyOf(DETECTORS);
    }

    /**
     * Retrieves all registered facet types.
     *
     * @return a list of facet types, keyed by their IDs
     */
    public static List<FacetType<?>> getTypes() {
        return List.copyOf(TYPES.values());
    }

    public static <D> FacetType<D> registerFacet(@NotNull FacetType<D> facetType, @NotNull FacetDetector<D> detector) {
        if (facetType == null)
            throw new IllegalArgumentException("Facet type must not be null");
        if (detector == null)
            throw new IllegalArgumentException("Facet detector must not be null");

        FacetType<D> registered = registerType(facetType);
        registerDetector(detector);

        return registered;
    }

    public static CompletableFuture<Collection<Facet<?>>> scan(@NotNull Project project) {
        if (project == null)
            throw new IllegalArgumentException("Project must not be null");

        return scan(project.getPath());
    }

    public static CompletableFuture<Collection<Facet<?>>> scan(@NotNull Path projectPath) {
        if (projectPath == null)
            throw new IllegalArgumentException("Project path must not be null");

        if (Files.notExists(projectPath))
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);

        if (!Files.isDirectory(projectPath))
            throw new IllegalArgumentException("Project path must be a directory: " + projectPath);

        return CompletableFuture.supplyAsync(() -> {
            Set<Facet<?>> facets = DETECTORS.stream()
                .map(detector -> detector.detect(projectPath))
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

            if (facets.isEmpty()) {
                Railroad.LOGGER.warn("No facets detected for project at {}", projectPath);
            } else {
                Railroad.LOGGER.info("Detected {} facets for project at {}", facets.size(), projectPath);
            }

            return facets;
        });
    }
}
