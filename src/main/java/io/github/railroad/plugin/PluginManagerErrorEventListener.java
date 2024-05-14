package io.github.railroad.plugin;

import java.util.EventListener;

public interface PluginManagerErrorEventListener extends EventListener {
    void onPluginManagerError(PluginManagerErrorEvent event);
}
