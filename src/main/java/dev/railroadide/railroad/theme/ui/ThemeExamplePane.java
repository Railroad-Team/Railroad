package dev.railroadide.railroad.theme.ui;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedComboBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedTableColumn;
import dev.railroadide.core.ui.styling.ButtonSize;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Arrays;

/**
 * A modernized theme preview pane that shows a live UI demonstration.
 * Features various components styled according to the selected theme.
 */
public class ThemeExamplePane {
    private final String themeName;

    public ThemeExamplePane(final String themeName) {
        this.themeName = themeName;
        var previewContent = createPreviewContent();

        var scrollPane = new ScrollPane(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("theme-preview-scroll-pane");

        var previewScene = new Scene(scrollPane, 900, 700);

        WindowBuilder builder = WindowBuilder.create()
            .title(L18n.localize("railroad.home.settings.appearance.preview") + " - " + formatThemeName(themeName))
            .scene(previewScene)
            .modality(Modality.APPLICATION_MODAL)
            .resizable(true)
            .minWidth(890)
            .minHeight(690);

        ThemeManager.applyThemeToScene(themeName, previewScene);

        builder.build();
    }

    private VBox createPreviewContent() {

        var mainContainer = new RRFormContainer();
        mainContainer.setPadding(new Insets(24));
        mainContainer.setAlignment(Pos.TOP_LEFT);
        mainContainer.getStyleClass().add("theme-example-main-container");

        var header = createHeaderSection();
        mainContainer.getChildren().add(header);

        var navigation = createNavigationSection();
        mainContainer.getChildren().add(navigation);

        var form = createFormSection();
        mainContainer.getChildren().add(form);

        var list = createListSection();
        mainContainer.getChildren().add(list);

        var buttons = createButtonSection();
        mainContainer.getChildren().add(buttons);

        var footer = createFooterSection();
        mainContainer.getChildren().add(footer);

        return mainContainer;
    }

    private VBox createHeaderSection() {
        var header = new RRVBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        var title = new LocalizedLabel("railroad.home.settings.appearance.preview.title");
        title.getStyleClass().add("form-title");

        var subtitle = new LocalizedLabel("railroad.home.settings.appearance.preview.subtitle");
        subtitle.getStyleClass().add("form-subtitle");

        header.getChildren().addAll(title, subtitle);
        return header;
    }

    private HBox createNavigationSection() {
        var navigation = new RRHBox(12);
        navigation.setAlignment(Pos.CENTER_LEFT);
        navigation.setPadding(new Insets(16, 0, 16, 0));

        var navItems = Arrays.asList(
            "railroad.home.settings.appearance.preview.navigation.0",
            "railroad.home.settings.appearance.preview.navigation.1",
            "railroad.home.settings.appearance.preview.navigation.2",
            "railroad.home.settings.appearance.preview.navigation.3"
        );

        for (String item : navItems) {
            var navButton = new RRButton(item);
            navButton.setOnMouseEntered(e -> navButton.getStyleClass().add("theme-example-nav-button-hover"));
            navButton.setOnMouseExited(e -> navButton.getStyleClass().remove("theme-example-nav-button-hover"));
            navButton.getStyleClass().add("theme-example-nav-button");
            navigation.getChildren().add(navButton);
        }

        return navigation;
    }

    private VBox createFormSection() {
        var formSection = new RRFormSection();
        formSection.setLocalizedHeaderText("railroad.home.settings.appearance.preview.form.components");

        var textFieldRow = new RRHBox(12);
        textFieldRow.setAlignment(Pos.CENTER_LEFT);
        textFieldRow.getStyleClass().add("transparent-background");

        var textField = new RRTextField("railroad.home.settings.appearance.preview.form.text_prompt");
        textField.setPrefWidth(200);

        var passwordField = new RRPasswordField("railroad.home.settings.appearance.preview.form.password_prompt");
        passwordField.setPrefWidth(200);

        textFieldRow.getChildren().addAll(
            new LocalizedLabel("railroad.theme.preview.text_field"), textField,
            new LocalizedLabel("railroad.theme.preview.password"), passwordField
        );

        var controlsRow = new RRHBox(12);
        controlsRow.setAlignment(Pos.CENTER_LEFT);
        controlsRow.getStyleClass().add("transparent-background");

        var comboBox = LocalizedComboBox.fromLocalizationKeys(FXCollections.observableArrayList(
            "railroad.home.settings.appearance.preview.form.combo_box.items.0",
            "railroad.home.settings.appearance.preview.form.combo_box.items.1",
            "railroad.home.settings.appearance.preview.form.combo_box.items.2"
        ));
        comboBox.setValue("railroad.home.settings.appearance.preview.form.combo_box.items.0");
        comboBox.setPrefWidth(150);

        var checkBox = new RRCheckBox("railroad.home.settings.appearance.preview.form.check_box.label");
        checkBox.setSelected(true);

        var toggleButton = new RRToggleButton("railroad.home.settings.appearance.preview.form.toggle_button.label");
        toggleButton.setSelected(false);

        var radioButton = new RRRadioButton("railroad.home.settings.appearance.preview.form.radio_button.label");
        radioButton.setSelected(true);

        controlsRow.getChildren().addAll(
            new LocalizedLabel("railroad.theme.preview.dropdown"), comboBox,
            toggleButton, checkBox, radioButton
        );

        formSection.addContent(textFieldRow, controlsRow);
        return formSection;
    }

    private VBox createListSection() {
        var listSection = new RRFormSection();
        listSection.setLocalizedHeaderText("railroad.home.settings.appearance.preview.list.components");

        var listView = new RRListView<>();
        listView.getItems().addAll(
            L18n.localize("railroad.home.settings.appearance.preview.list.items.0"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.1"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.2"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.3"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.4"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.5"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.6"),
            L18n.localize("railroad.home.settings.appearance.preview.list.items.7")
        );
        listView.setPrefHeight(250);
        listView.setMinHeight(200);
        listView.getStyleClass().add("theme-example-list-view");

        var table = new RRTableView<ProjectData>();
        var nameColumn = new LocalizedTableColumn<ProjectData, String>("railroad.home.settings.appearance.preview.table.columns.0");
        var typeColumn = new LocalizedTableColumn<ProjectData, String>("railroad.home.settings.appearance.preview.table.columns.1");
        var statusColumn = new LocalizedTableColumn<ProjectData, String>("railroad.home.settings.appearance.preview.table.columns.2");

        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        nameColumn.setPrefWidth(200);
        typeColumn.setPrefWidth(120);
        statusColumn.setPrefWidth(120);

        // noinspection unchecked
        table.getColumns().addAll(nameColumn, typeColumn, statusColumn);

        ObservableList<ProjectData> data = FXCollections.observableArrayList(
            new ProjectData("MyMod", "Fabric", "Active"),
            new ProjectData("CoolPlugin", "Forge", "Inactive"),
            new ProjectData("AwesomeAddon", "Neoforge", "Active"),
            new ProjectData("DemoMod", "Bukkit", "Inactive")
        );
        table.setItems(data);
        table.setPrefHeight(250);
        table.setMinHeight(200);

        listSection.addContent(listView, table);
        return listSection;
    }

    private VBox createButtonSection() {
        var buttonSection = new RRFormSection();
        buttonSection.setLocalizedHeaderText("railroad.home.settings.appearance.preview.button.components");

        var buttonRow1 = new RRHBox(12);
        buttonRow1.setAlignment(Pos.CENTER_LEFT);
        buttonRow1.getStyleClass().add("transparent-background");

        var primaryButton = new RRButton("railroad.home.settings.appearance.preview.button.primary");
        primaryButton.setVariant(ButtonVariant.PRIMARY);

        var secondaryButton = new RRButton("railroad.home.settings.appearance.preview.button.secondary");
        secondaryButton.setVariant(ButtonVariant.SECONDARY);

        var dangerButton = new RRButton("railroad.home.settings.appearance.preview.button.danger");
        dangerButton.setVariant(ButtonVariant.DANGER);

        var successButton = new RRButton("railroad.home.settings.appearance.preview.button.success");
        successButton.setVariant(ButtonVariant.SUCCESS);

        buttonRow1.getChildren().addAll(primaryButton, secondaryButton, dangerButton, successButton);

        var buttonRow2 = new RRHBox(12);
        buttonRow2.setAlignment(Pos.CENTER_LEFT);
        buttonRow2.getStyleClass().add("transparent-background");

        var ghostButton = new RRButton("railroad.home.settings.appearance.preview.button.ghost");
        ghostButton.setVariant(ButtonVariant.GHOST);

        var smallButton = new RRButton("railroad.home.settings.appearance.preview.button.small");
        smallButton.setButtonSize(ButtonSize.SMALL);

        var largeButton = new RRButton("railroad.home.settings.appearance.preview.button.large");
        largeButton.setButtonSize(ButtonSize.LARGE);

        var iconButton = new RRButton();
        iconButton.setIcon(FontAwesomeSolid.STAR);
        iconButton.setVariant(ButtonVariant.GHOST);

        buttonRow2.getChildren().addAll(ghostButton, smallButton, largeButton, iconButton);

        buttonSection.addContent(buttonRow1, buttonRow2);
        return buttonSection;
    }

    private HBox createFooterSection() {
        var footer = new RRHBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 0, 0, 0));

        var closeButton = new RRButton("railroad.home.settings.appearance.preview.close");
        closeButton.setVariant(ButtonVariant.SECONDARY);
        closeButton.setOnAction(e -> {
            var target = (Node) e.getTarget();
            var stage = (Stage) target.sceneProperty().get().getWindow();
            stage.close();
        });

        var applyButton = new RRButton("railroad.home.settings.appearance.preview.apply");
        applyButton.setVariant(ButtonVariant.PRIMARY);
        applyButton.setOnAction(e -> {
            // Apply the theme to the main application
            ThemeManager.setTheme(themeName.replace(".css", ""));

            var target = (Node) e.getTarget();
            var stage = (Stage) target.sceneProperty().get().getWindow();
            stage.close();

            applyButton.sceneProperty();
        });

        footer.getChildren().addAll(closeButton, applyButton);
        return footer;
    }

    private String formatThemeName(String themeName) {
        return themeName
            .replace("\"", "")
            .replace(".css", "")
            .replace("-", " ")
            .replace("_", " ");
    }

    public class ProjectData {

        public final StringProperty name = new SimpleStringProperty(this, "name", null);
        public final StringProperty type = new SimpleStringProperty(this, "type", null);
        public final StringProperty status = new SimpleStringProperty(this, "status", null);

        public String getName() { return name.get(); }
        public String getType() { return type.get(); }
        public String getStatus() { return status.get(); }

        public ProjectData(String name, String type, String status) {
            this.name.set(name);
            this.type.set(type);
            this.status.set(status);
        }
    }
}
