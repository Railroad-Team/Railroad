package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.settings.Setting;
import dev.railroadide.railroad.settings.SettingCodec;

/**
 * Service interface for managing and accessing application settings.
 */
public interface SettingsService {
    /**
     * Retrieves a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The setting associated with the given identifier.
     */
    Setting<?> getSetting(String id);

    /**
     * Retrieves the value of a given setting.
     *
     * @param <T>     The type of the setting's value.
     * @param setting The setting whose value is to be retrieved.
     * @return The value of the specified setting.
     */
    <T> T getValue(Setting<T> setting);

    /**
     * Retrieves the value of a setting by its unique identifier and type.
     *
     * @param <V>  The type of the setting's value.
     * @param id   The unique identifier of the setting.
     * @param type The class type of the setting's value.
     * @return The value of the setting.
     */
    <V> V getValue(String id, Class<V> type);

    /**
     * Sets the value of a setting by its unique identifier and type.
     *
     * @param <T>   The type of the setting's value.
     * @param id    The unique identifier of the setting.
     * @param clazz The class type of the setting's value.
     * @param value The new value to set for the setting.
     */
    <T> void setValue(String id, Class<T> clazz, T value);

    /**
     * Retrieves the boolean value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The boolean value of the setting.
     */
    boolean getBooleanValue(String id);

    /**
     * Sets the boolean value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new boolean value to set for the setting.
     */
    void setBooleanValue(String id, boolean value);

    /**
     * Retrieves the string value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The string value of the setting.
     */
    String getStringValue(String id);

    /**
     * Sets the string value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new string value to set for the setting.
     */
    void setStringValue(String id, String value);

    /**
     * Retrieves the integer value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The integer value of the setting.
     */
    int getIntValue(String id);

    /**
     * Sets the integer value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new integer value to set for the setting.
     */
    void setIntValue(String id, int value);

    /**
     * Retrieves the double value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The double value of the setting.
     */
    double getDoubleValue(String id);

    /**
     * Sets the double value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new double value to set for the setting.
     */
    void setDoubleValue(String id, double value);

    /**
     * Retrieves the float value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The float value of the setting.
     */
    float getFloatValue(String id);

    /**
     * Sets the float value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new float value to set for the setting.
     */
    void setFloatValue(String id, float value);

    /**
     * Retrieves the long value of a setting by its unique identifier.
     *
     * @param id The unique identifier of the setting.
     * @return The long value of the setting.
     */
    long getLongValue(String id);

    /**
     * Sets the long value of a setting by its unique identifier.
     *
     * @param id    The unique identifier of the setting.
     * @param value The new long value to set for the setting.
     */
    void setLongValue(String id, long value);

    /**
     * Retrieves a setting by its unique identifier and type.
     *
     * @param <T>  The type of the setting's value.
     * @param id   The unique identifier of the setting.
     * @param type The class type of the setting's value.
     * @return The setting associated with the given identifier and type.
     */
    <T> Setting<T> getSetting(String id, Class<T> type);

    /**
     * Retrieves the codec associated with a setting by its unique identifier.
     *
     * @param <T> The type of the setting's value.
     * @param id  The unique identifier of the setting.
     * @return The codec associated with the setting.
     */
    <T> SettingCodec<T, ?> getCodec(String id);
}
