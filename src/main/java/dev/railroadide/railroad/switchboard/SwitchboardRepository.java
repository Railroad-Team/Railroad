package dev.railroadide.railroad.switchboard;

import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;

/**
 * Marker interface for repositories that interact with the Switchboard metadata service.
 */
public interface SwitchboardRepository {
    Registry<SwitchboardRepository> REGISTRY = RegistryManager.createRegistry("railroad:switchboard_repository", SwitchboardRepository.class);
}
