package io.github.railroad.core.settings;

import io.github.railroad.core.localization.LocalizationService;
import io.github.railroad.core.localization.LocalizationServiceLocator;
import io.github.railroad.core.utility.FuzzySearch;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;

import java.util.*;

/**
 * Handles searching through settings and styling nodes based on search queries.
 * It uses fuzzy search to find relevant settings based on the user's input.
 */
public class SettingsSearchHandler {
    private final FuzzySearch<Map<String, String>, String> fuzzySearch;

    private final StringProperty query = new SimpleStringProperty("");

    /**
     * Loads settings into a map of setting strings, with their text to their tree id
     *
     * @param settings The collection of settings to be indexed for search
     */
    public SettingsSearchHandler(Collection<Setting<?>> settings) {
        Map<String, String> settingsMap = new HashMap<>();

        LocalizationService localizationService = LocalizationServiceLocator.getInstance();
        for (Setting<?> setting : settings) {
            String baseKey = setting.getTreePath();
            String titleKey = baseKey + ".title";
            String descKey = baseKey + ".description";

            if (localizationService.isKeyValid(titleKey)) {
                settingsMap.put(localizationService.get(titleKey), baseKey);
            }

            if (localizationService.isKeyValid(descKey)) {
                settingsMap.put(localizationService.get(descKey), baseKey);
            }
        }

        this.fuzzySearch = new FuzzySearch<>(settingsMap, (s) -> s.keySet().stream().toList(), settingsMap::get);
    }

    /**
     * Sets the current search query.
     *
     * @param input the search query string
     */
    public void setQuery(String input) {
        query.set(input);
    }

    /**
     * Gets the current search query.
     *
     * @return the current search query string
     */
    public String getQuery() {
        return query.get();
    }

    /**
     * Returns the most relevant folder for a given query
     *
     * @param query The search query
     * @return The most relevant folder for the given query
     */
    public String mostRelevantFolder(String query) {
        String res = fuzzySearch.search(query);

        if (res == null)
            return null;

        String[] resParts = res.split("[.]");
        return String.join(".", Arrays.copyOfRange(resParts, 0, resParts.length - 1));
    }

    /**
     * Styles a node based on the search query
     *
     * @param node The node to style
     * @param <T>  The type of the node
     * @return The styled node
     */
    public <T extends Node> T styleNode(T node) {
        String query = getQuery();
        if (query.isEmpty())
            return node;

        boolean foundMatch = false;

        List<String> texts = findText(node);
        for (String text : texts) {
            if (text == null || text.isEmpty())
                continue;

            boolean similar = fuzzySearch.isSimilar(query, text);
            if (similar) {
                node.getStyleClass().add("search-highlight");
                foundMatch = true;
                break;
            }
        }

        if (!foundMatch) {
            node.getStyleClass().remove("search-highlight");
        }

        return node;
    }

    private static List<String> findText(Node node) {
        List<String> texts = new ArrayList<>();
        switch (node) {
            case Labeled labeled -> texts.add(labeled.getText());
            case Text text -> texts.add(text.getText());
            case Pane pane -> {
                for (Node child : pane.getChildren()) {
                    texts.addAll(findText(child));
                }
            }
            case null, default -> {
            } // For other node types, we can ignore them or handle them as needed
        }

        return texts;
    }

    /**
     * Styles the provided nodes
     *
     * @param nodes The nodes to style
     * @param <T>   The type of the nodes
     * @return The styled nodes
     */
    @SafeVarargs
    public final <T extends Node> List<T> styleNodes(T... nodes) {
        if (getQuery().isEmpty())
            return Arrays.asList(nodes);

        List<T> result = new ArrayList<>();

        for (T node : nodes) {
            result.add(styleNode(node));
        }

        return result;
    }
}
