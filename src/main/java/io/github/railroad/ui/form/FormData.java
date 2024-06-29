package io.github.railroad.ui.form;

import io.github.railroad.minecraft.MinecraftVersion;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class FormData {
    private final Map<String, Object> data = new HashMap<>();

    public void add(String key, Object value) {
        data.put(key, value);
    }

    public void addProperty(String key, String value) {
        data.put(key, value);
    }

    public void addProperty(String key, int value) {
        data.put(key, value);
    }

    public void addProperty(String key, boolean value) {
        data.put(key, value);
    }

    public void addProperty(String key, double value) {
        data.put(key, value);
    }

    public void addProperty(String key, float value) {
        data.put(key, value);
    }

    public void addProperty(String key, long value) {
        data.put(key, value);
    }

    public void addProperty(String key, short value) {
        data.put(key, value);
    }

    public void addProperty(String key, byte value) {
        data.put(key, value);
    }

    public void addProperty(String key, char value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        return (String) data.get(key);
    }

    public int getInt(String key) {
        return (int) data.get(key);
    }

    public boolean getBoolean(String key) {
        return (boolean) data.get(key);
    }

    public double getDouble(String key) {
        return (double) data.get(key);
    }

    public float getFloat(String key) {
        return (float) data.get(key);
    }

    public long getLong(String key) {
        return (long) data.get(key);
    }

    public short getShort(String key) {
        return (short) data.get(key);
    }

    public byte getByte(String key) {
        return (byte) data.get(key);
    }

    public char getChar(String key) {
        return (char) data.get(key);
    }

    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return data.containsValue(value);
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public <T extends Enum<?>> T getEnum(String key, Class<T> enumType) {
        return enumType.cast(data.get(key));
    }

    public <T> T get(String key, Class<T> clazz) {
        return clazz.cast(data.get(key));
    }
}
