package io.github.railroad.minecraft;

// TODO: This is actually wrong. [ means it should be included, ( means it should be excluded. But it works for current use cases.
public record VersionRange(String string, double min, double max) {
    public boolean contains(double version) {
        return version >= min && version < max;
    }

    public static VersionRange parse(String string) {
        double minVersion = string.startsWith("(") ? 0 : Double.parseDouble(string.substring(1, string.indexOf(',')));
        double maxVersion = string.endsWith(")") ? Double.MAX_VALUE : Double.parseDouble(string.substring(string.indexOf(',') + 1, string.length() - 1));
        return new VersionRange(string, minVersion, maxVersion);
    }
}
