package io.github.railroad.project.ui.project;

import io.github.railroad.project.ProjectSort;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.util.StringConverter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

public class ProjectSortComboBox extends ComboBox<ProjectSort> {
    private static final FontIcon ICON = FontIcon.of(FontAwesomeSolid.FILTER);

    public ProjectSortComboBox() {
        setItems(FXCollections.observableArrayList(ProjectSort.values()));
        setPrefWidth(120);
        setConverter(new StringConverter<>() {
            @Override
            public String toString(ProjectSort object) {
                return object.getName();
            }

            @Override
            public ProjectSort fromString(String string) {
                return ProjectSort.valueOf(string.toUpperCase(Locale.ROOT));
            }
        });

        setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(ProjectSort item, boolean empty) {
                super.updateItem(item, empty);
                setText(item == null ? null : item.getName());
                setGraphic(ICON);
            }
        });

        setValue(ProjectSort.NONE);
    }
}