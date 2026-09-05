package dev.railroadide.railroad.project.creation.modjson;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;

/**
 * Groups Fabric entrypoints by their entrypoint key, including custom keys.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EntrypointContainer extends HashMap<String, List<Entrypoint>> {
    /**
     * Returns the common initialization entrypoints.
     *
     * @return the entrypoints under {@code main}, or {@code null} if absent
     */
    public List<Entrypoint> getMain() {
        return get("main");
    }

    /**
     * Returns the client initialization entrypoints.
     *
     * @return the entrypoints under {@code client}, or {@code null} if absent
     */
    public List<Entrypoint> getClient() {
        return get("client");
    }

    /**
     * Returns the dedicated server initialization entrypoints.
     *
     * @return the entrypoints under {@code server}, or {@code null} if absent
     */
    public List<Entrypoint> getServer() {
        return get("server");
    }
}
