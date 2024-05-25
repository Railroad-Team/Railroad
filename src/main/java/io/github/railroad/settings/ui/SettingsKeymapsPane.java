package io.github.railroad.settings.ui;

import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class SettingsKeymapsPane extends RRVBox {

        public SettingsKeymapsPane() {
            var title = new Label("Keymaps");
            title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
            title.prefWidthProperty().bind(widthProperty());
            title.setAlignment(Pos.CENTER);

            setPadding(new Insets(10));
            setSpacing(10);

            //TODO add items to table from config
            var keymapsList = new TableView<>();
            TableColumn keyColumn = new TableColumn("Key");
            TableColumn actionColumn = new TableColumn("Action");

            keyColumn.setMinWidth(150);
            keyColumn.setResizable(false);
            actionColumn.setMinWidth(300);
            actionColumn.setResizable(false);

            keymapsList.setMaxWidth(450);

            keymapsList.getColumns().addAll(keyColumn, actionColumn);
            keymapsList.columnResizePolicyProperty().set(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

            getChildren().addAll(title, keymapsList);
        }
}
