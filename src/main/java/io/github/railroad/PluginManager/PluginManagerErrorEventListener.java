package io.github.railroad.PluginManager;

import java.util.EventListener;

public interface PluginManagerErrorEventListener extends EventListener {
    void onPluginManagerError(PluginManagerErrorEvent event);
}
