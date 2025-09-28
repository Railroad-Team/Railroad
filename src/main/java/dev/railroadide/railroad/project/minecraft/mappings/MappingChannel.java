package dev.railroadide.railroad.project.minecraft.mappings;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class MappingChannel {
    private final String id;
    private final String translationKey;
    private final Function<MinecraftVersion, List<String>> versionLister;

    public String id() {
        return id;
    }

    public String translationKey() {
        return translationKey;
    }

    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        try {
            return versionLister.apply(minecraftVersion);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to list versions for mapping channel {} and Minecraft version {}", id, minecraftVersion.id(), exception);
            return Collections.emptyList();
        }
    }

    public boolean supports(MinecraftVersion minecraftVersion) {
        return !listVersionsFor(minecraftVersion).isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String translationKey;
        private Function<MinecraftVersion, List<String>> versionLister;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder translationKey(String translationKey) {
            this.translationKey = translationKey;
            return this;
        }

        public Builder versionLister(Function<MinecraftVersion, List<String>> versionLister) {
            this.versionLister = versionLister;
            return this;
        }

        public MappingChannel build(String id) {
            this.id = id;

            return build();
        }

        public MappingChannel build() {
            if (id == null || id.isBlank())
                throw new IllegalStateException("id must be set");

            if (translationKey == null || translationKey.isBlank()) {
                translationKey = "railroad.mapping_channel." + id;
            }

            if (versionLister == null)
                throw new IllegalStateException("versionLister must be set");

            return new MappingChannel(id, translationKey, versionLister);
        }
    }
}
