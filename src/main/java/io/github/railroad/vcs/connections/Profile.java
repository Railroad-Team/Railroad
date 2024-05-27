package io.github.railroad.vcs.connections;

import com.google.gson.JsonObject;

public class Profile {
    private String username;
    private String password;
    private String accessToken;
    private String Alias;
    private JsonObject config_obj;
    private boolean toDelete;

    public Profile() {}

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getShortAccessToken() {
        return accessToken.substring(0, 10) + "...";
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAlias() {
        return Alias;
    }

    public void setAlias(String alias) {
        Alias = alias;
    }

    public JsonObject getConfig_obj() {
        return config_obj;
    }

    public void setConfig_obj(JsonObject config_obj) {
        this.config_obj = config_obj;
    }

    public boolean toDelete() {
        return toDelete;
    }

    public void markDelete() {
        this.toDelete = true;
    }
}
