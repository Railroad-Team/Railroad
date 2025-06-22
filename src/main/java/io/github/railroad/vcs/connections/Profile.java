package io.github.railroad.vcs.connections;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

// TODO: Reimplement this, because holding the raw password on disk and memory is a security risk (do we even need it?)
public class Profile implements JsonSerializable<JsonObject> {
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty password = new SimpleStringProperty();
    private final StringProperty accessToken = new SimpleStringProperty();
    private final StringProperty alias = new SimpleStringProperty();
    private final BooleanProperty toDelete = new SimpleBooleanProperty(false);

    public Profile() {
    }

    public String getUsername() {
        return username.get();
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getPassword() {
        return password.get();
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public String getAccessToken() {
        return accessToken.get();
    }

    public StringProperty accessTokenProperty() {
        return accessToken;
    }

    public String getShortAccessToken() {
        return getAccessToken().substring(0, 10) + "...";
    }

    public String getAlias() {
        return alias.get();
    }

    public StringProperty aliasProperty() {
        return alias;
    }

    public boolean isToDelete() {
        return toDelete.get();
    }

    public BooleanProperty toDeleteProperty() {
        return toDelete;
    }

    public void markForDeletion() {
        toDelete.set(true);
    }

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();
        json.addProperty("Username", getUsername());
        json.addProperty("Password", getPassword());
        json.addProperty("AccessToken", getAccessToken());
        json.addProperty("Alias", getAlias());
        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if (json.has("Username") && json.get("Username").isJsonPrimitive()) {
            JsonPrimitive username = json.getAsJsonPrimitive("Username");
            if (username.isString()) {
                this.username.set(username.getAsString());
            }
        }

        if (json.has("Password") && json.get("Password").isJsonPrimitive()) {
            JsonPrimitive password = json.getAsJsonPrimitive("Password");
            if (password.isString()) {
                this.password.set(password.getAsString());
            }
        }

        if (json.has("AccessToken") && json.get("AccessToken").isJsonPrimitive()) {
            JsonPrimitive accessToken = json.getAsJsonPrimitive("AccessToken");
            if (accessToken.isString()) {
                this.accessToken.set(accessToken.getAsString());
            }
        }

        if (json.has("Alias") && json.get("Alias").isJsonPrimitive()) {
            JsonPrimitive alias = json.getAsJsonPrimitive("Alias");
            if (alias.isString()) {
                this.alias.set(alias.getAsString());
            }
        }
    }
}
