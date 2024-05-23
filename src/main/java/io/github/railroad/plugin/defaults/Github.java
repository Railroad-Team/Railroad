package io.github.railroad.plugin.defaults;

import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.plugin.PluginPhaseResult;
import io.github.railroad.plugin.defaults.github.ui.GithubAccounts;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;

public class Github extends Plugin {
    @Override
    public PluginPhaseResult initPlugin() {
        this.setPluiginName("Github");
        return null;
    }

    @Override
    public PluginPhaseResult loadPlugin() {
        return null;
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
        /*final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Railroad.getWindow());
        VBox dialogVbox = new VBox(20);
        dialogVbox.getChildren().add(new Text("This is a Dialog"));
        Scene dialogScene = new Scene(dialogVbox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.show();*/
        GithubAccounts accounts = new GithubAccounts();
        return accounts;
    }
}
