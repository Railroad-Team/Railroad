package io.github.railroad.settings.handler;

import com.google.gson.JsonObject;
import io.github.railroad.utility.JsonSerializable;
import javafx.scene.Node;

public class SettingNode<T extends Node> implements JsonSerializable<JsonObject> {
    private T node;

    public SettingNode(T node) {
        this.node = node;
    }

    public T getNode() {
        return node;
    }

    @Override
    public JsonObject toJson() {
        return null;
    }

    @Override
    public void fromJson(JsonObject json) {

    }
}
