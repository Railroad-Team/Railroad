package dev.railroadide.railroad.project.creation.modjson;

import com.google.gson.annotations.JsonAdapter;
import dev.railroadide.railroad.project.creation.modjson.adapter.IconTypeAdapter;
import dev.railroadide.railroad.project.creation.modjson.adapter.MixinListTypeAdapter;
import dev.railroadide.railroad.project.creation.modjson.adapter.PersonListTypeAdapter;
import dev.railroadide.railroad.project.creation.modjson.adapter.StringOrStringArrayTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Mutable Fabric mod metadata used when creating or reading {@code fabric.mod.json} files.
 * Optional properties may be left {@code null} when they are not supplied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FabricModJson {
    // Mandatory fields
    /** The mod identifier. */
    private String id;
    /** The mod version text. */
    private String version;

    // Optional fields (mod loading)
    /** The environments in which the mod can load. */
    @JsonAdapter(StringOrStringArrayTypeAdapter.class)
    private List<String> environment;
    /** Initialization entrypoints grouped by their entrypoint key. */
    private EntrypointContainer entrypoints;
    /** JAR resources nested inside the mod. */
    private List<NestedJarEntry> jars;
    /** Custom language adapter implementation classes keyed by adapter identifier. */
    private Map<String, String> languageAdapters;
    /** Mixin configuration resources and their optional environment restrictions. */
    @JsonAdapter(MixinListTypeAdapter.class)
    private List<MixinEnvironment> mixins;
    /** The access widener resource path. */
    private String accessWidener;

    // Optional fields (dependency resolution)
    /** Required dependencies keyed by mod identifier. */
    private Map<String, VersionRange> depends;
    /** Recommended dependencies keyed by mod identifier. */
    private Map<String, VersionRange> recommends;
    /** Suggested dependencies keyed by mod identifier. */
    private Map<String, VersionRange> suggests;
    /** Conflicting versions keyed by mod identifier. */
    private Map<String, VersionRange> conflicts;
    /** Incompatible versions that prevent loading, keyed by mod identifier. */
    private Map<String, VersionRange> breaks;

    // Optional fields (metadata)
    /** The human-readable mod name. */
    private String name;
    /** A description of the mod. */
    private String description;
    /** The mod's authors. */
    @JsonAdapter(PersonListTypeAdapter.class)
    private List<Person> authors;
    /** Additional contributors to the mod. */
    @JsonAdapter(PersonListTypeAdapter.class)
    private List<Person> contributors;
    /** Project contact information and links. */
    private ContactInformation contact;
    /** License identifiers or descriptions for the mod. */
    @JsonAdapter(StringOrStringArrayTypeAdapter.class)
    private List<String> license;
    /** Icon paths for the mod. */
    @JsonAdapter(IconTypeAdapter.class)
    private IconInfo icon;

    // Custom fields
    /** Additional metadata supplied under the custom property. */
    private Map<String, Object> custom;
}
