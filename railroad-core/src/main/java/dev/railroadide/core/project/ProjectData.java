package dev.railroadide.core.project;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public final class ProjectData {
    private final Map<String, Object> data = new HashMap<>(); // TODO: Consider whether this should be concurrent

    public Object get(String key) {
        return data.get(key);
    }

    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value))
            return type.cast(value);

        return null;
    }

    public Object getOrDefault(String key, Object defaultValue) {
        return data.getOrDefault(key, defaultValue);
    }

    public <T> T getOrDefault(String key, T defaultValue, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value))
            return type.cast(value);

        return defaultValue;
    }

    public int getAsInt(String key, int defaultValue) {
        return getOrDefault(key, defaultValue, int.class);
    }

    public int getAsInt(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Integer theInt)
            return theInt;

        throw new ClassCastException("Value for key: " + key + " is not of type int");
    }

    public boolean getAsBoolean(String key, boolean defaultValue) {
        return getOrDefault(key, defaultValue, boolean.class);
    }

    public boolean getAsBoolean(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Boolean theBool)
            return theBool;

        throw new ClassCastException("Value for key: " + key + " is not of type boolean");
    }

    public String getAsString(String key, String defaultValue) {
        return getOrDefault(key, defaultValue, String.class);
    }

    public String getAsString(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof String theString)
            return theString;

        throw new ClassCastException("Value for key: " + key + " is not of type String");
    }

    public float getAsFloat(String key, float defaultValue) {
        return getOrDefault(key, defaultValue, float.class);
    }

    public float getAsFloat(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Float theFloat)
            return theFloat;

        throw new ClassCastException("Value for key: " + key + " is not of type float");
    }

    public double getAsDouble(String key, double defaultValue) {
        return getOrDefault(key, defaultValue, double.class);
    }

    public double getAsDouble(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Double theDouble)
            return theDouble;

        throw new ClassCastException("Value for key: " + key + " is not of type double");
    }

    public long getAsLong(String key, long defaultValue) {
        return getOrDefault(key, defaultValue, long.class);
    }

    public long getAsLong(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Long theLong)
            return theLong;

        throw new ClassCastException("Value for key: " + key + " is not of type long");
    }

    public short getAsShort(String key, short defaultValue) {
        return getOrDefault(key, defaultValue, short.class);
    }

    public short getAsShort(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Short theShort)
            return theShort;

        throw new ClassCastException("Value for key: " + key + " is not of type short");
    }

    public byte getAsByte(String key, byte defaultValue) {
        return getOrDefault(key, defaultValue, byte.class);
    }

    public byte getAsByte(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Byte theByte)
            return theByte;

        throw new ClassCastException("Value for key: " + key + " is not of type byte");
    }

    public char getAsChar(String key, char defaultValue) {
        return getOrDefault(key, defaultValue, char.class);
    }

    public char getAsChar(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Character theChar)
            return theChar;

        throw new ClassCastException("Value for key: " + key + " is not of type char");
    }

    public byte[] getAsByteArray(String key, byte[] defaultValue) {
        return getOrDefault(key, defaultValue, byte[].class);
    }

    public byte[] getAsByteArray(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof byte[] theByteArray)
            return theByteArray;

        throw new ClassCastException("Value for key: " + key + " is not of type byte[]");
    }

    public <E extends Enum<E>> E getAsEnum(String key, Class<E> enumType, E defaultValue) {
        Object value = data.get(key);
        if (enumType.isInstance(value))
            return enumType.cast(value);

        return defaultValue;
    }

    public <E extends Enum<E>> E getAsEnum(String key, Class<E> enumType) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (enumType.isInstance(object))
            return enumType.cast(object);

        throw new ClassCastException("Value for key: " + key + " is not of type " + enumType.getName());
    }

    public Path getAsPath(String key, Path defaultValue) {
        return getOrDefault(key, defaultValue, Path.class);
    }

    public Path getAsPath(String key) throws NoSuchElementException, ClassCastException {
        if (!contains(key))
            throw new NoSuchElementException("No value present for key: " + key);

        Object object = get(key);
        if (object instanceof Path thePath)
            return thePath;

        throw new ClassCastException("Value for key: " + key + " is not of type Path");
    }

    public Object getOrSetDefault(String key, Object defaultValue) {
        return data.computeIfAbsent(key, k -> defaultValue);
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public void clear() {
        data.clear();
    }

    public Map<String, Object> getAll() {
        return Map.copyOf(data);
    }

    public Set<String> keys() {
        return Set.copyOf(data.keySet());
    }

    public List<Object> values() {
        return List.copyOf(data.values());
    }

    public Collection<Map.Entry<String, Object>> entries() {
        return Set.copyOf(data.entrySet());
    }

    public int size() {
        return data.size();
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

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
        if (o == null || getClass() != o.getClass()) return false;
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

    public static class DefaultKeys {
        public static final String NAME = "project.name";
        public static final String PATH = "project.path";
        public static final String INIT_GIT = "project.initGit";
        public static final String LICENSE = "project.license";
        public static final String LICENSE_CUSTOM = "project.licenseCustom";
        public static final String AUTHOR = "project.author";
        public static final String DESCRIPTION = "project.description";
        public static final String CREDITS = "project.credits";
        public static final String ISSUES_URL = "project.issuesUrl";
        public static final String HOMEPAGE_URL = "project.homepageUrl";
        public static final String SOURCES_URL = "project.sourcesUrl";
        public static final String TYPE = "project.type";
    }
}
