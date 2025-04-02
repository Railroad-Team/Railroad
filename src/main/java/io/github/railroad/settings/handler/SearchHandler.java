package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import javafx.scene.Node;
import javafx.scene.control.Label;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class SearchHandler {
    //A map of setting string e.g Theme, to the path e.g railroad.appearance.theme
    private final Map<String, String> settings = new HashMap<>();

    private final AtomicReference<String> query = new AtomicReference<>("");

    public SearchHandler() {
        settings.put("theme", "railroad:appearance.themes.select");
        settings.put("language", "railroad:appearance.language.language");
    }

    public void setQuery(String input) {
        query.set(input);
    }

    public String getQuery() {
        return query.get();
    }

    public String mostRelevantFolder(String query) {
        if (settings.get(query.toLowerCase()) != null) {
            var parts = settings.get(query.toLowerCase()).split("[:.]");
            return parts[parts.length - 3];
        } else {
            return null;
        }
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
