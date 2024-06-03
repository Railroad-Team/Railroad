package io.github.railroad.settings.ui;

import io.github.railroad.settings.SettingsCategory;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.railroad.Railroad.LOGGER;

public class SettingsSearchPane extends RRVBox {
    private final AtomicReference<String> search = new AtomicReference<>("");
    public final List<Pair<String, SettingsCategory>> settingsList = new ArrayList<>();

    private final RRListView<Object> settings = new RRListView<>();

    public SettingsSearchPane() {
        var title = new LocalizedLabel("railroad.home.settings.behavior");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        settingsList.add(new Pair<>("select a theme", SettingsCategory.APPEARANCE));
        settingsList.add(new Pair<>("download theme", SettingsCategory.APPEARANCE));
        settingsList.add(new Pair<>("language", SettingsCategory.GENERAL));
        settingsList.add(new Pair<>("plugins", SettingsCategory.PLUGINS));
        settingsList.add(new Pair<>("discord", SettingsCategory.PLUGINS));
        settingsList.add(new Pair<>("project", SettingsCategory.PROJECTS));
        settingsList.add(new Pair<>("key", SettingsCategory.KEYMAPS));
        settingsList.add(new Pair<>("action", SettingsCategory.KEYMAPS));
        settingsList.add(new Pair<>("general", SettingsCategory.GENERAL));

        settings.getItems().setAll(settingsList);

        setPadding(new Insets(10));
        setSpacing(10);

        getChildren().addAll(title, settings);
    }

    public void updateSearch(String newSearch) {
        LOGGER.info("Update search: {}", newSearch);
        search.set(newSearch);
        refreshSearch();
    }

    public void refreshSearch() {
        settings.getItems().clear();
        settings.getItems().addAll(settingsList.stream().filter(item -> item.getKey().toLowerCase().contains(search.get())).toList());
    }

    public List<Pair<String, SettingsCategory>> searchSettings(String search) {
        var finishedList = new ArrayList<Pair<String, SettingsCategory>>();

        finishedList.addAll(settingsList.stream().filter(
                i -> i.getKey()
                        .toLowerCase()
                        .contains(search)).toList()
        );

        return finishedList;
    }
}
