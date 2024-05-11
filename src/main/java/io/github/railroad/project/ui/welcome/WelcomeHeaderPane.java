package io.github.railroad.project.ui.welcome;

import io.github.railroad.project.ui.project.ProjectSearchField;
import io.github.railroad.project.ui.project.ProjectSortComboBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

public class WelcomeHeaderPane extends HBox {
    private final ProjectSearchField searchField;
    private final ProjectSortComboBox sortComboBox;

    public WelcomeHeaderPane() {
        // Creating and styling children
        var title = new Text();
        title.setText("Projects List");
        title.setStyle("-fx-font-size: 20px;");

        searchField = new ProjectSearchField();
        sortComboBox = new ProjectSortComboBox();

        // Containers setup
        var options = new HBox();
        options.getChildren().addAll(searchField, sortComboBox);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        VBox.setVgrow(options, Priority.ALWAYS);
        options.setAlignment(Pos.BOTTOM_LEFT);
        options.setSpacing(10);

        var vbox = new VBox();
        vbox.getChildren().addAll(title, options);
        HBox.setHgrow(vbox, Priority.ALWAYS);

        getChildren().add(vbox);

        // Styling
        setPadding(new Insets(10));
        setStyle("-fx-background-color: #f0f0f0;");
        setAlignment(Pos.CENTER_LEFT);
    }

    public ProjectSearchField getSearchField() {
        return searchField;
    }

    public ProjectSortComboBox getSortComboBox() {
        return sortComboBox;
    }
}