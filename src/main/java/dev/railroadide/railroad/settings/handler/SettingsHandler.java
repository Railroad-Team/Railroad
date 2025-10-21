package dev.railroadide.railroad.settings.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.core.settings.Setting;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class SettingsHandler {
    public static final Registry<Setting<?>> SETTINGS_REGISTRY = RegistryManager.createRegistry("settings", new TypeToken<>() {
    });
    private static final SettingsHolder SETTINGS_HOLDER = new SettingsHolder();
    private static final Path SETTINGS_PATH = ConfigHandler.getConfigDirectory().resolve("settings.json");

    public static void init() {
        try {
            if (Files.notExists(SETTINGS_PATH)) {
                createSettings();
            }

            loadSettings();
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to initialize settings file", exception);
        }
    }

    public static void createSettings() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            saveSettings();
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to create settings file", exception);
        }
    }

    public static void loadSettings() {
        try {
            String content = Files.readString(SETTINGS_PATH);
            if (content.isBlank() || content.replace(" ", "").equals("{}")) {
                Railroad.LOGGER.warn("Settings file is empty, resetting to default");
                saveSettings();
                return;
            }

            SETTINGS_HOLDER.fromJson(Railroad.GSON.fromJson(content, JsonObject.class));
        } catch (JsonSyntaxException exception) {
            Railroad.LOGGER.error("Failed to parse settings file, resetting file to default", exception);
            saveSettings();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void saveSettings() {
        Railroad.LOGGER.debug("Saving settings file");

        try {
            String json = Railroad.GSON.toJson(SETTINGS_HOLDER.toJson());
            Files.writeString(SETTINGS_PATH, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to save settings file", exception);
        }
    }

    public static <T> Setting<T> registerSetting(Setting<T> setting) {
        SETTINGS_REGISTRY.register(setting.getId(), setting);
        return setting;
    }

    public static Setting<?> getSetting(String id) {
        return SETTINGS_REGISTRY.get(id);
    }

    public static <T> T getValue(Setting<T> setting) {
        if (setting == null)
            throw new IllegalArgumentException("Setting cannot be null");

        T value = setting.getValue();
        if (value == null && !setting.isCanBeNull())
            throw new IllegalStateException("Setting " + setting.getId() + " cannot be null");

        return value;
    }

    public static <T> T getValue(String id, Class<T> type) {
        Setting<T> setting = getSetting(id, type);
        return setting == null ? null : setting.getValue();
    }

    public static <T> void setValue(String id, Class<T> clazz, T value) {
        Setting<T> setting = getSetting(id, clazz);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not of type " + value.getClass().getName());

        setting.setValue(value);
    }

    public static boolean getBooleanValue(String id) {
        Setting<Boolean> setting = getSetting(id, Boolean.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a boolean setting.");

        Boolean value = setting.getValue();
        return value != null && value; // Return false if the value is null
    }

    public static void setBooleanValue(String id, boolean value) {
        setValue(id, Boolean.class, value);
    }

    public static String getStringValue(String id) {
        Setting<String> setting = getSetting(id, String.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a string setting.");

        return setting.getValue();
    }

    public static void setStringValue(String id, String value) {
        setValue(id, String.class, value);
    }

    public static int getIntValue(String id) {
        Setting<Integer> setting = getSetting(id, Integer.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not an integer setting.");

        Integer value = setting.getValue();
        return value == null ? 0 : value; // Return 0 if the value is null
    }

    public static void setIntValue(String id, int value) {
        setValue(id, Integer.class, value);
    }

    public static double getDoubleValue(String id) {
        Setting<Double> setting = getSetting(id, Double.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a double setting.");

        Double value = setting.getValue();
        return value == null ? 0.0 : value; // Return 0.0 if the value is null
    }

    public static <T> void setValue(Setting<T> setting, T value) {
        if (setting == null)
            throw new IllegalArgumentException("Setting cannot be null");

        setting.setValue(value);
    }

    public static float getFloatValue(String id) {
        Setting<Float> setting = getSetting(id, Float.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a float setting.");

        Float value = setting.getValue();
        return value == null ? 0.0f : value; // Return 0.0f if the value is null
    }

    public static void setFloatValue(String id, float value) {
        setValue(id, Float.class, value);
    }

    public static long getLongValue(String id) {
        Setting<Long> setting = getSetting(id, Long.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a long setting.");

        Long value = setting.getValue();
        return value == null ? 0L : value; // Return 0L if the value is null
    }

    public static void setLongValue(String id, long value) {
        setValue(id, Long.class, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> Setting<T> getSetting(String id, Class<T> type) {
        Setting<?> setting = SETTINGS_REGISTRY.get(id);
        if (setting == null)
            return null;

        Class<?> settingType = setting.getType();
        if (!type.isAssignableFrom(settingType))
            throw new ClassCastException(
                "Setting with ID '" + id + "' is of type " + settingType.getName() + ", cannot cast to " + type.getName());

        return (Setting<T>) setting;
    }

    public static SettingsHolder getSettingsHolder() {
        return SETTINGS_HOLDER;
    }

    public void setDoubleValue(String id, double value) {
        setValue(id, Double.class, value);
    }
}
