package dev.railroadide.railroad.project.facet;

import lombok.Data;

/**
 * Represents a project facet, which is a modular aspect or characteristic of a project (such as Java, Gradle, or Fabric support).
 * Each facet is associated with a {@link FacetType} and may hold additional data describing the facet.
 *
 * @param <D> the type of data associated with this facet
 */
@Data
public class Facet<D> {
    private final FacetType<D> type;
    private D data;

    /**
     * Creates a new Facet instance.
     *
     * @param type the facet type (must not be null)
     * @param data the facet data, or null if not applicable
     * @throws IllegalArgumentException if type is null
     */
    public Facet(FacetType<D> type, D data) {
        if (type == null) {
            throw new IllegalArgumentException("Facet type must not be null");
        }

        this.type = type;
        this.data = data;
    }

    /**
     * Creates a new Facet instance with no data.
     *
     * @param type the facet type (must not be null)
     * @throws IllegalArgumentException if type is null
     */
    public Facet(FacetType<D> type) {
        this(type, null);
    }
}
