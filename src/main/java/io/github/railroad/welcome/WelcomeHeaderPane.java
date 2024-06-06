package io.github.railroad.welcome;

import io.github.railroad.project.ui.create.widget.ProjectSortComboBox;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedText;
import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WelcomeHeaderPane extends RRHBox {
    private final LocalizedTextField searchField;
    private final ProjectSortComboBox sortComboBox;

    public WelcomeHeaderPane() {
        // Creating and styling children
        var title = new LocalizedText("railroad.home.welcome.projects");
        title.setStyle("-fx-font-size: 20px;");

        searchField = new LocalizedTextField("railroad.home.welcome.projectsearch");
        sortComboBox = new ProjectSortComboBox();

        // Containers setup
        var options = new RRHBox();
        options.getChildren().addAll(searchField, sortComboBox);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        VBox.setVgrow(options, Priority.ALWAYS);
        options.setAlignment(Pos.BOTTOM_LEFT);
        options.setSpacing(10);

        var vbox = new RRVBox();
        vbox.getChildren().addAll(title, options);
        HBox.setHgrow(vbox, Priority.ALWAYS);

        getChildren().add(vbox);

        // Styling
        setPadding(new Insets(10));
        setAlignment(Pos.CENTER_LEFT);
    }

    public LocalizedTextField getSearchField() {
        return searchField;
    }

    public ProjectSortComboBox getSortComboBox() {
        return sortComboBox;
    }
}