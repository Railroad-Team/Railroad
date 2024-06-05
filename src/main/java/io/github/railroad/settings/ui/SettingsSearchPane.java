package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import io.github.railroad.settings.ui.general.SettingsGeneralPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedButton;
import io.github.railroad.ui.localized.LocalizedLabel;
import io.github.railroad.utility.localization.L18n;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class SettingsSearchPane extends RRVBox {
    private final AtomicReference<String> search = new AtomicReference<>("");
    private final Map<String, Map.Entry<String, SettingsCategory>> settingsMap = new HashMap<>();

    private Node pane;

    public SettingsSearchPane() {
        settingsMap.putAll(registerSettings());
    }

    public void updateSearch(String newSearch) {
        search.set(newSearch);
        refreshSearch();
    }

    public void refreshSearch() {
        var settingLIST = searchSettings(search.get());

        getChildren().clear();

        if(settingLIST.isEmpty() || settingLIST.getFirst().getValue().getValue() == SettingsCategory.PLUGINS) {
            getChildren().add(new LocalizedLabel("railroad.home.settings.search.notfound"));
            return;
        }

        switch (settingLIST.getFirst().getValue().getValue()) {
            case GENERAL -> pane = new SettingsGeneralPane();
            case APPEARANCE -> pane = new SettingsAppearancePane();
            case BEHAVIOR -> pane = new SettingsBehaviorPane();
            case KEYMAPS -> pane = new SettingsKeymapsPane();
            case PLUGINS -> pane = new SettingsPluginsPane();
            case PROJECTS -> pane = new SettingsProjectsPane();
            case TOOLS -> pane = new SettingsToolsPane();
            default -> throw new IllegalStateException("Unexpected Value: " + settingLIST.getFirst());
        }

        highlightSetting(settingLIST.getFirst().getValue().getKey());
        getChildren().add(pane);
    }

    public List<Pair<String, Map.Entry<String, SettingsCategory>>> searchSettings(String search) {
        var finishedList = new ArrayList<Pair<String, Map.Entry<String, SettingsCategory>>>();

        settingsMap.forEach((key, value) -> {
            if(key.toLowerCase().startsWith(search.toLowerCase())) {
                finishedList.addFirst(new Pair<>(key, value));
            } else if(key.toLowerCase().contains(search.toLowerCase())) {
                finishedList.add(new Pair<>(key, value));
            }
        });

        return finishedList;
    }

    private void highlightSetting(String setting) {
        ((Pane) pane).getChildren().forEach(child -> {
            if (child instanceof LocalizedButton || child instanceof LocalizedLabel) {
                if (Objects.equals(getKey(child), setting)) {
                    child.setStyle(child.getStyle() + "-fx-background-color: -color-success-5;");
                }
            } else if (child instanceof Pane) {
                ((Pane) child).getChildren().forEach(childBox -> {
                    if (childBox instanceof LocalizedButton || childBox instanceof LocalizedLabel) {
                        if (Objects.equals(getKey(childBox), setting)) {
                            childBox.setStyle(childBox.getStyle() + "-fx-background-color: -color-success-5;");
                        }
                    }
                });
            }
        });
    }

    private String getKey(Node node) {
        if (node instanceof LocalizedButton) {
            return ((LocalizedButton) node).getKey();
        } else if (node instanceof LocalizedLabel) {
            return ((LocalizedLabel) node).getKey();
        } else {
            return null;
        }
    }

    private Map<String, Map.Entry<String, SettingsCategory>> registerSettings() {
        var map = new HashMap<String, Map.Entry<String, SettingsCategory>>();
        var cache = L18n.getLangCache();

        cache.forEach((key, value) -> {
            var locKeyArr = key.toString().split("[.\\s]");
            if(key.toString().startsWith("railroad.home.settings")) {
                var category = SettingsCategory.fromName(locKeyArr[3]);
                if(category != null) {
                    map.put(value.toString(), Map.entry(key.toString(), category));
                }
            }
        });

        return map;
    }
}