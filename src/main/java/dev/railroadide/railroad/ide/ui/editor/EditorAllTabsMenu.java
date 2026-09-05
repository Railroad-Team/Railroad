package dev.railroadide.railroad.ide.ui.editor;

import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.utility.FileUtils;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class EditorAllTabsMenu implements AutoCloseable {
    private static final double ROW_HEIGHT = 48;
    private static final double MIN_LIST_HEIGHT = 72;
    private static final double MAX_LIST_HEIGHT = 384;

    private final DetachableTabPane tabPane;
    private final Function<Tab, EditorTab> editorTabResolver;
    private final ContextMenu popup = new ContextMenu();
    private final RRTextField searchField = new RRTextField("editor.tabs.dropdown.search");
    private final ObservableList<EditorTab> tabs = FXCollections.observableArrayList();
    private final FilteredList<EditorTab> filteredTabs;
    private final RRListView<EditorTab> tabsList;

    public EditorAllTabsMenu(
        DetachableTabPane tabPane,
        Function<Tab, EditorTab> editorTabResolver
    ) {
        this.tabPane = Objects.requireNonNull(tabPane, "Tab pane cannot be null");
        this.editorTabResolver = Objects.requireNonNull(editorTabResolver, "Editor tab resolver cannot be null");

        this.filteredTabs = new FilteredList<>(tabs);
        this.tabsList = new RRListView<>(filteredTabs);
        tabsList.setAnimationsEnabled(false);
        tabsList.setCellFactory(_ -> new EditorTabCell());
        tabsList.setPlaceholder(new LocalizedLabel("editor.tabs.dropdown.empty"));
        tabsList.getStyleClass().add("editor-all-tabs-list");
        tabsList.setMinWidth(480);
        tabsList.setPrefWidth(520);
        tabsList.setMaxWidth(620);

        searchField.getStyleClass().add("editor-all-tabs-search");
        searchField.textProperty().addListener((_, _, query) -> updateFilter(query));
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchKeyPressed);
        tabsList.addEventFilter(KeyEvent.KEY_PRESSED, this::handleListKeyPressed);
        tabsList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                activateSelectedTab();
            }
        });
        filteredTabs.addListener((InvalidationListener) _ -> updateListHeight());
        updateListHeight();

        var content = new RRVBox(6, searchField, tabsList);
        content.getStyleClass().add("editor-all-tabs-content");
        VBox.setVgrow(tabsList, Priority.ALWAYS);

        var menuItem = new CustomMenuItem(content, false);
        menuItem.getStyleClass().add("editor-all-tabs-menu-item");
        popup.getStyleClass().add("editor-all-tabs-menu");
        popup.getItems().setAll(menuItem);
        popup.setOnShown(_ -> Platform.runLater(searchField::requestFocus));
    }

    public void show(Node owner) {
        refreshTabs();
        if (filteredTabs.getSource().isEmpty())
            return;

        searchField.clear();
        updateFilter("");
        selectCurrentTab();
        popup.show(owner, Side.BOTTOM, 0, 0);
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void hide() {
        popup.hide();
    }

    private void refreshTabs() {
        var tabs = tabPane.getTabs().stream()
            .map(editorTabResolver)
            .filter(Objects::nonNull)
            .toList();
        this.tabs.setAll(tabs);
    }

    private void updateFilter(String query) {
        String[] terms = Objects.toString(query, "")
            .strip()
            .toLowerCase(Locale.ROOT)
            .split("\\s+");
        filteredTabs.setPredicate(tab -> {
            if (terms.length == 0 || terms.length == 1 && terms[0].isEmpty())
                return true;

            String searchableText = (fileName(tab.path()) + " " + tab.path().toAbsolutePath().normalize())
                .toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (!searchableText.contains(term))
                    return false;
            }
            return true;
        });
        selectCurrentTab();
    }

    private void updateListHeight() {
        double preferredHeight = Math.clamp(filteredTabs.size() * ROW_HEIGHT + 2, MIN_LIST_HEIGHT, MAX_LIST_HEIGHT);
        tabsList.setPrefHeight(preferredHeight);
    }

    private void selectCurrentTab() {
        EditorTab selectedTab = editorTabResolver.apply(tabPane.getSelectionModel().getSelectedItem());
        if (selectedTab != null && filteredTabs.contains(selectedTab)) {
            tabsList.getSelectionModel().select(selectedTab);
            tabsList.scrollTo(selectedTab);
        } else if (!filteredTabs.isEmpty()) {
            tabsList.getSelectionModel().selectFirst();
        } else {
            tabsList.getSelectionModel().clearSelection();
        }
    }

    private void handleSearchKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.DOWN) {
            if (tabsList.getSelectionModel().isEmpty()) {
                tabsList.getSelectionModel().selectFirst();
            }
            tabsList.requestFocus();
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            activateSelectedTab();
            event.consume();
        }
    }

    private void handleListKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            activateSelectedTab();
            event.consume();
        } else if (event.getCode() == KeyCode.ESCAPE) {
            hide();
            event.consume();
        }
    }

    private void activateSelectedTab() {
        EditorTab selectedTab = tabsList.getSelectionModel().getSelectedItem();
        if (selectedTab == null)
            return;

        tabPane.getSelectionModel().select(selectedTab.tab());
        hide();
        Node editorContent = selectedTab.tab().getContent();
        if (editorContent != null) {
            Platform.runLater(editorContent::requestFocus);
        }
    }

    private static String fileName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    @Override
    public void close() {
        hide();
        tabs.clear();
        popup.getItems().clear();
    }

    private final class EditorTabCell extends ListCell<EditorTab> {
        private final StackPane fileIconSlot = new RRStackPane();
        private final Label name = new Label();
        private final Label path = new Label();
        private final FontIcon dirtyIcon = new FontIcon(FontAwesomeSolid.ASTERISK);
        private final FontIcon pinIcon = new FontIcon(FontAwesomeSolid.THUMBTACK);
        private final FontIcon previewIcon = new FontIcon(FontAwesomeSolid.EYE);
        private final HBox content;
        private final InvalidationListener stateListener = _ -> updateContent();
        private final WeakInvalidationListener weakStateListener = new WeakInvalidationListener(stateListener);
        private EditorTab editorTab;

        private EditorTabCell() {
            getStyleClass().add("editor-all-tabs-cell");

            fileIconSlot.getStyleClass().add("editor-all-tabs-file-icon");
            fileIconSlot.setMinWidth(22);
            name.getStyleClass().add("editor-all-tabs-name");
            path.getStyleClass().add("editor-all-tabs-path");
            dirtyIcon.getStyleClass().addAll("editor-all-tabs-state-icon", "dirty");
            pinIcon.getStyleClass().addAll("editor-all-tabs-state-icon", "pinned");
            previewIcon.getStyleClass().addAll("editor-all-tabs-state-icon", "preview");
            Tooltip.install(dirtyIcon, new LocalizedTooltip("editor.tabs.dropdown.dirty"));
            Tooltip.install(pinIcon, new LocalizedTooltip("editor.tabs.dropdown.pinned"));
            Tooltip.install(previewIcon, new LocalizedTooltip("editor.tab.tooltip.preview"));

            var labels = new RRVBox(1, name, path);
            labels.getStyleClass().removeAll("Railroad", "Pane", "VBox", "background-2");
            labels.setAlignment(Pos.CENTER_LEFT);
            labels.setMinWidth(0);
            HBox.setHgrow(labels, Priority.ALWAYS);
            name.setMinWidth(0);
            name.setMaxWidth(Double.MAX_VALUE);
            path.setMinWidth(0);
            path.setMaxWidth(Double.MAX_VALUE);

            var states = new RRHBox(8, dirtyIcon, pinIcon, previewIcon);
            states.getStyleClass().removeAll("Railroad", "Pane", "HBox", "background-2");
            states.getStyleClass().add("editor-all-tabs-states");
            states.setAlignment(Pos.CENTER_RIGHT);
            this.content = new RRHBox(9, fileIconSlot, labels, states);
            content.getStyleClass().removeAll("Railroad", "Pane", "HBox", "background-2");
            content.getStyleClass().add("editor-all-tabs-row");
            content.setAlignment(Pos.CENTER_LEFT);
            content.setMaxWidth(Double.MAX_VALUE);
            content.prefWidthProperty().bind(Bindings.max(0, tabsList.widthProperty().subtract(20)));
        }

        @Override
        protected void updateItem(EditorTab item, boolean empty) {
            detachListeners();
            super.updateItem(item, empty);
            this.editorTab = empty ? null : item;
            if (editorTab == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            editorTab.pathProperty().addListener(weakStateListener);
            editorTab.displayTitleProperty().addListener(weakStateListener);
            editorTab.dirtyProperty().addListener(weakStateListener);
            editorTab.pinnedProperty().addListener(weakStateListener);
            editorTab.previewProperty().addListener(weakStateListener);
            editorTab.saveStateProperty().addListener(weakStateListener);
            setText(null);
            setGraphic(content);
            updateContent();
        }

        private void updateContent() {
            if (editorTab == null)
                return;

            Path editorPath = editorTab.path();
            Node fileIcon = FileUtils.getIcon(editorPath);
            fileIcon.setAccessibleText(editorTab.languageDisplayName() + " file");
            fileIconSlot.getChildren().setAll(fileIcon);
            name.setText(editorTab.displayTitle());
            String absolutePath = editorPath.toAbsolutePath().normalize().toString();
            path.setText(absolutePath);
            path.setTooltip(new Tooltip(absolutePath));
            dirtyIcon.setVisible(editorTab.dirty());
            dirtyIcon.setManaged(editorTab.dirty());
            pinIcon.setVisible(editorTab.pinned());
            pinIcon.setManaged(editorTab.pinned());
            previewIcon.setVisible(editorTab.preview());
            previewIcon.setManaged(editorTab.preview());
            if (editorTab.preview()) {
                if (!name.getStyleClass().contains("preview")) {
                    name.getStyleClass().add("preview");
                }
            } else {
                name.getStyleClass().remove("preview");
            }
            updateDirtyIcon(editorTab.saveState());
            setAccessibleText(editorTab.displayTitle() + ", " + editorTab.languageDisplayName() + ", " + absolutePath
                + (editorTab.dirty() ? ", unsaved changes" : "")
                + (editorTab.pinned() ? ", pinned" : "")
                + (editorTab.preview() ? ", preview" : ""));
        }

        private void detachListeners() {
            if (editorTab == null)
                return;

            editorTab.pathProperty().removeListener(weakStateListener);
            editorTab.displayTitleProperty().removeListener(weakStateListener);
            editorTab.dirtyProperty().removeListener(weakStateListener);
            editorTab.pinnedProperty().removeListener(weakStateListener);
            editorTab.previewProperty().removeListener(weakStateListener);
            editorTab.saveStateProperty().removeListener(weakStateListener);
        }

        private void updateDirtyIcon(EditorSaveState saveState) {
            dirtyIcon.getStyleClass().removeAll("saving", "error");
            if (saveState == EditorSaveState.SAVING) {
                dirtyIcon.setIconCode(FontAwesomeSolid.SYNC_ALT);
                dirtyIcon.getStyleClass().add("saving");
            } else if (saveState == EditorSaveState.ERROR) {
                dirtyIcon.setIconCode(FontAwesomeSolid.EXCLAMATION_CIRCLE);
                dirtyIcon.getStyleClass().add("error");
            } else {
                dirtyIcon.setIconCode(FontAwesomeSolid.ASTERISK);
            }
        }
    }
}
