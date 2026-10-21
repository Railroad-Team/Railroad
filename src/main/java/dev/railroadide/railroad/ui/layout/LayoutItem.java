package dev.railroadide.railroad.ui.layout;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A mutable named layout element with arbitrary properties consumed by the layout builder. */
public class LayoutItem {
    private final Map<String, Object> properties = new HashMap<>();
    private String name;

    /**
     * Creates an item with no properties.
     *
     * @param name element name used to select its pane type
     */
    public LayoutItem(String name) {
        this.name = name;
    }

    /**
     * Returns the element name.
     *
     * @return the current name, which may be null
     */
    public String getName() {
        return name;
    }

    /**
     * Changes the element name used by the layout builder.
     *
     * @param name new element name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns an unmodifiable snapshot of the properties; property values themselves are not copied.
     *
     * @return a snapshot of the current properties
     * @throws NullPointerException if a stored key or value is null
     */
    public Map<String, Object> getProperties() {
        return Map.copyOf(properties);
    }

    /**
     * Adds or replaces a property.
     *
     * @param key property name
     * @param value property value
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Looks up a property by name.
     *
     * @param key property name
     * @return the stored value, or null if absent or explicitly set to null
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Checks whether a property name is present, regardless of its value.
     *
     * @param key property name
     * @return true if the property is present
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    /**
     * Checks whether a property exists and its stored value equals the supplied value.
     *
     * @param key property name
     * @param value value to compare against
     * @return true if the property exists and the values are equal
     * @throws NullPointerException if the property exists with a null value
     */
    public boolean hasProperty(String key, Object value) {
        return properties.containsKey(key) && properties.get(key).equals(value);
    }

    /**
     * Returns a diagnostic representation of the name and any properties.
     *
     * @return the item description
     */
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

    /**
     * Compares items by their names and property maps.
     *
     * @param o object to compare
     * @return true if the other object is a layout item with equal name and properties
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LayoutItem that))
            return false;
        return Objects.equals(name, that.name) && Objects.equals(properties, that.properties);
    }

    /**
     * Computes a hash from the current name and properties.
     *
     * @return the hash code, which can change when this item is modified
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, properties);
    }
}
