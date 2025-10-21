package dev.railroadide.railroad.theme.ui;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
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
// TODO: Translate
public class ThemeExamplePane {
    private final Stage stage;
    private final String themeName;

    public ThemeExamplePane(final String themeName) {
        this.themeName = themeName;
        var previewContent = createPreviewContent();

        var scrollPane = new ScrollPane(previewContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("theme-preview-scroll-pane");

        var previewScene = new Scene(scrollPane, 900, 700);
        ThemeManager.applyThemeToScene(themeName, previewScene);

        this.stage = WindowBuilder.create()
            .title(L18n.localize("railroad.home.settings.appearance.preview") + " - " + formatThemeName(themeName))
            .scene(previewScene)
            .modality(Modality.APPLICATION_MODAL)
            .resizable(true)
            .minWidth(890)
            .minHeight(690)
            .build();
    }

    private VBox createPreviewContent() {
        var mainContainer = new RRVBox(20);
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

        var navItems = Arrays.asList("Home", "Projects", "Settings", "Help");
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

        var textField = new RRTextField();
        textField.setPromptText("Enter text here...");
        textField.setPrefWidth(200);

        var passwordField = new PasswordField(); // TODO: Create RRPasswordField
        passwordField.setPromptText("Password");
        passwordField.setPrefWidth(200);

        textFieldRow.getChildren().addAll(
            new LocalizedLabel("railroad.theme.preview.text_field"), textField,
            new LocalizedLabel("railroad.theme.preview.password"), passwordField
        );

        var controlsRow = new RRHBox(12);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        var comboBox = new ComboBox<>(); // TODO: Use LocalizedComboBox
        comboBox.getItems().addAll("Option 1", "Option 2", "Option 3");
        comboBox.setValue("Option 1");
        comboBox.setPrefWidth(150);

        var checkBox = new CheckBox("Enable feature"); // TODO: Create RRCheckBox
        checkBox.setSelected(true);

        var radioButton = new RadioButton("Radio option"); // TODO: Create RRRadioButton
        radioButton.setSelected(true);

        controlsRow.getChildren().addAll(
            new LocalizedLabel("railroad.theme.preview.dropdown"), comboBox,
            checkBox, radioButton
        );

        formSection.addContent(textFieldRow, controlsRow);
        return formSection;
    }

    private VBox createListSection() {
        var listSection = new RRFormSection();
        listSection.setLocalizedHeaderText("railroad.home.settings.appearance.preview.list.components");

        var listView = new RRListView<>();
        listView.getItems().addAll(
            "Project 1 - Minecraft Mod",
            "Project 2 - Fabric Plugin",
            "Project 3 - Forge Extension",
            "Project 4 - Neoforge Addon",
            "Project 5 - Quilt Mod",
            "Project 6 - Bukkit Plugin",
            "Project 7 - Spigot Extension",
            "Project 8 - Paper Addon"
        );
        listView.setPrefHeight(250);
        listView.setMinHeight(200);
        listView.getStyleClass().add("theme-example-list-view");

        var table = new TableView<ProjectData>();
        var nameColumn = new TableColumn<ProjectData, String>("Name");
        var typeColumn = new TableColumn<ProjectData, String>("Type");
        var statusColumn = new TableColumn<ProjectData, String>("Status");

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

        var primaryButton = new RRButton("Primary Button");
        primaryButton.setVariant(RRButton.ButtonVariant.PRIMARY);

        var secondaryButton = new RRButton("Secondary Button");
        secondaryButton.setVariant(RRButton.ButtonVariant.SECONDARY);

        var dangerButton = new RRButton("Danger Button");
        dangerButton.setVariant(RRButton.ButtonVariant.DANGER);

        var successButton = new RRButton("Success Button");
        successButton.setVariant(RRButton.ButtonVariant.SUCCESS);

        buttonRow1.getChildren().addAll(primaryButton, secondaryButton, dangerButton, successButton);

        var buttonRow2 = new RRHBox(12);
        buttonRow2.setAlignment(Pos.CENTER_LEFT);

        var ghostButton = new RRButton("Ghost Button");
        ghostButton.setVariant(RRButton.ButtonVariant.GHOST);

        var smallButton = new RRButton("Small Button");
        smallButton.setButtonSize(RRButton.ButtonSize.SMALL);

        var largeButton = new RRButton("Large Button");
        largeButton.setButtonSize(RRButton.ButtonSize.LARGE);

        var iconButton = new RRButton();
        iconButton.setIcon(FontAwesomeSolid.STAR);
        iconButton.setVariant(RRButton.ButtonVariant.GHOST);

        buttonRow2.getChildren().addAll(ghostButton, smallButton, largeButton, iconButton);

        buttonSection.addContent(buttonRow1, buttonRow2);
        return buttonSection;
    }

    private HBox createFooterSection() {
        var footer = new RRHBox(12);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(16, 0, 0, 0));

        var closeButton = new RRButton("railroad.home.settings.appearance.preview.close");
        closeButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        closeButton.setOnAction(e -> stage.close());

        var applyButton = new RRButton("railroad.home.settings.appearance.preview.apply");
        applyButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        applyButton.setOnAction($ -> {
            // Apply the theme to the main application
            ThemeManager.setTheme(themeName.replace(".css", ""));
            stage.close();
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

    public record ProjectData(SimpleStringProperty name, SimpleStringProperty type, SimpleStringProperty status) {
        public ProjectData(String name, String type, String status) {
            this(new SimpleStringProperty(name), new SimpleStringProperty(type), new SimpleStringProperty(status));
        }
    }
}
