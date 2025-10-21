package dev.railroadide.core.project.creation.modjson;

import com.google.gson.annotations.JsonAdapter;
import dev.railroadide.core.project.creation.modjson.adapter.VersionRangeTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonAdapter(VersionRangeTypeAdapter.class)
public class VersionRange {
    private List<String> ranges;

    public static VersionRange gte(String version) {
        return new VersionRange(List.of(">=" + version));
    }

    public static VersionRange gteMinor(String version) {
        return new VersionRange(List.of("~" + version));
    }

    public static VersionRange gteMajor(String version) {
        return new VersionRange(List.of("^" + version));
    }

    public static VersionRange eq(String version) {
        return new VersionRange(List.of("=" + version));
    }

    public static VersionRange lte(String version) {
        return new VersionRange(List.of("<=" + version));
    }

    public static VersionRange exact(String version) {
        return new VersionRange(List.of(version));
    }

    public static VersionRange lt(String version) {
        return new VersionRange(List.of("<" + version));
    }

    public static VersionRange gt(String version) {
        return new VersionRange(List.of(">" + version));
    }

    public static VersionRange any() {
        return new VersionRange(List.of("*"));
    }

    public static VersionRange between(String min, String max, boolean inclusive) {
        return inclusive ?
            new VersionRange(List.of(">=" + min, "<=" + max)) :
            new VersionRange(List.of(">" + min, "<" + max));
    }
}
