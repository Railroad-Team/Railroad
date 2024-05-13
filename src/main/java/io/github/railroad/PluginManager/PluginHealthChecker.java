package io.github.railroad.PluginManager;

public class PluginHealthChecker extends Thread {
    private Plugin plugin;

    public PluginHealthChecker(Plugin plugin) {
        this.plugin = plugin;
        this.setName("HC_" + plugin.getClass().getName());
    }

    public void run() {
        while (this.isAlive()) {
            try {
                if (plugin.getState() == PluginStates.ERROR_INIT) {
                    pluginPhaseResult result = plugin.initPlugin();
                    if (plugin.getState() != PluginStates.FINSIHED_INIT) {
                        plugin.getPluginManager().showError(plugin, result, "Failed to load plugin");
                    }
                }
                sleep(10000);
            } catch (InterruptedException e) {
                pluginPhaseResult result = new pluginPhaseResult();
                result.addError(new Error(e.getMessage()));
                plugin.getPluginManager().showError(plugin, result, "Health check loop error");
            }
        }
    }
}
