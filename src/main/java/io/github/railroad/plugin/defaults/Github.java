package io.github.railroad.plugin.defaults;

import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.plugin.PluginPhaseResult;
import io.github.railroad.plugin.PluginStates;
import io.github.railroad.plugin.defaults.github.ui.GithubAccounts;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;

public class Github extends Plugin {
    @Override
    public PluginPhaseResult initPlugin() {
        this.setPluiginName("Github");
        this.updateStatus(PluginStates.FINISHED_INIT);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult loadPlugin() {
        this.updateStatus(PluginStates.LOADED);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult unloadPlugin() {
        return null;
    }

    @Override
    public PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes) {
        return null;
    }

    @Override
    public PluginPhaseResult reloadPlugin() {
        return null;
    }

    @Override
    public ScrollPane showSettings() {
        GithubAccounts accounts = new GithubAccounts();
        return accounts;
    }
}
