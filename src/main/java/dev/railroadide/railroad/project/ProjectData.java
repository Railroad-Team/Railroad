package dev.railroadide.railroad.project;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Thread-safe storage for arbitrary project properties keyed by name.
 *
 * <p>
 * The typed accessors return a fallback when a value is absent or has a
 * different type, while their overloads without a fallback throw an
 * exception in those cases.
 * </p>
 */
public final class ProjectData {
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    /**
     * Returns the value associated with a key, or {@code null} when the key is absent.
     *
     * @param key the property key
     * @return the stored value, or {@code null} if no value is associated with the key
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Returns the value associated with a key when it has the requested type.
     *
     * @param key the property key
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the typed value, or {@code null} when the key is absent or the value has another type
     */
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value))
            return type.cast(value);

        return null;
    }

    /**
     * Returns the value associated with a key, or a fallback when the key is absent.
     *
     * @param key the property key
     * @param defaultValue the value to return when the key is absent
     * @return the stored value, or {@code defaultValue} when the key is absent
     */
    public Object getOrDefault(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    /**
     * Returns the value associated with a key when it has the requested type, or a fallback otherwise.
     *
     * @param key the property key
     * @param defaultValue the value to return when the key is absent or has another type
     * @param type the expected value type
     * @param <T> the expected value type
     * @return the typed value, or {@code defaultValue} when no matching value is stored
     */
    public <T> T getOrDefault(String key, T defaultValue, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value))
            return type.cast(value);

        return defaultValue;
    }

    /**
     * Returns an integer property, or a fallback when it is absent or not an integer.
     *
     * @param key the property key
     * @param defaultValue the value to return when no integer is stored
     * @return the stored integer, or {@code defaultValue}
     */
    public int getAsInt(String key, int defaultValue) {
        return getOrDefault(key, defaultValue, int.class);
    }

    /**
     * Returns an integer property.
     *
     * @param key the property key
     * @return the stored integer
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not an integer
     */
    public int getAsInt(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Integer theInt)
            return theInt;

        throw new ClassCastException("Value for key: " + key + " is not of type int");
    }

    /**
     * Returns a boolean property, or a fallback when it is absent or not a boolean.
     *
     * @param key the property key
     * @param defaultValue the value to return when no boolean is stored
     * @return the stored boolean, or {@code defaultValue}
     */
    public boolean getAsBoolean(String key, boolean defaultValue) {
        return getOrDefault(key, defaultValue, boolean.class);
    }

    /**
     * Returns a boolean property.
     *
     * @param key the property key
     * @return the stored boolean
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a boolean
     */
    public boolean getAsBoolean(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Boolean theBool)
            return theBool;

        throw new ClassCastException("Value for key: " + key + " is not of type boolean");
    }

    /**
     * Returns a string property, or a fallback when it is absent or not a string.
     *
     * @param key the property key
     * @param defaultValue the value to return when no string is stored
     * @return the stored string, or {@code defaultValue}
     */
    public String getAsString(String key, String defaultValue) {
        return getOrDefault(key, defaultValue, String.class);
    }

    /**
     * Returns a string property.
     *
     * @param key the property key
     * @return the stored string
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a string
     */
    public String getAsString(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof String theString)
            return theString;

        throw new ClassCastException("Value for key: " + key + " is not of type String");
    }

    /**
     * Returns a floating-point property, or a fallback when it is absent or not a float.
     *
     * @param key the property key
     * @param defaultValue the value to return when no float is stored
     * @return the stored float, or {@code defaultValue}
     */
    public float getAsFloat(String key, float defaultValue) {
        return getOrDefault(key, defaultValue, float.class);
    }

    /**
     * Returns a floating-point property.
     *
     * @param key the property key
     * @return the stored float
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a float
     */
    public float getAsFloat(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Float theFloat)
            return theFloat;

        throw new ClassCastException("Value for key: " + key + " is not of type float");
    }

    /**
     * Returns a double-precision property, or a fallback when it is absent or not a double.
     *
     * @param key the property key
     * @param defaultValue the value to return when no double is stored
     * @return the stored double, or {@code defaultValue}
     */
    public double getAsDouble(String key, double defaultValue) {
        return getOrDefault(key, defaultValue, double.class);
    }

    /**
     * Returns a double-precision property.
     *
     * @param key the property key
     * @return the stored double
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a double
     */
    public double getAsDouble(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Double theDouble)
            return theDouble;

        throw new ClassCastException("Value for key: " + key + " is not of type double");
    }

    /**
     * Returns a long property, or a fallback when it is absent or not a long.
     *
     * @param key the property key
     * @param defaultValue the value to return when no long is stored
     * @return the stored long, or {@code defaultValue}
     */
    public long getAsLong(String key, long defaultValue) {
        return getOrDefault(key, defaultValue, long.class);
    }

    /**
     * Returns a long property.
     *
     * @param key the property key
     * @return the stored long
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a long
     */
    public long getAsLong(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Long theLong)
            return theLong;

        throw new ClassCastException("Value for key: " + key + " is not of type long");
    }

    /**
     * Returns a short property, or a fallback when it is absent or not a short.
     *
     * @param key the property key
     * @param defaultValue the value to return when no short is stored
     * @return the stored short, or {@code defaultValue}
     */
    public short getAsShort(String key, short defaultValue) {
        return getOrDefault(key, defaultValue, short.class);
    }

    /**
     * Returns a short property.
     *
     * @param key the property key
     * @return the stored short
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a short
     */
    public short getAsShort(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Short theShort)
            return theShort;

        throw new ClassCastException("Value for key: " + key + " is not of type short");
    }

    /**
     * Returns a byte property, or a fallback when it is absent or not a byte.
     *
     * @param key the property key
     * @param defaultValue the value to return when no byte is stored
     * @return the stored byte, or {@code defaultValue}
     */
    public byte getAsByte(String key, byte defaultValue) {
        return getOrDefault(key, defaultValue, byte.class);
    }

    /**
     * Returns a byte property.
     *
     * @param key the property key
     * @return the stored byte
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a byte
     */
    public byte getAsByte(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Byte theByte)
            return theByte;

        throw new ClassCastException("Value for key: " + key + " is not of type byte");
    }

    /**
     * Returns a character property, or a fallback when it is absent or not a character.
     *
     * @param key the property key
     * @param defaultValue the value to return when no character is stored
     * @return the stored character, or {@code defaultValue}
     */
    public char getAsChar(String key, char defaultValue) {
        return getOrDefault(key, defaultValue, char.class);
    }

    /**
     * Returns a character property.
     *
     * @param key the property key
     * @return the stored character
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a character
     */
    public char getAsChar(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Character theChar)
            return theChar;

        throw new ClassCastException("Value for key: " + key + " is not of type char");
    }

    /**
     * Returns a byte-array property, or a fallback when it is absent or not a byte array.
     *
     * @param key the property key
     * @param defaultValue the value to return when no byte array is stored
     * @return the stored byte array, or {@code defaultValue}
     */
    public byte[] getAsByteArray(String key, byte[] defaultValue) {
        return getOrDefault(key, defaultValue, byte[].class);
    }

    /**
     * Returns a byte-array property.
     *
     * @param key the property key
     * @return the stored byte array
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a byte array
     */
    public byte[] getAsByteArray(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof byte[] theByteArray)
            return theByteArray;

        throw new ClassCastException("Value for key: " + key + " is not of type byte[]");
    }

    /**
     * Returns an enum property, or a fallback when it is absent or is not an instance of the enum type.
     *
     * @param key the property key
     * @param enumType the enum type to accept
     * @param defaultValue the value to return when no matching enum is stored
     * @param <E> the enum type
     * @return the stored enum value, or {@code defaultValue}
     */
    public <E extends Enum<E>> E getAsEnum(String key, Class<E> enumType, E defaultValue) {
        Object value = data.get(key);
        if (enumType.isInstance(value))
            return enumType.cast(value);

        return defaultValue;
    }

    /**
     * Returns an enum property.
     *
     * @param key the property key
     * @param enumType the enum type to accept
     * @param <E> the enum type
     * @return the stored enum value
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not an instance of {@code enumType}
     */
    public <E extends Enum<E>> E getAsEnum(String key, Class<E> enumType)
        throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (enumType.isInstance(object))
            return enumType.cast(object);

        throw new ClassCastException("Value for key: " + key + " is not of type " + enumType.getName());
    }

    /**
     * Returns a path property, or a fallback when it is absent or not a path.
     *
     * @param key the property key
     * @param defaultValue the value to return when no path is stored
     * @return the stored path, or {@code defaultValue}
     */
    public Path getAsPath(String key, Path defaultValue) {
        return getOrDefault(key, defaultValue, Path.class);
    }

    /**
     * Returns a path property.
     *
     * @param key the property key
     * @return the stored path
     * @throws NoSuchElementException if the key is absent
     * @throws ClassCastException if the value is not a path
     */
    public Path getAsPath(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Path thePath)
            return thePath;

        throw new ClassCastException("Value for key: " + key + " is not of type Path");
    }

    /**
     * Returns the value for a key, storing and returning the fallback if the key is absent.
     *
     * @param key the property key
     * @param defaultValue the value to store and return when the key is absent
     * @return the existing value, or {@code defaultValue} when it was stored for the first time
     */
    public Object getOrSetDefault(String key, Object defaultValue) {
        return data.computeIfAbsent(key, k -> defaultValue);
    }

    /**
     * Stores or replaces a property value.
     *
     * @param key the property key
     * @param value the value to store
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Removes a property if it is present.
     *
     * @param key the property key
     */
    public void remove(String key) {
        data.remove(key);
    }

    /**
     * Tests whether a property is present.
     *
     * @param key the property key
     * @return {@code true} if the key is present; otherwise {@code false}
     */
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /**
     * Removes all stored properties.
     */
    public void clear() {
        data.clear();
    }

    /**
     * Returns an unmodifiable snapshot of all stored properties.
     *
     * @return a snapshot of the key-value data
     */
    public Map<String, Object> getAll() {
        return Map.copyOf(data);
    }

    /**
     * Returns an unmodifiable snapshot of all property keys.
     *
     * @return a snapshot of the property keys
     */
    public Set<String> keys() {
        return Set.copyOf(data.keySet());
    }

    /**
     * Returns an unmodifiable snapshot of all property values.
     *
     * @return a snapshot of the property values
     */
    public List<Object> values() {
        return List.copyOf(data.values());
    }

    /**
     * Returns an unmodifiable snapshot of all property entries.
     *
     * @return a snapshot of the property entries
     */
    public Collection<Map.Entry<String, Object>> entries() {
        return Set.copyOf(data.entrySet());
    }

    /**
     * Returns the number of stored properties.
     *
     * @return the number of key-value pairs
     */
    public int size() {
        return data.size();
    }

    /**
     * Tests whether no properties are stored.
     *
     * @return {@code true} if this store contains no properties; otherwise {@code false}
     */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /**
     * Removes every property whose entry matches a predicate.
     *
     * @param filter the predicate used to select entries for removal
     * @return {@code true} if at least one property was removed
     */
    public boolean removeIf(Predicate<Map.Entry<String, Object>> filter) {
        Objects.requireNonNull(filter);
        boolean removed = false;
        Iterator<Map.Entry<String, Object>> each = data.entrySet().iterator();
        while (each.hasNext()) {
            if (filter.test(each.next())) {
                each.remove();
                removed = true;
            }
        }

        return removed;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ProjectData that = (ProjectData) o;
        return Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(data);
    }

    @Override
    public String toString() {
        return "ProjectData{" +
            "data=" + data +
            '}';
    }

    /**
     * Standard keys used for common project properties.
     */
    public static class DefaultKeys {
        /** Key for the project name. */
        public static final String NAME = "project.name";
        /** Key for the project path. */
        public static final String PATH = "project.path";
        /** Key indicating whether Git should be initialized for the project. */
        public static final String INIT_GIT = "project.initGit";
        /** Key for the selected project license. */
        public static final String LICENSE = "project.license";
        /** Key for custom project license text. */
        public static final String LICENSE_CUSTOM = "project.licenseCustom";
        /** Key for the project author. */
        public static final String AUTHOR = "project.author";
        /** Key for the project description. */
        public static final String DESCRIPTION = "project.description";
        /** Key for project credits. */
        public static final String CREDITS = "project.credits";
        /** Key for the project's issue tracker URL. */
        public static final String ISSUES_URL = "project.issuesUrl";
        /** Key for the project's homepage URL. */
        public static final String HOMEPAGE_URL = "project.homepageUrl";
        /** Key for the project's source repository URL. */
        public static final String SOURCES_URL = "project.sourcesUrl";
        /** Key for the project type. */
        public static final String TYPE = "project.type";
    }
}
