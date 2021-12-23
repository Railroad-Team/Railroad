package io.github.railroad.utility.helper;

import java.awt.Color;

import io.github.railroad.project.settings.theme.Theme;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;

/**
 * 
 * @author matyrobbrt
 *
 */
public class JavaFXHelper {

	private JavaFXHelper() {
	}

	public static void setBackgroundColour(Node node, Color color) {
		node.setStyle("-fx-background-color: " + ColorHelper.toHex(color));
	}

	public static void setNodeStyle(Theme theme, Node... nodes) {
		for (Node node : nodes) {
			if (node instanceof Button btn) {
				setBackgroundColour(btn, theme.getButtonColor());
			} else if (node instanceof Label label) {
				label.setTextFill(ColorHelper.awtColourToFx(theme.getTextColor()));
			} else if (node instanceof TextField textField) {
				setBackgroundColour(textField, theme.getTextFieldColor());
			} else if (node instanceof ChoiceBox<?> box) {
				setBackgroundColour(box, theme.getTextFieldColor());
			} else if (node instanceof Region region) {
				region.setBackground(new Background(new BackgroundFill(
						ColorHelper.awtColourToFx(theme.getBackgroundColor()), CornerRadii.EMPTY, Insets.EMPTY)));
			}
		}
	}

}
