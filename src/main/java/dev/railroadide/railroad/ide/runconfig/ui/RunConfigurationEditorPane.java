package dev.railroadide.railroad.ide.runconfig.ui;

import dev.railroadide.core.form.Form;
import dev.railroadide.core.form.FormData;
import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationType;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class RunConfigurationEditorPane extends RRVBox {
    private final ObservableList<RunConfiguration<?>> configurations = FXCollections.observableArrayList();
    private final ObjectProperty<RunConfiguration<?>> selectedConfiguration = new SimpleObjectProperty<>();
    private final Project project;
    private final RunConfigurationTreeView configurationTreeView;
    private final SplitPane editorSplitPane;
    private final StackPane detailContentContainer;
    private final StackPane centerContentContainer;
    private final Node noConfigurationsPane;
    private final Node detailsEmptyStatePane;
    private final Map<UUID, ConfigurationFormContext> configurationFormContexts = new HashMap<>();

    public RunConfigurationEditorPane(Project project) {
        this.project = project;
        this.configurations.addAll(project.getRunConfigManager().getConfigurations());
        this.noConfigurationsPane = createEmptyStatePane();
        this.detailsEmptyStatePane = createEmptyStatePane();
        this.configurationTreeView = new RunConfigurationTreeView(configurations);
        this.detailContentContainer = new StackPane();
        this.detailContentContainer.getStyleClass().add("run-configuration-details-pane");
        this.centerContentContainer = new StackPane();
        this.editorSplitPane = createEditorSplitPane();
        this.selectedConfiguration.bindBidirectional(configurationTreeView.selectedConfigurationProperty());

        getStyleClass().add("run-configuration-editor-pane");
        initializeUI();
        initializeBindings();
        updateEditorContent();
        updateDetailContent(selectedConfiguration.get());
    }

    private void initializeUI() {
        var topButtonBar = createTopButtonBar(project);
        getChildren().add(topButtonBar);

        centerContentContainer.getChildren().setAll(configurations.isEmpty() ? noConfigurationsPane : editorSplitPane);
        VBox.setVgrow(centerContentContainer, Priority.ALWAYS);
        getChildren().add(centerContentContainer);

        var bottomButtonBar = createBottomButtonBar(project);
        getChildren().add(bottomButtonBar);
    }

    private void initializeBindings() {
        configurations.addListener((ListChangeListener<RunConfiguration<?>>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(config -> configurationFormContexts.remove(config.uuid()));
                }
            }
            updateEditorContent();
        });

        selectedConfiguration.addListener((obs, oldValue, newValue) -> updateDetailContent(newValue));
    }

    private void updateDetailContent(RunConfiguration<?> configuration) {
        if (configuration == null) {
            detailContentContainer.getChildren().setAll(detailsEmptyStatePane);
            return;
        }

        detailContentContainer.getChildren().setAll(getConfigurationFormNode(configuration));
    }

    private void updateEditorContent() {
        if (configurations.isEmpty()) {
            centerContentContainer.getChildren().setAll(noConfigurationsPane);
        } else {
            centerContentContainer.getChildren().setAll(editorSplitPane);
        }
    }

    private VBox createEmptyStatePane() {
        var container = new RRVBox(8);
        container.setAlignment(Pos.CENTER);
        container.setPadding(new Insets(20));
        container.getStyleClass().add("run-configuration-editor-empty-state");

        var title = new LocalizedLabel("railroad.runconfig.details.empty.title");
        title.getStyleClass().add("run-configuration-editor-empty-state-title");

        var description = new LocalizedLabel("railroad.runconfig.details.empty.description");
        description.getStyleClass().add("run-configuration-editor-empty-state-description");
        description.setWrapText(true);
        description.setMaxWidth(320);

        container.getChildren().addAll(title, description);
        return container;
    }

    private SplitPane createEditorSplitPane() {
        detailContentContainer.getChildren().setAll(detailsEmptyStatePane);
        var splitPane = new SplitPane(configurationTreeView, detailContentContainer);
        splitPane.getStyleClass().add("run-configuration-editor-split-pane");
        splitPane.setDividerPositions(0.3);
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        return splitPane;
    }

    private HBox createTopButtonBar(Project project) {
        var topButtonBar = new RRHBox(5);
        topButtonBar.setAlignment(Pos.CENTER_LEFT);
        topButtonBar.setPadding(new Insets(5));
        topButtonBar.getStyleClass().add("run-configuration-editor-top-bar");

        var addButton = createTopBarButton(FontAwesomeSolid.PLUS);
        addButton.setOnAction($ -> {
            ContextMenu contextMenu = createAddContextMenu(project);
            contextMenu.show(addButton, Side.BOTTOM, 0, 0);
        });

        var removeButton = createTopBarButton(FontAwesomeSolid.MINUS);
        removeButton.setOnAction($ -> {
            RunConfiguration<?> selectedConfig = selectedConfiguration.get();
            if (selectedConfig != null) {
                configurations.remove(selectedConfig);
                selectedConfiguration.set(configurations.isEmpty() ? null : configurations.getFirst());
            }
        });
        removeButton.disableProperty().bind(selectedConfiguration.isNull());

        var copyButton = createTopBarButton(FontAwesomeSolid.COPY);
        copyButton.disableProperty().bind(selectedConfiguration.isNull());

        var addFolderButton = createTopBarButton(FontAwesomeSolid.FOLDER_PLUS);

        var renameButton = createTopBarButton(FontAwesomeSolid.PENCIL_ALT);
        renameButton.disableProperty().bind(selectedConfiguration.isNull());

        topButtonBar.getChildren().addAll(addButton, removeButton, copyButton, addFolderButton, renameButton);
        return topButtonBar;
    }

    private static RRButton createTopBarButton(Ikon icon) {
        var button = new RRButton("", icon);
        button.setSquare(true);
        button.setVariant(RRButton.ButtonVariant.GHOST);
        button.setButtonSize(RRButton.ButtonSize.SMALL);
        button.getStyleClass().add("run-configuration-editor-top-bar-button");
        return button;
    }

    private ContextMenu createAddContextMenu(Project project) {
        var contextMenu = new ContextMenu();
        for (RunConfigurationType<?> runConfigurationType : RunConfigurationType.REGISTRY.values()) {
            var graphic = new FontIcon(runConfigurationType.getIcon());
            graphic.setIconColor(runConfigurationType.getIconColor());
            graphic.getStyleClass().add("run-configuration-editor-add-menu-icon");
            String runConfigTypeId = RunConfigurationType.REGISTRY.entries().entrySet().stream()
                .filter(entry -> entry.getValue() == runConfigurationType)
                .findFirst()
                .map(entry -> entry.getKey().toLowerCase(Locale.ROOT))
                .orElse("unknown");
            graphic.getStyleClass().add("run-configuration-editor-add-menu-icon-" + runConfigTypeId);

            var menuItem = new MenuItem(L18n.localize(runConfigurationType.getLocalizationKey()), graphic);
            menuItem.setOnAction(event -> createRunConfiguration(project, runConfigurationType));
            contextMenu.getItems().add(menuItem);
        }

        return contextMenu;
    }

    private void createRunConfiguration(Project project, RunConfigurationType<?> runConfigurationType) {
        RunConfiguration<?> newConfiguration = runConfigurationType.createDefaultConfiguration(project);
        configurations.add(newConfiguration);
        selectedConfiguration.set(newConfiguration);
    }

    private HBox createBottomButtonBar(Project project) {
        var bottomButtonBar = new RRHBox(5);
        bottomButtonBar.setAlignment(Pos.CENTER_RIGHT);
        bottomButtonBar.setPrefHeight(HBox.USE_COMPUTED_SIZE);
        bottomButtonBar.setPadding(new Insets(5, 10, 5, 10));

        var okButton = new RRButton("railroad.generic.ok", FontAwesomeSolid.CHECK);
        okButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        okButton.setOnAction(event -> {
            if (!applySelectedConfigurationChanges())
                return;

            project.getRunConfigManager().sendUpdatedConfigurations(configurations);
            getScene().getWindow().hide();
        });

        var cancelButton = new RRButton("railroad.generic.cancel", FontAwesomeSolid.TIMES);
        cancelButton.setVariant(RRButton.ButtonVariant.DANGER);
        cancelButton.setOnAction(event -> getScene().getWindow().hide());

        var applyButton = new RRButton("railroad.generic.apply", FontAwesomeSolid.CHECK_DOUBLE);
        applyButton.setVariant(RRButton.ButtonVariant.SUCCESS);
        applyButton.setOnAction(event -> {
            if (!applySelectedConfigurationChanges())
                return;

            project.getRunConfigManager().sendUpdatedConfigurations(configurations);
        });

        bottomButtonBar.getChildren().addAll(okButton, cancelButton, applyButton);
        return bottomButtonBar;
    }

    public void selectConfiguration(RunConfiguration<?> runConfiguration) {
        selectedConfiguration.set(runConfiguration);
    }

    private Node getConfigurationFormNode(RunConfiguration<?> configuration) {
        ConfigurationFormContext context = configurationFormContexts.get(configuration.uuid());
        if (context == null) {
            Form form = configuration.data().createConfigurationForm(project, configuration);
            Node formNode = form.createUI();
            VBox.setVgrow(formNode, Priority.ALWAYS);
            context = new ConfigurationFormContext(form, formNode);
            configurationFormContexts.put(configuration.uuid(), context);
        }

        return context.node();
    }

    private boolean applySelectedConfigurationChanges() {
        RunConfiguration<?> configuration = selectedConfiguration.get();
        if (configuration == null)
            return true;

        ConfigurationFormContext context = configurationFormContexts.get(configuration.uuid());
        if (context == null)
            return true;

        Form form = context.form();
        form.runValidation();
        if (!form.validate())
            return false;

        FormData formData = form.getFormData();
        configuration.data().applyConfigurationFormData(formData);
        return true;
    }

    private record ConfigurationFormContext(Form form, Node node) {
    }
}
