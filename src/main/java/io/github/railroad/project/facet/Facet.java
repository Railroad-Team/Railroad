package io.github.railroad.project.facet;

import lombok.Data;

@Data
public class Facet<D> {
    private final FacetType<D> type;
    private D data;

    /**
     * Creates a new Facet instance.
     *
     * @param type the facet type
     * @param data the facet data
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
     * @param type the facet type
     */
    public Facet(FacetType<D> type) {
        this(type, null);
    }
}
