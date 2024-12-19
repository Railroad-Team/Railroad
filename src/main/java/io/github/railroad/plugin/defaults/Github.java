package io.github.railroad.plugin.defaults;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.config.PluginSettings;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.plugin.PluginPhaseResult;
import io.github.railroad.plugin.PluginState;
import io.github.railroad.plugin.defaults.github.ui.GithubAccounts;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.connections.Profile;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class Github extends Plugin {
    @Override
    public PluginPhaseResult init() {
        setName("Github");
        var phaseResult = new PluginPhaseResult();
        try {
            //GithubSettings settings = ConfigHandler.getConfig().getSettings().getPluginSettings("Github", GithubSettings.class);
            //for (Profile profile : settings.getAccounts()) {
            //    Railroad.LOGGER.info("Adding Github connection for {}", profile.getUsername());
            //    Railroad.REPOSITORY_MANAGER.addConnection(new GithubConnection(profile));
            //}

            updateStatus(PluginState.FINISHED_INIT);
        } catch (Exception exception) {
            phaseResult.addError(new Error(exception.getMessage()));
        }

        return phaseResult;
    }

    @Override
    public PluginPhaseResult load() {
        updateStatus(PluginState.LOADED);
        return new PluginPhaseResult();
    }

    @Override
    public PluginPhaseResult unload() {
        return null;
    }

    @Override
    public PluginPhaseResult railroadActivityChange(RailroadActivities.RailroadActivityTypes railroadActivityTypes, Object... data) {
        return null;
    }

    @Override
    public PluginPhaseResult reload() {
        return null;
    }

    @Override
    public RRVBox showSettings() {
        return new GithubAccounts();
    }

    @Override
    public PluginSettings createSettings() {
        return new GithubSettings();
    }

    @Getter
    public static class GithubSettings implements PluginSettings {
        private final ObservableList<Profile> accounts = FXCollections.observableArrayList();

        @Override
        public JsonObject toJson() {
            var json = new JsonObject();

            var accountsArray = new JsonArray();
            for (Profile account : accounts) {
                accountsArray.add(account.toJson());
            }

            json.add("Accounts", accountsArray);
            return json;
        }

        @Override
        public void fromJson(JsonObject json) {
            List<Profile> accounts = new ArrayList<>();

            JsonElement accountsElement = json.get("Accounts");
            if (accountsElement != null) {
                JsonArray accountsArray = accountsElement.getAsJsonArray();
                for (JsonElement accountElement : accountsArray) {
                    if (!accountElement.isJsonObject())
                        continue;

                    var profile = new Profile();
                    profile.fromJson(accountElement.getAsJsonObject());
                    accounts.add(profile);
                }
            }

            this.accounts.setAll(accounts);
        }
    }
}
