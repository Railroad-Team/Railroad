package dev.railroadide.core.project.creation.modjson;

import com.google.gson.annotations.JsonAdapter;
import dev.railroadide.core.project.creation.modjson.adapter.IconTypeAdapter;
import dev.railroadide.core.project.creation.modjson.adapter.MixinListTypeAdapter;
import dev.railroadide.core.project.creation.modjson.adapter.PersonListTypeAdapter;
import dev.railroadide.core.project.creation.modjson.adapter.StringOrStringArrayTypeAdapter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FabricModJson {
    // Mandatory fields
    private String id;
    private String version;

    // Optional fields (mod loading)
    @JsonAdapter(StringOrStringArrayTypeAdapter.class)
    private List<String> environment;
    private EntrypointContainer entrypoints;
    private List<NestedJarEntry> jars;
    private Map<String, String> languageAdapters;
    @JsonAdapter(MixinListTypeAdapter.class)
    private List<MixinEnvironment> mixins;
    private String accessWidener;

    // Optional fields (dependency resolution)
    private Map<String, VersionRange> depends;
    private Map<String, VersionRange> recommends;
    private Map<String, VersionRange> suggests;
    private Map<String, VersionRange> conflicts;
    private Map<String, VersionRange> breaks;

    // Optional fields (metadata)
    private String name;
    private String description;
    @JsonAdapter(PersonListTypeAdapter.class)
    private List<Person> authors;
    @JsonAdapter(PersonListTypeAdapter.class)
    private List<Person> contributors;
    private ContactInformation contact;
    @JsonAdapter(StringOrStringArrayTypeAdapter.class)
    private List<String> license;
    @JsonAdapter(IconTypeAdapter.class)
    private IconInfo icon;

    // Custom fields
    private Map<String, Object> custom;
}
