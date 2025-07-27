package dev.railroadide.railroad.project.minecraft;

public record VersionRange(
        String string,
        double min, double max,
        boolean includeMin, boolean includeMax) {
    public static VersionRange parse(String string) {
        boolean includeMin = string.startsWith("[");
        boolean includeMax = string.endsWith("]");

        int commaIndex = string.indexOf(',');
        double minVersion = Double.parseDouble(string.substring(1, commaIndex));
        double maxVersion = Double.parseDouble(string.substring(commaIndex + 1, string.length() - 1));

        return new VersionRange(string, minVersion, maxVersion, includeMin, includeMax);
    }

    public boolean contains(double version) {
        boolean lowerBound = includeMin ? version >= min : version > min;
        boolean upperBound = includeMax ? version <= max : version < max;
        return lowerBound && upperBound;
    }
}