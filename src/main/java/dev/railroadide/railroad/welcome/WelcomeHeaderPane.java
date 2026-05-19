package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRCard;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.TextFieldSize;
import dev.railroadide.railroad.welcome.project.ui.widget.ProjectSortComboBox;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
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
        super(18);
        getStyleClass().add("welcome-card");

        searchField = new RRTextField("railroad.home.welcome.projectsearch");
        searchField.setTextFieldSize(TextFieldSize.LARGE);
        searchField.getStyleClass().add("welcome-search-input");
        sortComboBox = new ProjectSortComboBox();
        var searchBar = createSearchBar();

        var heroSection = createHeroSection();
        var statsBar = createStatsBar();
        var options = createOptionsRow(searchBar);

        var content = new VBox(heroSection, statsBar, options);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("welcome-header-content");
        VBox.setVgrow(options, Priority.ALWAYS);

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

        var textGroup = new VBox(welcomeMessage, title, subtitle);
        textGroup.getStyleClass().add("welcome-hero-text");

        var heroRow = new HBox(heroIcon, textGroup);
        heroRow.getStyleClass().add("welcome-hero");
        heroRow.setAlignment(Pos.CENTER_LEFT);

        return new VBox(heroRow);
    }

    private HBox createStatsBar() {
        var projectsIcon = new FontIcon(FontAwesomeSolid.LIST);
        projectsIcon.getStyleClass().add("welcome-stat-icon");

        projectsStatLabel.getStyleClass().add("welcome-stat-label");
        var projectsStat = new HBox(projectsIcon, projectsStatLabel);
        projectsStat.getStyleClass().add("welcome-stat");
        projectsStat.setAlignment(Pos.CENTER_LEFT);

        var tipLabel = new LocalizedLabel("railroad.home.welcome.header.tip");
        tipLabel.getStyleClass().add("welcome-tip-label");
        var tipIcon = new FontIcon(FontAwesomeSolid.LIGHTBULB);
        tipIcon.getStyleClass().add("welcome-tip-icon");
        var tip = new HBox(tipIcon, tipLabel);
        tip.getStyleClass().add("welcome-tip");
        tip.setAlignment(Pos.CENTER_LEFT);

        var statsBar = new HBox(projectsStat, tip);
        statsBar.getStyleClass().add("welcome-stats-bar");
        statsBar.setAlignment(Pos.CENTER_LEFT);
        statsBar.setFillHeight(false);

        return statsBar;
    }

    private HBox createOptionsRow(HBox searchBar) {
        var options = new HBox(searchBar, sortComboBox);
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

        var searchContainer = new HBox(searchIcon, searchField, clearButton);
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
