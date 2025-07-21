package dev.railroadide.railroad.welcome;

import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.welcome.project.ui.widget.ProjectSortComboBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

@Getter
public class WelcomeHeaderPane extends RRCard {
    private final RRTextField searchField;
    private final ProjectSortComboBox sortComboBox;

    public WelcomeHeaderPane() {
        super(18, new Insets(24, 32, 24, 32));
        setSpacing(18);
        getStyleClass().add("welcome-card");

        var welcomeMessage = new LocalizedText("railroad.home.welcome.greeting");
        welcomeMessage.getStyleClass().add("welcome-message");

        var title = new LocalizedText("railroad.home.welcome.projects");
        title.getStyleClass().add("welcome-title");

        var subtitle = new LocalizedText("railroad.home.welcome.projects.subtitle");
        subtitle.getStyleClass().add("welcome-subtitle");

        searchField = new RRTextField("railroad.home.welcome.projectsearch");
        sortComboBox = new ProjectSortComboBox();

        var options = new HBox(14);
        options.getChildren().addAll(searchField, sortComboBox);
        options.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        HBox.setHgrow(sortComboBox, Priority.NEVER);

        var vbox = new VBox(8);
        vbox.getChildren().addAll(welcomeMessage, title, subtitle, options);
        vbox.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(options, Priority.ALWAYS);
        vbox.setPadding(new Insets(8, 12, 8, 12));

        getChildren().clear();
        getChildren().add(vbox);
    }
}