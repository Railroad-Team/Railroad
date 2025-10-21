package dev.railroadide.railroad.ui.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LayoutItem {
    private final Map<String, Object> properties = new HashMap<>();
    private String name;

    public LayoutItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getProperties() {
        return Map.copyOf(properties);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    public boolean hasProperty(String key, Object value) {
        return properties.containsKey(key) && properties.get(key).equals(value);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("LayoutItem{name='").append(name).append('\'');

        if (!properties.isEmpty()) {
            sb.append(", properties=").append(properties);
        }

        sb.append('}');
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LayoutItem that)) return false;
        return Objects.equals(name, that.name) && Objects.equals(properties, that.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, properties);
    }
}
