package dev.railroadide.railroad.project.creation.modjson;

import com.google.gson.annotations.JsonAdapter;
import dev.railroadide.railroad.project.creation.modjson.adapter.VersionRangeTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Stores Fabric dependency version expressions for serialization as a string or string array.
 * Factory methods construct expressions without validating the supplied version text.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonAdapter(VersionRangeTypeAdapter.class)
public class VersionRange {
    /** The version expressions in their serialization order. */
    private List<String> ranges;

    /**
     * Creates a greater-than-or-equal version expression.
     *
     * @param version the lower-bound version text
     * @return a range containing {@code >=} followed by the version
     */
    public static VersionRange gte(String version) {
        return new VersionRange(List.of(">=" + version));
    }

    /**
     * Creates a tilde-prefixed version expression.
     *
     * @param version the base version text
     * @return a range containing {@code ~} followed by the version
     */
    public static VersionRange gteMinor(String version) {
        return new VersionRange(List.of("~" + version));
    }

    /**
     * Creates a caret-prefixed version expression.
     *
     * @param version the base version text
     * @return a range containing {@code ^} followed by the version
     */
    public static VersionRange gteMajor(String version) {
        return new VersionRange(List.of("^" + version));
    }

    /**
     * Creates an equality version expression.
     *
     * @param version the required version text
     * @return a range containing {@code =} followed by the version
     */
    public static VersionRange eq(String version) {
        return new VersionRange(List.of("=" + version));
    }

    /**
     * Creates a less-than-or-equal version expression.
     *
     * @param version the upper-bound version text
     * @return a range containing {@code <=} followed by the version
     */
    public static VersionRange lte(String version) {
        return new VersionRange(List.of("<=" + version));
    }

    /**
     * Stores a version expression exactly as supplied, without an operator prefix.
     *
     * @param version the version expression to store
     * @return a range containing the supplied expression
     */
    public static VersionRange exact(String version) {
        return new VersionRange(List.of(version));
    }

    /**
     * Creates a strict upper-bound version expression.
     *
     * @param version the excluded upper-bound version text
     * @return a range containing {@code <} followed by the version
     */
    public static VersionRange lt(String version) {
        return new VersionRange(List.of("<" + version));
    }

    /**
     * Creates a strict lower-bound version expression.
     *
     * @param version the excluded lower-bound version text
     * @return a range containing {@code >} followed by the version
     */
    public static VersionRange gt(String version) {
        return new VersionRange(List.of(">" + version));
    }

    /**
     * Creates a wildcard version expression.
     *
     * @return a range containing {@code *}
     */
    public static VersionRange any() {
        return new VersionRange(List.of("*"));
    }

    /**
     * Creates separate lower-bound and upper-bound expressions in that order.
     * The expressions remain separate entries when serialized as a JSON array.
     *
     * @param min the lower-bound version text
     * @param max the upper-bound version text
     * @param inclusive whether both comparison operators include their boundary
     * @return a range containing the lower-bound and upper-bound expressions
     */
    public static VersionRange between(String min, String max, boolean inclusive) {
        return inclusive
            ? new VersionRange(List.of(">=" + min, "<=" + max))
            : new VersionRange(List.of(">" + min, "<" + max));
    }
}
