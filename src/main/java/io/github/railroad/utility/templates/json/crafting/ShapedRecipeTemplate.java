package io.github.railroad.utility.templates.json.crafting;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.google.gson.JsonObject;

import io.github.railroad.objects.crafting.KeyIngredient;
import io.github.railroad.objects.crafting.Pattern;
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
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class ShapedRecipeTemplate extends JsonTemplate {

	public ShapedRecipeTemplate(@NotNull Project project, String folderName, String fileName) {
		super(project, folderName, fileName);
	}

	private TextField pattern1Field = new TextField();
	private TextField pattern2Field = new TextField();
	private TextField pattern3Field = new TextField();

	private Label labelPattern1 = new Label("Row 1: ");
	private Label labelPattern2 = new Label("Row 2: ");
	private Label labelPattern3 = new Label("Row 3: ");

	private Pattern pattern;

	private int currentKey;

	private List<KeyIngredient> ingredients;

	@Override
	public void openWindow(Stage stage) {
		clearCache();

		var vbox = new VBox();

		var patternVbox = new VBox(3, new HBox(2, labelPattern1, pattern1Field),
				new HBox(2, labelPattern2, pattern2Field), new HBox(2, labelPattern3, pattern3Field));

		var nextBtn = new Button(LangProvider.fromLang("buttons.next"));
		nextBtn.setOnAction(e -> nextButtonClick(stage));

		patternVbox.setPadding(new Insets(4));

		JavaFXHelper.setNodeStyle(project.getTheme(), vbox, pattern1Field, pattern2Field, pattern3Field, labelPattern1,
				labelPattern2, labelPattern3, nextBtn);

		vbox.getChildren().addAll(patternVbox, nextBtn);

		stage.setScene(new Scene(vbox));
		stage.setTitle("Shaped Recipe Json File");
		stage.sizeToScene();
		stage.centerOnScreen();
		stage.setResizable(true);
	}

	private void nextButtonClick(Stage stage) {
		pattern = new Pattern(pattern1Field.getText(), pattern2Field.getText(), pattern3Field.getText());
		if (Boolean.FALSE.equals(pattern.isValid().getKey())) {
			var alert = new Alert(AlertType.ERROR);
			alert.setContentText(pattern.isValid().getValue());
			alert.show();
		} else {
			currentKey = -1;
			nextKey(stage);
		}
	}

	private void nextKey(Stage stage) {
		currentKey++;
		if (currentKey < pattern.getKeys().size()) {
			var label = new Label(LangProvider.fromLang("jsonGenerator.ingredientForKey").replace('%',
					pattern.getKeys().get(currentKey)));
			var ingredientTextField = new TextField();
			var contBtn = new Button(LangProvider.fromLang("buttons.continue"));
			
			label.setTranslateY(2);

			contBtn.setOnAction(ac -> {
				if (ingredientTextField.getText() == "")
					return;
				ingredients.add(new KeyIngredient(pattern.getKeys().get(currentKey), ingredientTextField.getText()));
				nextKey(stage);
			});
			
			ingredientTextField.setOnKeyPressed(keyEvent -> {
				if (keyEvent.getCode() == KeyCode.ENTER)
					contBtn.fire();
			});

			var hbox = new HBox(4, label, ingredientTextField, contBtn);
			var vbox = new VBox(3, pattern.renderPattern(project.getTheme()), hbox);
			hbox.setPadding(new Insets(10));

			JavaFXHelper.setNodeStyle(project.getTheme(), label, contBtn, ingredientTextField, vbox);

			stage.setScene(new Scene(vbox));
			stage.sizeToScene();
		} else {
			var resultItemLabel = new Label(fromLang("result"));
			var countLabel = new Label(fromLang("count"));
			var nbtLabel = new Label("NBT:    ");

			var resultItemField = new TextField();
			var countField = new TextField();
			var nbtField = new TextField();

			var createBtn = new Button(LangProvider.fromLang("buttons.create"));
			createBtn.setOnAction(ac -> {
				if (resultItemField.getText() == "")
					return;
				
				var obj = new JsonObject();
				obj.addProperty("type", "minecraft:crafting_shaped");

				obj.add("pattern", pattern.toJsonArray());

				var keyObj = new JsonObject();
				ingredients.forEach(ingr -> {
					ingr.toJson(keyObj);
				});

				obj.add("key", keyObj);

				var resultObj = new JsonObject();
				resultObj.addProperty("item", resultItemField.getText());
				if (countField.getText() != "")
					resultObj.addProperty("count", countField.getText());
				if (nbtField.getText() != "")
					resultObj.addProperty("nbt", nbtField.getText());

				obj.add("result", resultObj);

				var file = new File(folderName, fileName + ".json");

				if (file.exists()) {
					var alert = new Alert(AlertType.ERROR);
					alert.setHeaderText(LangProvider.fromLang("alert.fileExists"));
					alert.show();
				}

				try (var writer = new FileWriter(file)) {
					Gsons.JSON_CREATING_GSON.toJson(obj, writer);
					stage.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			});

			var vbox = new VBox(4, new HBox(2, resultItemLabel, resultItemField), new HBox(2, countLabel, countField),
					new HBox(2, nbtLabel, nbtField), createBtn);

			vbox.setPadding(new Insets(5));

			JavaFXHelper.setNodeStyle(project.getTheme(), createBtn, vbox, resultItemLabel, countLabel, resultItemField,
					countField, nbtField, nbtLabel);

			stage.setScene(new Scene(vbox));
			stage.sizeToScene();
		}
	}

	@Override
	protected void clearCache() {
		super.clearCache();
		pattern1Field.setText("");
		pattern2Field.setText("");
		pattern3Field.setText("");
		if (pattern != null)
			pattern.deletePattern();
		ingredients = new LinkedList<>();
	}

}
