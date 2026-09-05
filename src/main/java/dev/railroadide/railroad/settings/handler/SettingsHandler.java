package dev.railroadide.railroad.settings.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import dev.railroadide.railroad.settings.Setting;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

/**
 * Coordinates registration, persistence, and typed access to application
 * settings.
 *
 * <p>
 * The handler keeps settings in a shared registry and stores persisted
 * values in the application's settings file.
 * </p>
 */
public class SettingsHandler {
    private static final SettingsHolder SETTINGS_HOLDER = new SettingsHolder();
    private static final Registry<Setting<?>> SETTINGS_REGISTRY_DELEGATE = RegistryManager
        .createOrderedRegistry("settings", new TypeToken<>() {
        });
    /**
     * Registry containing all settings available to the application.
     *
     * <p>
     * Registering through this registry also hydrates a setting when a value
     * for it was loaded before the setting itself was registered.
     * </p>
     */
    public static final Registry<Setting<?>> SETTINGS_REGISTRY = new Registry<>() {
        @Override
        public String getId() {
            return SETTINGS_REGISTRY_DELEGATE.getId();
        }

        @Override
        public Type getType() {
            return SETTINGS_REGISTRY_DELEGATE.getType();
        }

        @Override
        public Setting<?> register(String id, Setting<?> item) {
            Setting<?> registered = SETTINGS_REGISTRY_DELEGATE.register(id, item);
            SETTINGS_HOLDER.tryHydratePendingSetting(id, registered);
            return registered;
        }

        @Override
        public Setting<?> unregister(String id) {
            return SETTINGS_REGISTRY_DELEGATE.unregister(id);
        }

        @Override
        public Setting<?> get(String id) {
            return SETTINGS_REGISTRY_DELEGATE.get(id);
        }

        @Override
        public boolean contains(String id) {
            return SETTINGS_REGISTRY_DELEGATE.contains(id);
        }

        @Override
        public List<Setting<?>> values() {
            return SETTINGS_REGISTRY_DELEGATE.values();
        }

        @Override
        public List<String> keys() {
            return SETTINGS_REGISTRY_DELEGATE.keys();
        }

        @Override
        public Map<String, Setting<?>> entries() {
            return SETTINGS_REGISTRY_DELEGATE.entries();
        }
    };
    private static final Path SETTINGS_PATH = ConfigHandler.getConfigDirectory().resolve("settings.json");

    /**
     * Initializes the settings file and loads its values into registered
     * settings.
     *
     * <p>
     * If the file does not exist, it is created before loading. Initialization
     * failures are logged and do not escape this method.
     * </p>
     */
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

    /**
     * Creates the settings directory and an initial settings file.
     *
     * <p>
     * If the file cannot be written, the failure is logged.
     * </p>
     */
    public static void createSettings() {
        try {
            Files.createDirectories(SETTINGS_PATH.getParent());
            saveSettings();
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to create settings file", exception);
        }
    }

    /**
     * Loads persisted values from the settings file.
     *
     * <p>
     * An empty or malformed file is replaced with the current default
     * settings. I/O failures are surfaced as runtime exceptions.
     * </p>
     */
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

    /**
     * Serializes the current persisted settings to the settings file.
     *
     * <p>
     * Write failures are logged and do not escape this method.
     * </p>
     */
    public static void saveSettings() {
        Railroad.LOGGER.debug("Saving settings file");

        try {
            String json = Railroad.GSON.toJson(SETTINGS_HOLDER.toJson());
            Files.writeString(SETTINGS_PATH, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to save settings file", exception);
        }
    }

    /**
     * Registers a setting using its own identifier.
     *
     * @param <T> value type of the setting
     * @param setting setting to register
     * @return the registered setting
     */
    public static <T> Setting<T> registerSetting(Setting<T> setting) {
        SETTINGS_REGISTRY.register(setting.getId(), setting);
        return setting;
    }

    /**
     * Finds a setting by identifier without imposing a value type.
     *
     * @param id identifier of the setting
     * @return the matching setting, or {@code null} when no setting is registered
     */
    public static Setting<?> getSetting(String id) {
        return SETTINGS_REGISTRY.get(id);
    }

    /**
     * Reads and validates the value of a setting.
     *
     * @param <T> value type of the setting
     * @param setting setting whose value should be read
     * @return the setting's current value
     * @throws IllegalArgumentException if {@code setting} is {@code null}
     * @throws IllegalStateException if the setting disallows a {@code null} value
     */
    public static <T> T getValue(Setting<T> setting) {
        if (setting == null)
            throw new IllegalArgumentException("Setting cannot be null");

        T value = setting.getValue();
        if (value == null && !setting.isCanBeNull())
            throw new IllegalStateException("Setting " + setting.getId() + " cannot be null");

        return value;
    }

    /**
     * Reads a setting value by identifier and expected value type.
     *
     * @param <T> expected value type
     * @param id identifier of the setting
     * @param type expected value class
     * @return the setting's current value, or {@code null} when no setting has
     *         the supplied identifier
     * @throws ClassCastException if the registered setting has an incompatible type
     */
    public static <T> T getValue(String id, Class<T> type) {
        Setting<T> setting = getSetting(id, type);
        return setting == null ? null : setting.getValue();
    }

    /**
     * Sets a value after locating a setting by identifier and value type.
     *
     * @param <T> value type
     * @param id identifier of the setting
     * @param clazz expected value class
     * @param value new value
     * @throws IllegalArgumentException if no compatible setting is registered
     * @throws ClassCastException if the registered setting has an incompatible type
     */
    public static <T> void setValue(String id, Class<T> clazz, T value) {
        Setting<T> setting = getSetting(id, clazz);
        if (setting == null)
            throw new IllegalArgumentException(
                "Setting " + id + " does not exist or is not of type " + value.getClass().getName());

        setting.setValue(value);
    }

    /**
     * Reads a boolean setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current value, treating a {@code null} value as {@code false}
     * @throws IllegalArgumentException if no boolean setting is registered
     */
    public static boolean getBooleanValue(String id) {
        Setting<Boolean> setting = getSetting(id, Boolean.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a boolean setting.");

        Boolean value = setting.getValue();
        return value != null && value; // Return false if the value is null
    }

    /**
     * Sets a boolean setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new boolean value
     */
    public static void setBooleanValue(String id, boolean value) {
        setValue(id, Boolean.class, value);
    }

    /**
     * Reads a string setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current string value
     * @throws IllegalArgumentException if no string setting is registered
     */
    public static String getStringValue(String id) {
        Setting<String> setting = getSetting(id, String.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a string setting.");

        return setting.getValue();
    }

    /**
     * Sets a string setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new string value
     */
    public static void setStringValue(String id, String value) {
        setValue(id, String.class, value);
    }

    /**
     * Reads an integer setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current value, treating a {@code null} value as zero
     * @throws IllegalArgumentException if no integer setting is registered
     */
    public static int getIntValue(String id) {
        Setting<Integer> setting = getSetting(id, Integer.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not an integer setting.");

        Integer value = setting.getValue();
        return value == null ? 0 : value; // Return 0 if the value is null
    }

    /**
     * Sets an integer setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new integer value
     */
    public static void setIntValue(String id, int value) {
        setValue(id, Integer.class, value);
    }

    /**
     * Reads a double setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current value, treating a {@code null} value as zero
     * @throws IllegalArgumentException if no double setting is registered
     */
    public static double getDoubleValue(String id) {
        Setting<Double> setting = getSetting(id, Double.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a double setting.");

        Double value = setting.getValue();
        return value == null ? 0.0 : value; // Return 0.0 if the value is null
    }

    /**
     * Sets a value directly on a setting instance.
     *
     * @param <T> value type of the setting
     * @param setting setting to update
     * @param value new value
     * @throws IllegalArgumentException if {@code setting} is {@code null}
     */
    public static <T> void setValue(Setting<T> setting, T value) {
        if (setting == null)
            throw new IllegalArgumentException("Setting cannot be null");

        setting.setValue(value);
    }

    /**
     * Reads a float setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current value, treating a {@code null} value as zero
     * @throws IllegalArgumentException if no float setting is registered
     */
    public static float getFloatValue(String id) {
        Setting<Float> setting = getSetting(id, Float.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a float setting.");

        Float value = setting.getValue();
        return value == null ? 0.0f : value; // Return 0.0f if the value is null
    }

    /**
     * Sets a float setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new float value
     */
    public static void setFloatValue(String id, float value) {
        setValue(id, Float.class, value);
    }

    /**
     * Reads a long setting by identifier.
     *
     * @param id identifier of the setting
     * @return the current value, treating a {@code null} value as zero
     * @throws IllegalArgumentException if no long setting is registered
     */
    public static long getLongValue(String id) {
        Setting<Long> setting = getSetting(id, Long.class);
        if (setting == null)
            throw new IllegalArgumentException("Setting " + id + " does not exist or is not a long setting.");

        Long value = setting.getValue();
        return value == null ? 0L : value; // Return 0L if the value is null
    }

    /**
     * Sets a long setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new long value
     */
    public static void setLongValue(String id, long value) {
        setValue(id, Long.class, value);
    }

    /**
     * Finds a setting by identifier and verifies its value type.
     *
     * @param <T> expected value type
     * @param id identifier of the setting
     * @param type expected value class
     * @return the matching typed setting, or {@code null} when no setting has
     *         the supplied identifier
     * @throws ClassCastException if the registered setting has an incompatible type
     */
    @SuppressWarnings("unchecked")
    public static <T> Setting<T> getSetting(String id, Class<T> type) {
        Setting<?> setting = SETTINGS_REGISTRY.get(id);
        if (setting == null)
            return null;

        Class<?> settingType = setting.getType();
        if (!type.isAssignableFrom(settingType))
            throw new ClassCastException(
                "Setting with ID '" + id + "' is of type " + settingType.getName() + ", cannot cast to "
                    + type.getName());

        return (Setting<T>) setting;
    }

    /**
     * Returns the shared holder used for settings persistence and deferred
     * hydration.
     *
     * @return the shared settings holder
     */
    public static SettingsHolder getSettingsHolder() {
        return SETTINGS_HOLDER;
    }

    /**
     * Sets a double setting by identifier.
     *
     * @param id identifier of the setting
     * @param value new double value
     */
    public void setDoubleValue(String id, double value) {
        setValue(id, Double.class, value);
    }
}
