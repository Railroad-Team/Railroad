package io.github.railroad.utility.templates.json.model.item;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import io.github.railroad.project.Project;
import io.github.railroad.project.lang.LangProvider;
import io.github.railroad.utility.Gsons;
import io.github.railroad.utility.helper.JavaFXHelper;
import io.github.railroad.utility.templates.json.JsonTemplate;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ItemGeneratedModel extends JsonTemplate {

	public ItemGeneratedModel(@NotNull Project project, String folder, String fileName) {
		super(project, folder, fileName);
	}

	private TextField layer0Texture = new TextField();
	private Label layer0Label = new Label(fromLang("layer0"));

	@Override
	public void openWindow(Stage stage) {
		clearCache();

		layer0Label.setPadding(new Insets(3));

		layer0Texture.setOnInputMethodTextChanged(e -> System.out.println(e.getCommitted()));

		final var createBtn = new Button(LangProvider.fromLang("buttons.create"));

		createBtn.setOnAction(click -> {
			var jsonObject = new JsonObject();
			jsonObject.addProperty("parent", "item/generated");

			var texturesObj = new JsonObject();
			texturesObj.addProperty("layer0", layer0Texture.getText());

			jsonObject.add("textures", texturesObj);

			var file = new File(folderName, fileName + ".json");

			if (file.exists()) {
				var alert = new Alert(AlertType.ERROR);
				alert.setHeaderText("File already exists");
				alert.show();
			}

			try (var writer = new FileWriter(file)) {
				Gsons.JSON_CREATING_GSON.toJson(jsonObject, writer);
				stage.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});

		final var hbox = new HBox(5, layer0Label, layer0Texture, createBtn);
		hbox.setPadding(new Insets(10));
		JavaFXHelper.setNodeStyle(project.getTheme(), layer0Label, layer0Texture, createBtn, hbox);
		final var scene = new Scene(hbox);

		stage.setTitle("Item Generated Json File");
		stage.sizeToScene();
		stage.requestFocus();
		stage.centerOnScreen();
		stage.setResizable(true);
		stage.setScene(scene);
		stage.showAndWait();
		// });
	}

	@Override
	protected void clearCache() {
		super.clearCache();
		layer0Texture = new TextField();
	}

}