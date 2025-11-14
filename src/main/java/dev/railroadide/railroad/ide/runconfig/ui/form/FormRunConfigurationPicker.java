package dev.railroadide.railroad.ide.runconfig.ui.form;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.form.ui.InformativeLabeledHBox;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRButton.ButtonSize;
import dev.railroadide.core.ui.RRButton.ButtonVariant;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.localization.L18n;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.Priority;
import org.jetbrains.annotations.Nullable;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.*;
import java.util.function.Predicate;

/**
 * Form field that lets users pick, order, and remove run configurations.
 */
public class FormRunConfigurationPicker extends InformativeLabeledHBox<RRVBox> implements HasSetValue {
    private ObservableList<RunConfiguration<?>> selectedConfigurations;
    private ReadOnlyObjectWrapper<RunConfiguration<?>[]> value;

    private ObservableList<RunConfiguration<?>> availableConfigurations;
    private Predicate<RunConfiguration<?>> filter;
    private FilteredList<RunConfiguration<?>> selectableConfigurations;

    private ContextMenu addMenu;
    private ListView<RunConfiguration<?>> selectedListView;

    private void ensureStateInitialized() {
        if (selectedConfigurations == null) {
            selectedConfigurations = FXCollections.observableArrayList();
        }

        if (value == null) {
            value = new ReadOnlyObjectWrapper<>(new RunConfiguration<?>[0]);
        }

        if (addMenu == null) {
            addMenu = new ContextMenu();
        }
    }

    public FormRunConfigurationPicker(String labelKey,
                                      boolean required,
                                      ObservableList<RunConfiguration<?>> availableConfigurations,
                                      Predicate<RunConfiguration<?>> filter,
                                      List<RunConfiguration<?>> initialSelection) {
        super(labelKey, required, Map.of(
            "availableConfigurations", availableConfigurations,
            "filter", filter == null ? (Predicate<RunConfiguration<?>>) Objects::nonNull : filter,
            "initialSelection", initialSelection == null ? List.of() : initialSelection
        ));
        ensureStateInitialized();

        selectedConfigurations.addListener((ListChangeListener<? super RunConfiguration<?>>) change -> {
            updateValue();
            refreshSelectablePredicate();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public RRVBox createPrimaryComponent(Map<String, Object> params) {
        ensureStateInitialized();
        this.availableConfigurations = (ObservableList<RunConfiguration<?>>) params.get("availableConfigurations");
        this.filter = (Predicate<RunConfiguration<?>>) params.get("filter");
        List<RunConfiguration<?>> initialSelection = (List<RunConfiguration<?>>) params.get("initialSelection");

        this.selectableConfigurations = new FilteredList<>(availableConfigurations);

        selectedListView = new RRListView<>(selectedConfigurations);
        selectedListView.setPrefHeight(200);
        selectedListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(RunConfiguration<?> item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : formatDisplayName(item));
            }
        });
        selectedListView.setPlaceholder(new LocalizedLabel("railroad.runconfig.compound.configuration.configurations.empty"));
        RRVBox.setVgrow(selectedListView, Priority.ALWAYS);

        var toolbar = new RRHBox(6);
        toolbar.getStyleClass().add("runconfig-picker-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        RRButton addButton = createToolbarButton(FontAwesomeSolid.PLUS,
            "railroad.runconfig.compound.configuration.configurations.add",
            "add");
        addButton.disableProperty().bind(Bindings.isEmpty(selectableConfigurations));
        addButton.setOnAction(event -> toggleAddMenu(addButton));
        toolbar.getChildren().add(addButton);

        RRButton removeButton = createToolbarButton(FontAwesomeSolid.MINUS,
            "railroad.runconfig.compound.configuration.configurations.remove",
            "remove");
        removeButton.disableProperty().bind(selectedListView.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction($ -> removeSelectedConfiguration());
        toolbar.getChildren().add(removeButton);

        RRButton moveUpButton = createToolbarButton(FontAwesomeSolid.ARROW_UP,
            "railroad.runconfig.compound.configuration.configurations.move_up",
            "move-up");
        moveUpButton.disableProperty().bind(selectedListView.getSelectionModel().selectedIndexProperty().lessThanOrEqualTo(0));
        moveUpButton.setOnAction($ -> moveSelection(-1));
        toolbar.getChildren().add(moveUpButton);

        RRButton moveDownButton = createToolbarButton(FontAwesomeSolid.ARROW_DOWN,
            "railroad.runconfig.compound.configuration.configurations.move_down",
            "move-down");
        moveDownButton.disableProperty().bind(Bindings.createBooleanBinding(
            () -> {
                int selectedIndex = selectedListView.getSelectionModel().getSelectedIndex();
                return selectedIndex < 0 || selectedIndex >= selectedConfigurations.size() - 1;
            },
            selectedListView.getSelectionModel().selectedIndexProperty(),
            selectedConfigurations
        ));
        moveDownButton.setOnAction($ -> moveSelection(1));
        toolbar.getChildren().add(moveDownButton);

        var container = new RRVBox(8, toolbar, selectedListView);

        availableConfigurations.addListener((ListChangeListener<? super RunConfiguration<?>>) change -> {
            while (change.next()) {
                if (change.wasRemoved() || change.wasAdded() || change.wasReplaced() || change.wasPermutated()) {
                    break;
                }
            }

            boolean selectionsChanged = synchronizeSelectionWithAvailable();
            refreshSelectablePredicate();
            if (selectionsChanged) {
                updateValue();
            }
        });

        if (initialSelection != null && !initialSelection.isEmpty()) {
            selectedConfigurations.setAll(initialSelection);
        } else {
            refreshSelectablePredicate();
            updateValue();
        }

        return container;
    }

    public ReadOnlyObjectProperty<RunConfiguration<?>[]> valueProperty() {
        ensureStateInitialized();
        return value.getReadOnlyProperty();
    }

    public RunConfiguration<?>[] getValue() {
        ensureStateInitialized();
        return value.get() == null ? new RunConfiguration[0] : value.get();
    }

    public ObservableList<RunConfiguration<?>> getSelectedConfigurations() {
        ensureStateInitialized();
        return FXCollections.unmodifiableObservableList(selectedConfigurations);
    }

    @Override
    public void setValue(Object value) {
        ensureStateInitialized();
        Platform.runLater(() -> {
            // Should be able to have a switch statement here, but for some reason Java doesn't like it (?)
            if (value == null) {
                selectedConfigurations.clear();
            } else if (value instanceof RunConfiguration<?>[] array) {
                selectedConfigurations.setAll(Arrays.asList(array));
            } else if (value instanceof List<?> list) {
                List<RunConfiguration<?>> sanitized = new ArrayList<>(list.size());
                for (Object element : list) {
                    if (element instanceof RunConfiguration<?> rc) {
                        sanitized.add(rc);
                    }
                }

                selectedConfigurations.setAll(sanitized);
            } else
                throw new IllegalArgumentException("Unsupported value type: " + value.getClass());
        });
    }

    private RRButton createToolbarButton(Ikon icon, String tooltipKey, String styleSuffix) {
        var button = new RRButton("", icon);
        button.setVariant(ButtonVariant.GHOST);
        button.setButtonSize(ButtonSize.SMALL);
        button.setSquare(true);
        button.setAccessibleText(L18n.localize(tooltipKey));
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        button.getStyleClass().addAll("runconfig-picker-button", "runconfig-picker-button-" + styleSuffix);
        return button;
    }

    private void toggleAddMenu(RRButton anchor) {
        ensureStateInitialized();
        if (addMenu == null) {
            addMenu = new ContextMenu();
        }

        refreshAddMenuItems();
        if (addMenu.getItems().isEmpty()) {
            return;
        }

        if (addMenu.isShowing()) {
            addMenu.hide();
        } else {
            addMenu.show(anchor, Side.BOTTOM, 0, 0);
        }
    }

    private void refreshAddMenuItems() {
        addMenu.getItems().clear();
        if (selectableConfigurations == null)
            return;

        for (RunConfiguration<?> configuration : selectableConfigurations) {
            var menuItem = new MenuItem(formatDisplayName(configuration));
            menuItem.setOnAction($ -> addSelectedConfiguration(configuration));
            addMenu.getItems().add(menuItem);
        }

        if (addMenu.getItems().isEmpty()) {
            var emptyItem = new MenuItem(L18n.localize("railroad.runconfig.compound.configuration.configurations.empty"));
            emptyItem.setDisable(true);
            addMenu.getItems().add(emptyItem);
        }
    }

    private void addSelectedConfiguration(RunConfiguration<?> toAdd) {
        ensureStateInitialized();
        if (toAdd != null && selectedConfigurations.stream().noneMatch(config -> config.uuid().equals(toAdd.uuid()))) {
            selectedConfigurations.add(toAdd);
        }

        if (addMenu != null && addMenu.isShowing()) {
            addMenu.hide();
        }
    }

    private void removeSelectedConfiguration() {
        ensureStateInitialized();
        RunConfiguration<?> selected = selectedListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedConfigurations.remove(selected);
        }
    }

    private void moveSelection(int delta) {
        ensureStateInitialized();
        int index = selectedListView.getSelectionModel().getSelectedIndex();
        if (index < 0)
            return;

        int target = index + delta;
        if (target < 0 || target >= selectedConfigurations.size())
            return;

        Collections.swap(selectedConfigurations, index, target);
        selectedListView.getSelectionModel().select(target);
    }

    private void refreshSelectablePredicate() {
        ensureStateInitialized();
        selectableConfigurations.setPredicate(configuration ->
            configuration != null &&
                filter.test(configuration) &&
                selectedConfigurations.stream().noneMatch(selected -> selected.uuid().equals(configuration.uuid())));
    }

    private boolean synchronizeSelectionWithAvailable() {
        ensureStateInitialized();
        if (availableConfigurations == null || availableConfigurations.isEmpty()) {
            if (selectedConfigurations.isEmpty())
                return false;

            selectedConfigurations.clear();
            return true;
        }

        if (selectedConfigurations.isEmpty())
            return false;

        Map<UUID, RunConfiguration<?>> availableById = new HashMap<>();
        for (RunConfiguration<?> available : availableConfigurations) {
            if (available != null) {
                availableById.put(available.uuid(), available);
            }
        }

        boolean changed = false;
        for (int i = selectedConfigurations.size() - 1; i >= 0; i--) {
            RunConfiguration<?> selected = selectedConfigurations.get(i);
            if (selected == null) {
                selectedConfigurations.remove(i);
                changed = true;
                continue;
            }

            RunConfiguration<?> replacement = availableById.get(selected.uuid());
            if (replacement == null) {
                selectedConfigurations.remove(i);
                changed = true;
            } else if (replacement != selected) {
                selectedConfigurations.set(i, replacement);
                changed = true;
            }
        }

        return changed;
    }

    private void updateValue() {
        ensureStateInitialized();
        value.set(selectedConfigurations.toArray(new RunConfiguration[0]));
    }

    private static String formatDisplayName(@Nullable RunConfiguration<?> configuration) {
        if (configuration == null)
            return "";

        RunConfigurationType<?> type = configuration.type();
        String typeName = type == null ? "" : L18n.localize(type.getLocalizationKey());
        if (typeName == null || typeName.isBlank())
            return configuration.data().getName();

        return L18n.localize("railroad.runconfig.compound.configuration.configurations.entry_format",
            configuration.data().getName(), typeName);
    }
}
