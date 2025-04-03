package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import io.github.railroad.localization.L18n;
import javafx.scene.Node;
import javafx.scene.control.Labeled;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SearchHandler {
    private final String foundStyle = "-fx-background-color: #FF0000;";
    //A map of setting string e.g Theme, to the path e.g railroad.appearance.theme
    private final Map<String, String> settings = new HashMap<>();

    private final AtomicReference<String> query = new AtomicReference<>("");

    //TODO fuzzy search

    public SearchHandler() {
        for (Setting setting : Railroad.SETTINGS_HANDLER.getSettings().getSettings().values()) {
            var baseKey = setting.getTreeId().replace(":", ".");
            var titleKey = baseKey + ".title";
            var descKey = baseKey + ".description";

            if (L18n.isKeyValid(titleKey)) {
                settings.put(L18n.localize(titleKey), baseKey);
            }

            if (L18n.isKeyValid(descKey)) {
                settings.put(L18n.localize(descKey), baseKey);
            }
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
        for (String key : settings.keySet()) {
            if (key.toLowerCase().contains(query.toLowerCase())) {
                var parts = settings.get(key).split("[.]");
                return String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
            }
        }

        return null;
    }

    public <T extends Node> T styleNode(T n) {
        if (getQuery().isEmpty()) {
            return n;
        }
        //TODO handle other types of node, and vbox/hbox ?
        if (n instanceof Labeled) {
            if (((Labeled) n).getText().toLowerCase().contains(query.get().toLowerCase())) {
                n.setStyle(n.getStyle() + foundStyle);
                //TODO better way for this? Maybe a style class or something
                Railroad.LOGGER.debug("Text {} matches query {}", ((Labeled) n).getText(), query);
            } else {
                Railroad.LOGGER.debug("Text {} does not match query {}", ((Labeled) n).getText(), query);
                n.setStyle(n.getStyle().replace(foundStyle, ""));
            }
        }

        return n;
    }

    public <T extends Node> List<T> styleNodes(T... node) {
        if (getQuery().isEmpty()) {
            return Arrays.stream(node).toList();
        }

        List<T> result = new ArrayList<>();

        for (T n : node) {
            result.add(styleNode(n));
        }

        return result;
    }
}
