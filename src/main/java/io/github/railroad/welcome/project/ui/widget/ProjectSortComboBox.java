package io.github.railroad.welcome.project.ui.widget;

import io.github.railroad.core.ui.localized.LocalizedComboBox;
import io.github.railroad.core.ui.localized.LocalizedListCell;
import io.github.railroad.welcome.project.ProjectSort;
import javafx.collections.FXCollections;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProjectSortComboBox extends LocalizedComboBox<ProjectSort> {
    private static final FontIcon ICON = FontIcon.of(FontAwesomeSolid.FILTER);

    public ProjectSortComboBox() {
        super(ProjectSort::getKey, ProjectSort::valueOf);
        setItems(FXCollections.observableArrayList(ProjectSort.values()));
        setPrefWidth(120);

        setButtonCell(new LocalizedListCell<>(ProjectSort::getKey) {
            @Override
            protected void updateItem(ProjectSort item, boolean empty) {
                super.updateItem(item, empty);

                setGraphic(ICON);
            }
        });

        setValue(ProjectSort.NONE);
    }
}