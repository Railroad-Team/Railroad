package io.github.railroad.settings.handler;

import io.github.railroad.Railroad;
import io.github.railroad.localization.L18n;
import io.github.railroad.utility.FuzzySearch;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SearchHandler {
    private final String foundStyle = "-fx-background-color: #FF0000;";
    private final Map<String, String> settings = new HashMap<>();
    private final FuzzySearch<Map<String, String>, String> fuzzySearch;

    private final AtomicReference<String> query = new AtomicReference<>("");

    /**
     * Loads settings into a map of setting strings, with their text to their tree id
     */
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

        fuzzySearch = new FuzzySearch<>(settings, (s) -> s.keySet().stream().toList(), settings::get);
    }

    public void setQuery(String input) {
        query.set(input);
    }

    public String getQuery() {
        return query.get();
    }

    /**
     * Returns the most relevant folder for a given query
     * @param query The search query
     * @return The most relevant folder for the given query
     */
    public String mostRelevantFolder(String query) {
        var res = fuzzySearch.search(query);

        if (res == null) {
            return null;
        }

        var resParts = res.split("[:.]");
        return String.join(".", Arrays.copyOfRange(resParts, 0, resParts.length - 1));
    }

    /**
     * Styles a node based on the search query
     * @param n The node to style
     * @return The styled node
     * @param <T> The type of the node
     */
    public <T extends Node> T styleNode(T n) {
        if (getQuery().isEmpty()) {
            return n;
        }
        //IMPORTANT: Only supports a singular node per setting (so doesn't work for VBOX or HBOX)
        if (n instanceof Pane) {
            throw new IllegalArgumentException("Settings node cannot be a pane");
        }
        if (n instanceof Labeled) {
            var sr = fuzzySearch.isSimilar(getQuery(), ((Labeled) n).getText());
            if (sr) {
                //TODO better way for this? Maybe a style class or something
                n.setStyle(n.getStyle() + foundStyle);
            } else {
                n.setStyle(n.getStyle().replace(foundStyle, ""));
            }
        }

        return n;
    }

    /**
     * Styles the provided nodes
     * @param node The nodes to style
     * @return The styled nodes
     * @param <T> The type of the nodes
     */
    @SafeVarargs
    public final <T extends Node> List<T> styleNodes(T... node) {
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
