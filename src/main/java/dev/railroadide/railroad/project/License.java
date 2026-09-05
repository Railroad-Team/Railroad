package dev.railroadide.railroad.project;

import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Represents a project license and its associated metadata.
 * <p>
 * Licenses are registered in {@link #REGISTRY} and can be looked up by name or
 * SPDX identifier.
 */
@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class License {
    /** Registry containing the licenses available to projects. */
    public static final Registry<License> REGISTRY = RegistryManager.createOrderedRegistry("railroad:license",
        License.class);

    private final String name;
    private final String url;
    private final String spdxId;
    private final String headerText;

    /**
     * Creates a builder for constructing a license.
     *
     * @return a new license builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Finds a registered license by its SPDX identifier.
     *
     * @param spdxId the SPDX identifier to search for
     * @return the matching license, or {@code null} if no license is registered
     *         with the identifier
     */
    public static License fromSpdxId(String spdxId) {
        for (License license : REGISTRY.values()) {
            if (license.spdxId.equals(spdxId))
                return license;
        }

        return null;
    }

    /**
     * Finds a registered license by its human-readable name.
     *
     * @param name the license name to search for
     * @return the matching license, or {@code null} if no license is registered
     *         with the name
     */
    public static License fromName(String name) {
        for (License license : REGISTRY.values()) {
            if (license.name.equals(name))
                return license;
        }

        return null;
    }

    /** Builder for constructing {@link License} instances. */
    public static class Builder {
        private String name;
        private String url;
        private String spdxId;
        private String headerText;

        /**
         * Sets the human-readable name of the license.
         *
         * @param name the license name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the URL of the license text or its canonical information page.
         *
         * @param url the license URL
         * @return this builder
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the SPDX identifier of the license.
         *
         * @param spdxId the SPDX identifier
         * @return this builder
         */
        public Builder spdxId(String spdxId) {
            this.spdxId = spdxId;
            return this;
        }

        /**
         * Sets the text to include in source-file headers for the license.
         *
         * @param headerText the source-file header text
         * @return this builder
         */
        public Builder headerText(String headerText) {
            this.headerText = headerText;
            return this;
        }

        /**
         * Builds a license from the values configured on this builder.
         *
         * @return a new license
         */
        public License build() {
            return new License(name, url, spdxId, headerText);
        }
    }
}
