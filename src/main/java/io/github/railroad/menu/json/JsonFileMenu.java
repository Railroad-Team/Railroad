package io.github.railroad.menu.json;

import org.jetbrains.annotations.NotNull;

import io.github.railroad.menu.json.item.CraftingRecipeMenuItem;
import io.github.railroad.menu.json.item.ItemModelMenuItem;
import io.github.railroad.project.Project;
import io.github.railroad.project.lang.LangProvider;
import javafx.scene.control.Menu;

/**
 * 
 * @author matyrobbrt
 *
 */
public class JsonFileMenu extends Menu {

	public final Project project;

	public JsonFileMenu(@NotNull Project project) {
		super(LangProvider.fromLang("menuBar.json.name"), null, new ItemModelMenuItem(project),
				new CraftingRecipeMenuItem(project));
		this.project = project;
	}
}
