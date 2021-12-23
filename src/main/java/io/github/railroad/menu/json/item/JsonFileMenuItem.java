package io.github.railroad.menu.json.item;

import io.github.railroad.project.lang.LangProvider;
import javafx.scene.control.MenuItem;
import javafx.stage.Stage;

/**
 * 
 * @author matyrobbrt
 *
 */
public abstract class JsonFileMenuItem extends MenuItem {

	protected Stage stage = new Stage();

	protected JsonFileMenuItem(String title) {
		super(title);
	}

	public String fromLang(String key) {
		return LangProvider.fromLang("jsonGenerator." + key);
	}

}