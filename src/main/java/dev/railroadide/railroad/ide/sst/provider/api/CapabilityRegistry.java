package dev.railroadide.railroad.ide.sst.provider.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable typed services. Absence means unsupported; duplicate names are rejected. */
public final class CapabilityRegistry {
    private final Map<String, Entry> entries;

    private CapabilityRegistry(Map<String, Entry> entries) {
        this.entries = Map.copyOf(entries);
    }

    /**
     * Starts building an immutable service registry.
     *
     * @return a new empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a registry with no supported services.
     *
     * @return an empty immutable registry
     */
    public static CapabilityRegistry empty() {
        return builder().build();
    }

    /**
     * Looks up a service by capability identity and checks its declared type.
     *
     * @param <T> public contract type of the requested service
     * @param key capability identity and expected contract type
     * @return the service, or empty if the capability identity is absent
     * @throws IllegalArgumentException if the identity exists with a different contract type
     */
    public <T> Optional<T> find(CapabilityKey<T> key) {
        Objects.requireNonNull(key, "key");
        Entry entry = entries.get(key.id());
        if (entry == null)
            return Optional.empty();
        if (!entry.key.equals(key))
            throw new IllegalArgumentException("Conflicting capability type for " + key.id());
        return Optional.of(key.type().cast(entry.value));
    }

    /**
     * Returns an immutable snapshot of the registered services for ownership/disposal.
     *
     * @return an immutable snapshot of service instances
     */
    public Collection<Object> values() {
        return entries.values().stream().map(Entry::value).toList();
    }

    /**
     * Returns the contracts exposed by this registry.
     *
     * @return an immutable set of registered capability keys
     */
    public Set<CapabilityKey<?>> keys() {
        return entries.values().stream().map(Entry::key).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Collects typed services and rejects duplicate capability identities.
     */
    public static final class Builder {
        private final Map<String, Entry> entries = new LinkedHashMap<>();

        private Builder() {
        }

        /**
         * Adds a non-null service after checking it against its contract.
         *
         * @param <T> public contract type of the service
         * @param key capability identity and contract type
         * @param value service implementing the contract
         * @return this builder
         * @throws IllegalArgumentException if the capability identity is already registered
         * @throws ClassCastException if the service does not implement the contract
         */
        public <T> Builder add(CapabilityKey<T> key, T value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            key.type().cast(value);
            if (entries.putIfAbsent(key.id(), new Entry(key, value)) != null)
                throw new IllegalArgumentException("Duplicate capability: " + key.id());
            return this;
        }

        /**
         * Takes an immutable snapshot of the collected services.
         *
         * @return a registry independent of subsequent builder changes
         */
        public CapabilityRegistry build() {
            return new CapabilityRegistry(entries);
        }
    }

    private record Entry(CapabilityKey<?> key, Object value) {
    }
}
