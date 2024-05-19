package io.github.railroad.plugin;

public class PluginHealthChecker extends Thread {
    private final Plugin plugin;

    public PluginHealthChecker(Plugin plugin) {
        this.plugin = plugin;
        setName("HC_" + plugin.getClass().getName());
    }

    @Override
    public void run() {
        // TODO: This needs redoing because currently it just spams new windows and the console
//        while (isAlive()) {
//            try {
//                if (plugin.getState() == PluginStates.ERROR_INIT) {
//                    PluginPhaseResult result = plugin.initPlugin();
//                    if (plugin.getState() != PluginStates.FINISHED_INIT) {
//                        plugin.getPluginManager().showError(plugin, result, "Failed to load plugin");
//                    }
//                }
//
//                Thread.sleep(10000);
//            } catch (InterruptedException exception) {
//                PluginPhaseResult result = new PluginPhaseResult();
//                result.addError(new Error(exception.getMessage()));
//                plugin.getPluginManager().showError(plugin, result, "Health check loop error");
//            }
//        }
    }
}
