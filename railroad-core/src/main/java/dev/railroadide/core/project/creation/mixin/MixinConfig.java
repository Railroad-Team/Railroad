package dev.railroadide.core.project.creation.mixin;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixinConfig {
    // Mandatory fields
    @SerializedName("package")
    private String packageName;
    private List<String> mixins;
    private List<String> client;
    private List<String> server;

    // Optional fields
    private String refmap;
    private Integer priority;
    private String plugin;
    private Boolean required;
    private String minVersion;
    private Boolean setSourceFile;
    private Boolean verbose;

    // Forge specific
    private String compatibilityLevel;
    private Map<String, Object> injectors;
}
