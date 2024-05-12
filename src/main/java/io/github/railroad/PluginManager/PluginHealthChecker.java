package io.github.railroad.PluginManager;

public class PluginHealthChecker extends Thread {
    private Plugin plugin;
    public PluginHealthChecker(Plugin plugin) {
        this.plugin = plugin;
    };

    public void run() {
        while (this.isAlive()) {
            try {
                if (plugin.getState() == PluginStates.ERROR_INIT) {
                    plugin.print("Init failed, restarting Init...");
                    PluginPhaseResult result = plugin.InitPlugin();
                    PluginManager.showError(plugin, result);
                }
                sleep(10000);
            } catch (InterruptedException e) {
                this.plugin.print("Healthcheck error:" +e);
            }
        }
    }
}
