package dev.railroadide.core.project;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class License {
    public static final Registry<License> REGISTRY =  RegistryManager.createRegistry("railroad:license", License.class);

    private final String name;
    private final String url;
    private final String spdxId;
    private final String headerText;

    public static Builder builder() {
        return new Builder();
    }

    public static License fromSpdxId(String spdxId) {
        for (License license : REGISTRY.values()) {
            if (license.spdxId.equals(spdxId)) {
                return license;
            }
        }

        return null;
    }

    public static License fromName(String name) {
        for (License license : REGISTRY.values()) {
            if (license.name.equals(name)) {
                return license;
            }
        }

        return null;
    }

    public static class Builder {
        private String name;
        private String url;
        private String spdxId;
        private String headerText;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder spdxId(String spdxId) {
            this.spdxId = spdxId;
            return this;
        }

        public Builder headerText(String headerText) {
            this.headerText = headerText;
            return this;
        }

        public License build() {
            return new License(name, url, spdxId, headerText);
        }
    }
}
