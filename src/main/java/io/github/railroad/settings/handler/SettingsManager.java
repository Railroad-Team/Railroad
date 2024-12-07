package io.github.railroad.settings.handler;

import com.google.gson.JsonObject;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.ObservableList;

import java.util.List;

public class SettingsManager {

    public SettingsManager() {
        //          STRING, SettingsCategory, SettingComponentType, List<Type>, Type
        //register - Name, Category, ComponentType (Dropdown, checkbox etc), Options (if it can have multiple), default value/option
        //Type can be int, string, boolean, color, theme etc. Maybe make each type have a method to convert to a string/back?
        //Will need some way to convert List<Type> into ComponentType, and same for default value/option
        //
        registerSetting();
    }
    //Load settings from plugins and default settings
    public void loadSettings() {}

    //Register settings to the list
    public void registerSetting() {}

    //Add settings to menu
    public void initSettings() {}

    //Search for settings
    public List<SettingData> searchSettings(String search) { return null; }

    //Set setting value
    public  void setSetting(String settingId, JsonObject value) { }

    //Get setting value
    public JsonObject getSetting(String settingId) { return null; }
}
