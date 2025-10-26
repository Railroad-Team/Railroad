package dev.railroadide.railroad.settings.keybinds;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindCategory;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.*;
public class KeybindsList extends RRVBox {
    private final Map<String, List<KeybindData>> keybinds = new LinkedHashMap<>();

    private final RRTextField searchField = new RRTextField();
    private final FlowPane categoryBar = new FlowPane();
    private final ToggleGroup categoryToggleGroup = new ToggleGroup();
    private final VBox cardsContainer = new VBox(12);
    private final LocalizedLabel emptyTitle = new LocalizedLabel("railroad.settings.keybinds.empty.title");
    private final LocalizedLabel emptySubtitle = new LocalizedLabel("railroad.settings.keybinds.empty.subtitle");
    private final VBox emptyState = new VBox(6, emptyTitle, emptySubtitle);
    private final StackPane listStack = new StackPane();

    private String activeCategoryId;
    private String pendingEditKey;
    private int pendingEditIndex = -1;

    public KeybindsList() {
        this(null);
    }

    public KeybindsList(Map<String, List<KeybindData>> initialKeybinds) {
        getStyleClass().add("keybinds-pane");
        setSpacing(18);
        setFillWidth(true);

        createHeader();
        createCategoryBar();
        createListArea();

        loadKeybinds(initialKeybinds);
    }

    public Map<String, List<KeybindData>> getKeybinds() {
        Map<String, List<KeybindData>> copy = new LinkedHashMap<>();
        keybinds.forEach((id, bindings) -> copy.put(id, new ArrayList<>(bindings)));
        return copy;
    }

    public void loadKeybinds(Map<String, List<KeybindData>> incoming) {
        keybinds.clear();

        Map<String, List<KeybindData>> defaults = KeybindHandler.getDefaults();
        defaults.forEach((id, combos) -> keybinds.put(id, new ArrayList<>(combos)));

        if (incoming != null) {
            incoming.forEach((id, combos) -> keybinds.put(id, new ArrayList<>(combos)));
        }

        rebuildCategoryFilters();
        renderKeybindCards();
    }

    private void createHeader() {
        var title = new LocalizedLabel("railroad.settings.keybinds.title");
        title.getStyleClass().add("keybinds-header-title");
        var subtitle = new LocalizedLabel("railroad.settings.keybinds.subtitle");
        subtitle.getStyleClass().add("keybinds-header-subtitle");

        searchField.setLocalizedPlaceholder("railroad.settings.keybinds.search");
        searchField.textProperty().addListener((obs, oldText, newText) -> renderKeybindCards());

        var headerBox = new VBox(4, title, subtitle);
        headerBox.getStyleClass().add("keybinds-header");

        getChildren().addAll(headerBox, searchField);
    }

    private void createCategoryBar() {
        categoryBar.getStyleClass().add("keybinds-category-bar");
        categoryBar.setHgap(8);
        categoryBar.setVgap(8);
        getChildren().add(categoryBar);
    }

    private void createListArea() {
        cardsContainer.getStyleClass().add("keybinds-card-container");

        emptyState.getStyleClass().add("keybinds-empty-state");
        emptyState.setAlignment(Pos.CENTER);

        listStack.getChildren().addAll(cardsContainer, emptyState);

        var scrollPane = new ScrollPane(listStack);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.getStyleClass().add("keybinds-scroll");

        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
    }

    private void rebuildCategoryFilters() {
        categoryBar.getChildren().clear();

        ToggleButton allButton = createCategoryChip("railroad.settings.keybinds.filter.all", null);
        categoryBar.getChildren().add(allButton);

        Set<String> seenCategories = new LinkedHashSet<>();
        keybinds.keySet().forEach(id -> {
            Keybind keybind = KeybindHandler.getKeybind(id);
            if (keybind == null) return;
            KeybindCategory category = keybind.getCategory();
            if (category == null) return;
            if (seenCategories.add(category.id())) {
                categoryBar.getChildren().add(createCategoryChip(category.titleKey(), category.id()));
            }
        });

        if (activeCategoryId == null) {
            allButton.setSelected(true);
            categoryToggleGroup.selectToggle(allButton);
        } else {
            categoryToggleGroup.getToggles().stream()
                .filter(toggle -> Objects.equals(toggle.getUserData(), activeCategoryId))
                .findFirst()
                .ifPresent(toggle -> {
                    toggle.setSelected(true);
                    categoryToggleGroup.selectToggle(toggle);
                });
        }
    }

    private ToggleButton createCategoryChip(String localizationKey, String categoryId) {
        ToggleButton chip = new ToggleButton();
        chip.getStyleClass().add("keybinds-category-chip");
        chip.setToggleGroup(categoryToggleGroup);
        chip.setUserData(categoryId);
        chip.textProperty().bind(Bindings.createStringBinding(() ->
                localizationKey == null ? "" : L18n.localize(localizationKey),
            L18n.currentLanguageProperty()));
        chip.setOnAction(event -> {
            activeCategoryId = categoryId;
            renderKeybindCards();
        });
        return chip;
    }

    private void renderKeybindCards() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);

        List<Map.Entry<String, List<KeybindData>>> filtered = keybinds.entrySet().stream()
            .filter(entry -> KeybindHandler.getKeybind(entry.getKey()) != null)
            .filter(entry -> matchesCategory(entry.getKey()))
            .filter(entry -> matchesSearch(entry.getKey(), query))
            .sorted(Comparator.comparing(entry -> getDisplayName(entry.getKey()), String.CASE_INSENSITIVE_ORDER))
            .toList();

        cardsContainer.getChildren().setAll(filtered.stream()
            .map(entry -> createKeybindCard(entry.getKey(), entry.getValue()))
            .toList());

        boolean hasResults = !filtered.isEmpty();
        cardsContainer.setVisible(hasResults);
        cardsContainer.setManaged(hasResults);
        emptyState.setVisible(!hasResults);
        emptyState.setManaged(!hasResults);

        if (!hasResults) {
            pendingEditKey = null;
            pendingEditIndex = -1;
        }
    }

    private boolean matchesCategory(String keybindId) {
        if (activeCategoryId == null || activeCategoryId.isBlank()) {
            return true;
        }
        Keybind keybind = KeybindHandler.getKeybind(keybindId);
        return keybind != null &&
            keybind.getCategory() != null &&
            activeCategoryId.equals(keybind.getCategory().id());
    }

    private boolean matchesSearch(String keybindId, String query) {
        if (query.isBlank()) return true;
        String display = getDisplayName(keybindId).toLowerCase(Locale.ENGLISH);
        return display.contains(query) || keybindId.toLowerCase(Locale.ENGLISH).contains(query);
    }

    private Node createKeybindCard(String keybindId, List<KeybindData> bindings) {
        var card = new RRCard();
        card.getStyleClass().add("keybind-card");

        var title = new LocalizedLabel(localizationKeyFor(keybindId));
        title.getStyleClass().add("keybind-card-title");

        var subtitle = createSubtitleLabel(keybindId);
        if (subtitle != null) {
            subtitle.getStyleClass().add("keybind-card-subtitle");
        }

        var titleBox = new VBox(4);
        titleBox.getChildren().add(title);
        if (subtitle != null) {
            titleBox.getChildren().add(subtitle);
        }

        var addButton = new RRButton("railroad.settings.keybinds.add_binding", FontAwesomeSolid.PLUS);
        addButton.setButtonSize(RRButton.ButtonSize.SMALL);
        addButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        addButton.getStyleClass().add("keybind-card-add");
        addButton.setOnAction(event -> addBlankShortcut(keybindId, bindings));

        var header = new HBox(12, titleBox, addButton);
        header.getStyleClass().add("keybind-card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        var shortcutsBox = new VBox(8);
        shortcutsBox.getStyleClass().add("keybind-card-shortcuts");

        if (bindings.isEmpty()) {
            var emptyLabel = new LocalizedLabel("railroad.settings.keybinds.no_shortcuts");
            emptyLabel.getStyleClass().add("keybind-card-shortcuts-empty");
            shortcutsBox.getChildren().add(emptyLabel);
        } else {
            for (int i = 0; i < bindings.size(); i++) {
                shortcutsBox.getChildren().add(createShortcutRow(keybindId, bindings, bindings.get(i), i));
            }
        }

        card.addContent(header, shortcutsBox);
        return card;
    }

    private LocalizedLabel createSubtitleLabel(String keybindId) {
        String descriptionKey = localizationKeyFor(keybindId) + ".description";
        if (L18n.isKeyValid(descriptionKey)) {
            return new LocalizedLabel(descriptionKey);
        }

        Keybind keybind = KeybindHandler.getKeybind(keybindId);
        if (keybind != null && keybind.getCategory() != null) {
            String categoryKey = keybind.getCategory().titleKey();
            if (categoryKey != null && L18n.isKeyValid(categoryKey)) {
                return new LocalizedLabel(categoryKey);
            }
        }

        return null;
    }

    private Node createShortcutRow(String keybindId, List<KeybindData> bindings, KeybindData binding, int index) {
        var row = new HBox(8);
        row.getStyleClass().add("keybind-shortcut-row");
        row.setAlignment(Pos.CENTER_LEFT);

        var comboNode = new KeyComboNode(binding);
        comboNode.setOnAction(event -> comboNode.toggleEditing());
        comboNode.setOnComboModified(updated -> bindings.set(index, updated));
        maybeStartPendingEdit(keybindId, index, comboNode);

        var removeButton = new RRButton("", FontAwesomeSolid.TRASH);
        removeButton.setVariant(RRButton.ButtonVariant.DANGER);
        removeButton.setButtonSize(RRButton.ButtonSize.SMALL);
        removeButton.getStyleClass().add("keybind-shortcut-remove");
        removeButton.setOnAction(event -> {
            bindings.remove(index);
            renderKeybindCards();
        });

        row.getChildren().addAll(comboNode, removeButton);
        return row;
    }

    private void maybeStartPendingEdit(String keybindId, int index, KeyComboNode comboNode) {
        if (Objects.equals(pendingEditKey, keybindId) && pendingEditIndex == index) {
            Platform.runLater(comboNode::toggleEditing);
            pendingEditKey = null;
            pendingEditIndex = -1;
        }
    }

    private void addBlankShortcut(String keybindId, List<KeybindData> bindings) {
        var newBinding = new KeybindData(KeyCode.UNDEFINED, new KeyCombination.Modifier[0]);
        bindings.add(newBinding);
        pendingEditKey = keybindId;
        pendingEditIndex = bindings.size() - 1;
        renderKeybindCards();
    }

    private String getDisplayName(String keybindId) {
        String key = localizationKeyFor(keybindId);
        if (L18n.isKeyValid(key)) {
            return L18n.localize(key);
        }
        return keybindId;
    }

    private String localizationKeyFor(String keybindId) {
        if (keybindId == null || !keybindId.contains(":")) {
            return "railroad.settings.keybinds." + keybindId;
        }
        return "railroad.settings.keybinds." + keybindId.split(":", 2)[1];
    }

    /**
     * Converts the keybinds map to a JSON representation.
     *
     * @param keybinds
     * @return a JsonElement representing the keybinds.
     */
    public static JsonElement toJson(Map<String, List<KeybindData>> keybinds) {
        var jsonObject = new JsonObject();
        for (Map.Entry<String, List<KeybindData>> entry : keybinds.entrySet()) {
            var keyList = new JsonArray();

            for (KeybindData combo : entry.getValue()) {
                var comboString = new StringBuilder(combo.keyCode().toString() + ";");
                if (combo.modifiers() == null || combo.modifiers().length == 0) {
                    keyList.add(comboString.toString());
                    continue;
                }
                for (KeyCombination.Modifier modifier : combo.modifiers()) {
                    comboString.append(modifier.toString()).append(",");
                }
                comboString.deleteCharAt(comboString.length() - 1);
                keyList.add(comboString.toString());
            }

            jsonObject.add(entry.getKey(), keyList);
        }

        return jsonObject;
    }

    /**
     * Converts a JSON representation of keybinds into a map of keybind IDs to their corresponding KeybindData lists.
     *
     * @param json the JSON element representing the keybinds.
     * @return a map of keybind IDs to lists of KeybindData.
     */
    public static Map<String, List<KeybindData>> fromJson(JsonElement json) {
        var map = new LinkedHashMap<String, List<KeybindData>>();

        for (Map.Entry<String, JsonElement> keybindJson : json.getAsJsonObject().entrySet()) {
            var id = keybindJson.getKey();
            var keyList = keybindJson.getValue().getAsJsonArray();
            if (KeybindHandler.getKeybind(id) == null) {
                Railroad.LOGGER.warn("Keybind " + id + " does not exist");
                continue;
            }

            for (JsonElement keyCombo : keyList) {
                String[] parts = keyCombo.getAsString().split(";");
                KeyCode keyCode = KeyCode.valueOf(parts[0]);

                if (parts.length < 2 || parts[1].isBlank()) {
                    KeybindHandler.getKeybind(id).addKey(keyCode, (KeyCombination.Modifier) null);
                    continue;
                }

                String[] modParts = parts[1].split(",");
                List<KeyCombination.Modifier> modifiers = new ArrayList<>();

                for (String mod : modParts) {
                    switch (mod.trim()) {
                        case "Shortcut" -> modifiers.add(KeyCombination.SHORTCUT_DOWN);
                        case "Ctrl" -> modifiers.add(KeyCombination.CONTROL_DOWN);
                        case "Shift" -> modifiers.add(KeyCombination.SHIFT_DOWN);
                        case "Alt" -> modifiers.add(KeyCombination.ALT_DOWN);
                        default -> throw new IllegalArgumentException("Unknown modifier: " + mod);
                    }
                }

                KeybindHandler.getKeybind(id).addKey(keyCode, modifiers.toArray(new KeyCombination.Modifier[0]));
            }

            var keys = new ArrayList<KeybindData>();
            for (JsonElement keyCombo : keyList) {
                var parts = keyCombo.getAsString().split(";");
                var keyCode = KeyCode.valueOf(parts[0]);

                if (parts.length == 1) {
                    keys.add(new KeybindData(keyCode, new KeyCombination.Modifier[0]));
                    continue;
                }

                var modParts = parts[1].split(",");
                var modifiers = new KeyCombination.Modifier[modParts.length];

                for (int i = 0; i < modParts.length; i++) {
                    switch (modParts[i]) {
                        case "Shortcut" -> modifiers[i] = KeyCombination.SHORTCUT_DOWN;
                        case "Ctrl" -> modifiers[i] = KeyCombination.CONTROL_DOWN;
                        case "Shift" -> modifiers[i] = KeyCombination.SHIFT_DOWN;
                        case "Alt" -> modifiers[i] = KeyCombination.ALT_DOWN;
                        default -> throw new IllegalArgumentException("Unknown modifier: " + modParts[i]);
                    }
                }

                keys.add(new KeybindData(keyCode, modifiers));
            }

            map.put(id, keys);
        }

        return map;
    }
}
