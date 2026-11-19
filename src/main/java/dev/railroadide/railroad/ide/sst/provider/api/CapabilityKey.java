package dev.railroadide.railroad.ide.sst.provider.api;

import java.util.Objects;

/**
 * Identifies an optional service by a namespaced name and its public contract.
 *
 * @param <T> public contract type of the service
 * @param id namespaced capability identity
 * @param type runtime class of the service contract
 */
public record CapabilityKey<T>(String id, Class<T> type) {
    /**
     * Creates a typed capability key with a validated namespaced identity.
     *
     * @param id namespaced capability identity
     * @param type runtime class of the service contract
     * @throws IllegalArgumentException if the identity is not a lowercase namespace and name separated by a colon
     */
    public CapabilityKey {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (!id.matches("[a-z0-9_.-]+:[a-z0-9_.-]+"))
            throw new IllegalArgumentException("Capability ID must be namespaced: " + id);
    }
}
