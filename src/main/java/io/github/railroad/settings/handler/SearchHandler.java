package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import io.github.railroad.localization.L18n;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SearchHandler {
    //A map of setting string e.g Theme, to the path e.g railroad.appearance.theme
    private final Map<String, String> settings = new HashMap<>();

    private final AtomicReference<String> query = new AtomicReference<>("");

    //TODO fuzzy search

    public SearchHandler() {
        for (Setting setting : Railroad.SETTINGS_HANDLER.getSettings().getSettings().values()) {
            var q = L18n.localize(setting.getTreeId().replace(":", "."));
            settings.put(q, setting.getTreeId().replace(":", "."));
        }

        Railroad.LOGGER.debug("Loaded {} settings", settings);
    }

    public void setQuery(String input) {
        query.set(input);
    }

    public String getQuery() {
        return query.get();
    }

    public String mostRelevantFolder(String query) {
        Railroad.LOGGER.debug("Searching for most relevant folder for {}", query);
        for (String key : settings.keySet()) {
            //TODO turn into stream
            if (key.toLowerCase().contains(query.toLowerCase())) {
                var parts = settings.get(key).split("[.]");
                Railroad.LOGGER.debug("Found {} for {}", key, query);
                return String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
            }
        }

        return null;
    }

    public <T extends Node> boolean nodeMatches(T node) {
        if (getQuery().isEmpty()) {
            return false;
        }

        if (node instanceof Label) {
            if (((Label) node).getText().toLowerCase().contains(query.get().toLowerCase())) {
                Railroad.LOGGER.info("Found label node: {}", node);
                return true;
            }
        }

        return false;
    }

    public <T extends Node> T styleNode(T node) {
        if (node instanceof Label) {
            node.setStyle("-fx-text-fill: #FF0000;");
        }

        return node;
    }
}
