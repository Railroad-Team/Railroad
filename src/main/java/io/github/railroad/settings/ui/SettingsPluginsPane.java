package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.settings.ui.plugin.PluginListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;

public class SettingsPluginsPane extends ScrollPane {
    //TODO move over to new settings system
    private final ListView<Plugin> pluginList = new ListView<>();

    public SettingsPluginsPane() {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        pluginList.setCellFactory(param -> new PluginListCell(this));
        pluginList.getItems().addAll(Railroad.PLUGIN_MANAGER.getPluginList());
        setContent(pluginList);
    }
}
