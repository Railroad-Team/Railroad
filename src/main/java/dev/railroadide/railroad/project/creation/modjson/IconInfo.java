package dev.railroadide.railroad.project.creation.modjson;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Fabric icon paths indexed by width, with {@code default} representing a single unqualified path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IconInfo {
    /** Icon resource paths keyed by width text or {@code default}. */
    private Map<String, String> iconsByWidth;
}
