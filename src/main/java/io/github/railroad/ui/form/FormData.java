package io.github.railroad.ui.form;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a form data object that can be used to store form data.
 */
@Getter
public class FormData {
    private final Map<String, Object> data = new HashMap<>();

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value
     */
    public void add(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a string)
     */
    public void addProperty(String key, String value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as an integer)
     */
    public void addProperty(String key, int value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a boolean)
     */
    public void addProperty(String key, boolean value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a double)
     */
    public void addProperty(String key, double value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a float)
     */
    public void addProperty(String key, float value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a long)
     */
    public void addProperty(String key, long value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a short)
     */
    public void addProperty(String key, short value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a byte)
     */
    public void addProperty(String key, byte value) {
        data.put(key, value);
    }

    /**
     * Adds a key-value pair to the form data.
     *
     * @param key   the key
     * @param value the value (as a char)
     */
    public void addProperty(String key, char value) {
        data.put(key, value);
    }

    /**
     * Gets the value of the specified key.
     *
     * @param key the key
     * @return the value
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Gets the value of the specified key as a string.
     *
     * @param key the key
     * @return the value
     */
    public String getString(String key) {
        return (String) data.get(key);
    }

    /**
     * Gets the value of the specified key as an integer.
     *
     * @param key the key
     * @return the value
     */
    public int getInt(String key) {
        return (int) data.get(key);
    }

    /**
     * Gets the value of the specified key as a boolean.
     *
     * @param key the key
     * @return the value
     */
    public boolean getBoolean(String key) {
        return (boolean) data.get(key);
    }

    /**
     * Gets the value of the specified key as a double.
     *
     * @param key the key
     * @return the value
     */
    public double getDouble(String key) {
        return (double) data.get(key);
    }

    /**
     * Gets the value of the specified key as a float.
     *
     * @param key the key
     * @return the value
     */
    public float getFloat(String key) {
        return (float) data.get(key);
    }

    /**
     * Gets the value of the specified key as a long.
     *
     * @param key the key
     * @return the value
     */
    public long getLong(String key) {
        return (long) data.get(key);
    }

    /**
     * Gets the value of the specified key as a short.
     *
     * @param key the key
     * @return the value
     */
    public short getShort(String key) {
        return (short) data.get(key);
    }

    /**
     * Gets the value of the specified key as a byte.
     *
     * @param key the key
     * @return the value
     */
    public byte getByte(String key) {
        return (byte) data.get(key);
    }

    /**
     * Gets the value of the specified key as a char.
     *
     * @param key the key
     * @return the value
     */
    public char getChar(String key) {
        return (char) data.get(key);
    }

    /**
     * Checks if the form data contains the specified key.
     *
     * @param key the key
     * @return true if the form data contains the key, false otherwise
     */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /**
     * Checks if the form data contains the specified value.
     *
     * @param value the value
     * @return true if the form data contains the value, false otherwise
     */
    public boolean containsValue(Object value) {
        return data.containsValue(value);
    }

    /**
     * Checks if the form data is empty.
     *
     * @return true if the form data is empty, false otherwise
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Gets the value of the specified key and casts it to the specified enum type.
     *
     * @param key      the key
     * @param enumType the enum type
     * @param <T>      the enum type
     * @return the value
     * @throws ClassCastException if the value cannot be cast to the specified enum type
     */
    public <T extends Enum<?>> T getEnum(String key, Class<T> enumType) {
        return enumType.cast(data.get(key));
    }

    /**
     * Gets the value of the specified key and casts it to the specified class.
     *
     * @param key   the key
     * @param clazz the class
     * @param <T>   the type
     * @return the value
     * @throws ClassCastException if the value cannot be cast to the specified class
     */
    public <T> T get(String key, Class<T> clazz) {
        return clazz.cast(data.get(key));
    }
}
