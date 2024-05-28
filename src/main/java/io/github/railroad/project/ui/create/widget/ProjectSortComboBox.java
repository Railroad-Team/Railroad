package io.github.railroad.project.ui.create.widget;

import io.github.railroad.project.ProjectSort;
import io.github.railroad.ui.localized.LocalizedComboBox;
import io.github.railroad.utility.localization.L18n;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ProjectSortComboBox extends LocalizedComboBox<ProjectSort> {
    private static final FontIcon ICON = FontIcon.of(FontAwesomeSolid.FILTER);

    public ProjectSortComboBox() {
        super(ProjectSort::getKey, ProjectSort::valueOf);
        setItems(FXCollections.observableArrayList(ProjectSort.values()));
        setPrefWidth(120);

        setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ProjectSort item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? null : L18n.localize(item.getKey()));
                L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
                    if (item != null) {
                        setText(L18n.localize(item.getKey()));
                    } else {
                        setText(null);
                    }
                });
                setGraphic(ICON);
            }
        });

        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
            setItems(FXCollections.observableArrayList(ProjectSort.values()));
        });

        setValue(ProjectSort.NONE);
    }
}