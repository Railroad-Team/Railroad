package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.ui.localized.LocalizedComboBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.railroad.welcome.project.ProjectSort;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.EnumMap;
import java.util.Map;

public class ProjectSortComboBox extends LocalizedComboBox<ProjectSort> {
    private static final FontAwesomeSolid DEFAULT_ICON = FontAwesomeSolid.FILTER;
    private static final Map<ProjectSort, FontAwesomeSolid> ICON_BY_SORT = new EnumMap<>(ProjectSort.class);
    private static final Map<ProjectSort, String> DESCRIPTION_KEYS = new EnumMap<>(ProjectSort.class);

    static {
        ICON_BY_SORT.put(ProjectSort.NONE, FontAwesomeSolid.LIST);
        ICON_BY_SORT.put(ProjectSort.NAME, FontAwesomeSolid.SORT_ALPHA_DOWN);
        ICON_BY_SORT.put(ProjectSort.DATE, FontAwesomeSolid.CLOCK);

        DESCRIPTION_KEYS.put(ProjectSort.NONE, "railroad.home.welcome.sort.none.description");
        DESCRIPTION_KEYS.put(ProjectSort.NAME, "railroad.home.welcome.sort.name.description");
        DESCRIPTION_KEYS.put(ProjectSort.DATE, "railroad.home.welcome.sort.date.description");
    }

    private final LocalizationService localizationService = ServiceLocator.getService(LocalizationService.class);
    private final Tooltip sortTooltip = new Tooltip();

    public ProjectSortComboBox() {
        super(ProjectSort::getKey, ProjectSort::valueOf);
        setItems(FXCollections.observableArrayList(ProjectSort.values()));
        getStyleClass().addAll("rr-combo-box", "project-sort-combo");
        setMinWidth(170);
        setPrefWidth(220);
        setMaxWidth(260);
        setVisibleRowCount(ProjectSort.values().length);

        setButtonCell(new ProjectSortListCell(true));
        setCellFactory(listView -> new ProjectSortListCell(false));

        initializeTooltip();

        setValue(ProjectSort.NONE);
    }

    private void initializeTooltip() {
        sortTooltip.getStyleClass().add("project-sort-tooltip");
        sortTooltip.setShowDelay(Duration.millis(180));
        sortTooltip.setShowDuration(Duration.seconds(4));
        setTooltip(sortTooltip);
        updateTooltip(getValue());
        valueProperty().addListener((observable, oldValue, newValue) -> updateTooltip(newValue));
    }

    private void updateTooltip(ProjectSort sort) {
        ProjectSort targetSort = sort != null ? sort : ProjectSort.NONE;
        String sortName = localizationService.get(targetSort.getKey());
        sortTooltip.setText(localizationService.get("railroad.home.welcome.sort.tooltip", sortName));
    }

    private static class ProjectSortListCell extends ListCell<ProjectSort> {
        private final boolean compact;
        private final FontIcon icon = new FontIcon(DEFAULT_ICON);
        private final LocalizedLabel title = new LocalizedLabel("");
        private final LocalizedLabel description = new LocalizedLabel("");
        private final VBox textContainer = new VBox(2);
        private final HBox container = new HBox(10);

        private ProjectSortListCell(boolean compact) {
            this.compact = compact;
            icon.setIconSize(16);
            description.setManaged(!compact);
            description.setVisible(!compact);

            icon.getStyleClass().add("project-sort-option-icon");
            title.getStyleClass().add("project-sort-option-title");
            description.getStyleClass().add("project-sort-option-description");
            textContainer.getStyleClass().add("project-sort-option-text");
            container.getStyleClass().add("project-sort-option");

            textContainer.getChildren().add(title);
            if (!compact) {
                textContainer.getChildren().add(description);
            }

            container.setAlignment(Pos.CENTER_LEFT);
            container.getChildren().addAll(icon, textContainer);
            HBox.setHgrow(textContainer, Priority.ALWAYS);
        }

        @Override
        protected void updateItem(ProjectSort item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            icon.setIconCode(ICON_BY_SORT.getOrDefault(item, DEFAULT_ICON));
            title.setKey(item.getKey());

            if (!compact) {
                description.setKey(DESCRIPTION_KEYS.getOrDefault(item, item.getKey()));
            }

            setGraphic(container);
            setText(null);
        }
    }
}
