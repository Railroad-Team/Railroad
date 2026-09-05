package dev.railroadide.railroad.form;

/**
 * Represents a UI component whose value can be assigned from a generic object.
 */
public interface HasSetValue {
    /**
     * Sets the component value represented by the supplied object.
     *
     * @param value the value to assign
     */
    void setValue(Object value);
}
