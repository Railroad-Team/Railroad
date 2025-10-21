package dev.railroadide.core.switchboard;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;

/**
 * Marker interface for repositories that interact with the Switchboard metadata service.
 */
public interface SwitchboardRepository {
    Registry<SwitchboardRepository> REGISTRY = RegistryManager.createRegistry("railroad:switchboard_repository", SwitchboardRepository.class);
}
