package dev.railroadide.railroad.welcome;

import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.welcome.project.ui.widget.ProjectSortComboBox;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

@Getter
public class WelcomeHeaderPane extends RRCard {
    private static final PseudoClass FOCUSED_PSEUDO_CLASS = PseudoClass.getPseudoClass("focused");
    private final RRTextField searchField;
    private final ProjectSortComboBox sortComboBox;
    private final LocalizedLabel projectsStatLabel = new LocalizedLabel("railroad.home.welcome.stats.projects", 0);

    public WelcomeHeaderPane() {
        super(18, new Insets(24, 32, 24, 32));
        setSpacing(18);
        getStyleClass().add("welcome-card");

        searchField = new RRTextField("railroad.home.welcome.projectsearch");
        searchField.setTextFieldSize(RRTextField.TextFieldSize.LARGE);
        searchField.getStyleClass().add("welcome-search-input");
        sortComboBox = new ProjectSortComboBox();
        var searchBar = createSearchBar();

        var heroSection = createHeroSection();
        var statsBar = createStatsBar();
        var options = createOptionsRow(searchBar);

        var content = new VBox(18, heroSection, statsBar, options);
        content.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(options, Priority.ALWAYS);
        content.setPadding(new Insets(8, 12, 8, 12));

        getChildren().setAll(content);
        initializeProjectStats();
    }

    private VBox createHeroSection() {
        var welcomeMessage = new LocalizedText("railroad.home.welcome.greeting");
        welcomeMessage.getStyleClass().add("welcome-message");

        var title = new LocalizedText("railroad.home.welcome.projects");
        title.getStyleClass().add("welcome-title");

        var subtitle = new LocalizedText("railroad.home.welcome.projects.subtitle");
        subtitle.getStyleClass().add("welcome-subtitle");

        var heroIcon = new FontIcon(FontAwesomeSolid.ROCKET);
        heroIcon.getStyleClass().add("welcome-hero-icon");

        var textGroup = new VBox(4, welcomeMessage, title, subtitle);
        textGroup.getStyleClass().add("welcome-hero-text");

        var heroRow = new HBox(16, heroIcon, textGroup);
        heroRow.getStyleClass().add("welcome-hero");
        heroRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(heroRow);
    }

    private HBox createStatsBar() {
        var projectsIcon = new FontIcon(FontAwesomeSolid.LIST);
        projectsIcon.getStyleClass().add("welcome-stat-icon");

        projectsStatLabel.getStyleClass().add("welcome-stat-label");
        var projectsStat = new HBox(8, projectsIcon, projectsStatLabel);
        projectsStat.getStyleClass().add("welcome-stat");
        projectsStat.setAlignment(Pos.CENTER_LEFT);

        var tipLabel = new LocalizedLabel("railroad.home.welcome.header.tip");
        tipLabel.getStyleClass().add("welcome-tip-label");
        var tipIcon = new FontIcon(FontAwesomeSolid.LIGHTBULB);
        tipIcon.getStyleClass().add("welcome-tip-icon");
        var tip = new HBox(6, tipIcon, tipLabel);
        tip.getStyleClass().add("welcome-tip");
        tip.setAlignment(Pos.CENTER_LEFT);

        var statsBar = new HBox(18, projectsStat, tip);
        statsBar.getStyleClass().add("welcome-stats-bar");
        statsBar.setAlignment(Pos.CENTER_LEFT);
        statsBar.setFillHeight(false);

        return statsBar;
    }

    private HBox createOptionsRow(HBox searchBar) {
        var options = new HBox(14, searchBar, sortComboBox);
        options.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);
        HBox.setHgrow(sortComboBox, Priority.NEVER);
        options.getStyleClass().add("welcome-options-row");
        return options;
    }

    private HBox createSearchBar() {
        var searchIcon = new FontIcon(FontAwesomeSolid.SEARCH);
        searchIcon.getStyleClass().add("welcome-search-icon");

        var clearIcon = new FontIcon(FontAwesomeSolid.TIMES);
        clearIcon.getStyleClass().add("welcome-search-clear-icon");

        var clearButton = new Button();
        clearButton.getStyleClass().add("welcome-search-clear");
        clearButton.setGraphic(clearIcon);
        clearButton.setFocusTraversable(false);
        clearButton.setOnAction(event -> searchField.clear());
        clearButton.visibleProperty().bind(searchField.textProperty().isNotEmpty());
        clearButton.managedProperty().bind(clearButton.visibleProperty());

        var searchContainer = new HBox(10, searchIcon, searchField, clearButton);
        searchContainer.getStyleClass().add("welcome-search-container");
        searchContainer.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        searchField.focusedProperty().addListener((obs, oldValue, newValue) ->
            searchContainer.pseudoClassStateChanged(FOCUSED_PSEUDO_CLASS, newValue)
        );

        return searchContainer;
    }

    private void initializeProjectStats() {
        updateProjectStats();
        Railroad.PROJECT_MANAGER.getProjects().addListener((ListChangeListener<Project>) change -> updateProjectStats());
    }

    private void updateProjectStats() {
        int totalProjects = Railroad.PROJECT_MANAGER.getProjects().size();
        projectsStatLabel.setKey("railroad.home.welcome.stats.projects", totalProjects);
    }
}
