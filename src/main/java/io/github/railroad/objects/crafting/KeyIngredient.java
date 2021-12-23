package io.github.railroad.objects.crafting;

import com.google.gson.JsonObject;

public class KeyIngredient {

	public char key;
	public String ingredient;
	
	public KeyIngredient(char key, String ingredient) {
		this.key = key;
		this.ingredient = ingredient;
	}
	
	public void toJson(JsonObject obj) {
		if (ingredient == null)
			return;
		
		if (ingredient.startsWith("#")) {
			JsonObject serialized = new JsonObject();
			serialized.addProperty("tag", ingredient.substring(1));
			obj.add(Character.toString(key), serialized);
		} else {
			JsonObject serialized = new JsonObject();
			serialized.addProperty("item", ingredient);
			obj.add(Character.toString(key), serialized);
		}
	}
	
}
