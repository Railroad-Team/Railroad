package io.github.railroad.menu.json.item;

import java.io.File;

import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
import io.github.railroad.project.lang.LangProvider;
import io.github.railroad.utility.helper.ColorHelper;
import io.github.railroad.utility.helper.JavaFXHelper;
import io.github.railroad.utility.templates.json.model.item.ItemGeneratedModel;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

/**
 * 
 * @author matyrobbrt
 *
 */
public class ItemModelMenuItem extends JsonFileMenuItem {

	public final Image icon = new Image(Railroad.class.getResourceAsStream("/icons/menu/json/item_model.png"));
	public final ImageView iconView = new ImageView(icon);

	public final Project project;

	public ItemModelMenuItem(Project project) {
		super(LangProvider.fromLang("menuBar.json.itemModel"));
		this.project = project;
		this.setOnAction(this::executeClick);
		this.setGraphic(iconView);
	}

	@SuppressWarnings("rawtypes")
	private ChoiceBox cb = new ChoiceBox<>();
	private DirectoryChooser directoryChooser = new DirectoryChooser();
	private File selectedDirectory;
	private TextField pathArea = new TextField();
	private TextField fileName = new TextField();
	private Label fileNameLabel = new Label(" " + LangProvider.fromLang("textArea.fileName") + ": ");
	private Label modelType = new Label(" " + fromLang("modelTypeField"));
	private Label pathLabel = new Label(" " + LangProvider.fromLang("textArea.selectPath") + ": ");

	private VBox vbox = new VBox();

	@SuppressWarnings("unchecked")
	private void executeClick(ActionEvent event) {
		clearCache();

		cb.getItems().add("Item Generated");

		var browse = new Button(LangProvider.fromLang("buttons.browse"));
		browse.setOnAction(e -> {
			selectedDirectory = directoryChooser.showDialog(stage);
			pathArea.setText(selectedDirectory.getPath());
		});
		browse.setStyle("-fx-background-color: " + ColorHelper.toHex(this.project.getTheme().getButtonColor()));

		var next = new Button(LangProvider.fromLang("buttons.next"));
		next.setOnAction(this::nextButtonClick);
		next.setStyle("-fx-background-color: " + ColorHelper.toHex(this.project.getTheme().getButtonColor()));

		var cancel = new Button(LangProvider.fromLang("buttons.cancel"));
		cancel.setOnAction(e -> stage.close());
		cancel.setStyle("-fx-background-color: " + ColorHelper.toHex(this.project.getTheme().getButtonColor()));

		pathArea.autosize();
		pathArea.deselect();

		fileNameLabel.setPadding(new Insets(3));
		modelType.setPadding(new Insets(3));
		pathLabel.setPadding(new Insets(3));

		JavaFXHelper.setNodeStyle(project.getTheme(), fileNameLabel, modelType, pathLabel, 
				vbox, pathArea, fileName, cb);

		vbox.setSpacing(5);
		vbox.setPadding(new Insets(5));
		vbox.getChildren().add(new HBox(modelType, cb));
		vbox.getChildren().add(new HBox(fileNameLabel, fileName));
		vbox.getChildren().add(new HBox(3, pathLabel, pathArea, browse));

		var btnBox = new HBox(3, cancel, next);
		btnBox.setAlignment(Pos.BOTTOM_RIGHT);
		vbox.getChildren().add(btnBox);

		final var scene = new Scene(vbox);
		stage.setScene(scene);

		stage.setTitle("Item Model Json File");
		stage.getIcons().add(icon);
		stage.setWidth(400);
		stage.setResizable(false);
		stage.centerOnScreen();
		stage.requestFocus();
		stage.showAndWait();
	}

	private void nextButtonClick(ActionEvent event) {
		if (cb.getSelectionModel().isEmpty()) {
			stage.close();
			var alert = new Alert(AlertType.ERROR);
			alert.setHeaderText(LangProvider.fromLang("alert.typeNotSelected"));
			alert.show();
			return;
		}
		if (fileName.getText().equalsIgnoreCase("")) {
			stage.close();
			var alert = new Alert(AlertType.ERROR);
			alert.setHeaderText(LangProvider.fromLang("alert.noFileName"));
			alert.show();
			return;
		}
		if (cb.getSelectionModel().getSelectedIndex() == 0) {
			new ItemGeneratedModel(project, pathArea.getText(), fileName.getText()).openWindow(stage);
			clearCache();
		}
	}

	private void clearCache() {
		cb = new ChoiceBox<>();
		vbox = new VBox();
		pathArea.setText("");
		fileName.setText("");
		directoryChooser.setInitialDirectory(this.project.getProjectFolder());
	}


}