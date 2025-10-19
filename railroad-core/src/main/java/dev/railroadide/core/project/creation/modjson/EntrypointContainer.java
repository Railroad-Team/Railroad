package dev.railroadide.core.project.creation.modjson;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class EntrypointContainer extends HashMap<String, List<Entrypoint>> {
    public List<Entrypoint> getMain() {
        return get("main");
    }

    public List<Entrypoint> getClient() {
        return get("client");
    }

    public List<Entrypoint> getServer() {
        return get("server");
    }
}
