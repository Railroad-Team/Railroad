package dev.railroadide.railroad.project.minecraft;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A named source of Minecraft mappings with a provider for the available mapping versions.
 */
@ToString
@EqualsAndHashCode
@AllArgsConstructor
public class MappingChannel {
    /**
     * Ordered registry of available mapping channels.
     */
    public static final Registry<MappingChannel> REGISTRY = RegistryManager
        .createOrderedRegistry("railroad:mapping_channel", MappingChannel.class);

    private final String id;
    private final String translationKey;
    private final Function<MinecraftVersion, List<String>> versionLister;

    /**
     * Returns the identifier of this mapping channel.
     *
     * @return the channel identifier
     */
    public String id() {
        return id;
    }

    /**
     * Returns the translation key for the channel display name.
     *
     * @return the display name translation key
     */
    public String translationKey() {
        return translationKey;
    }

    /**
     * Queries the mapping version provider, logging failures and treating them as an empty result.
     *
     * @param minecraftVersion the Minecraft version to query
     * @return the provider's mapping versions, or an empty list if the provider fails
     */
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        try {
            return versionLister.apply(minecraftVersion);
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to list versions for mapping channel {} and Minecraft version {}", id,
                minecraftVersion.id(), exception);
            return Collections.emptyList();
        }
    }

    /**
     * Checks whether the provider supplies any mappings for a Minecraft version.
     *
     * @param minecraftVersion the Minecraft version to query
     * @return true if at least one mapping version is available
     */
    public boolean supports(MinecraftVersion minecraftVersion) {
        return !listVersionsFor(minecraftVersion).isEmpty();
    }

    /**
     * Starts a builder for a mapping channel.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds mapping channels with a required identifier and version provider.
     */
    public static class Builder {
        private String id;
        private String translationKey;
        private Function<MinecraftVersion, List<String>> versionLister;

        /**
         * Sets the mapping channel identifier.
         *
         * @param id the nonblank channel identifier
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the translation key for the channel display name.
         *
         * @param translationKey the key, or null or blank to derive it from the identifier when built
         * @return this builder
         */
        public Builder translationKey(String translationKey) {
            this.translationKey = translationKey;
            return this;
        }

        /**
         * Sets the provider that lists mappings available for each Minecraft version.
         *
         * @param versionLister the mapping version provider
         * @return this builder
         */
        public Builder versionLister(Function<MinecraftVersion, List<String>> versionLister) {
            this.versionLister = versionLister;
            return this;
        }

        /**
         * Replaces the identifier and builds the mapping channel.
         *
         * @param id the nonblank channel identifier
         * @return the configured channel
         * @throws IllegalStateException if the identifier is blank or the version provider is missing
         */
        public MappingChannel build(String id) {
            this.id = id;

            return build();
        }

        /**
         * Builds a channel, defaulting the translation key to {@code railroad.mapping_channel.} plus its identifier.
         *
         * @return the configured channel
         * @throws IllegalStateException if the identifier is missing or blank, or the version provider is missing
         */
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
