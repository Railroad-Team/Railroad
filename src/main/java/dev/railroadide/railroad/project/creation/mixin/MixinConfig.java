package dev.railroadide.railroad.project.creation.mixin;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Mutable configuration data for a generated Mixin JSON resource.
 * Optional boxed values may be {@code null} to leave the corresponding setting unspecified.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MixinConfig {
    // Mandatory fields
    /** The base Java package containing the mixin classes. */
    @SerializedName("package")
    private String packageName;
    /** Common mixin class names relative to the base package. */
    private List<String> mixins;
    /** Client mixin class names relative to the base package. */
    private List<String> client;
    /** Dedicated server mixin class names relative to the base package. */
    private List<String> server;

    // Optional fields
    /** The reference map resource name. */
    private String refmap;
    /** The priority assigned to this mixin configuration. */
    private Integer priority;
    /** The fully qualified configuration plugin class name. */
    private String plugin;
    /** Whether failure to apply this configuration is fatal. */
    private Boolean required;
    /** The minimum supported Mixin subsystem version. */
    private String minVersion;
    /** Whether mixins replace the target class's source file attribute. */
    private Boolean setSourceFile;
    /** Whether verbose logging is enabled for this configuration. */
    private Boolean verbose;

    // Forge specific
    /** The Java compatibility level requested by the configuration. */
    private String compatibilityLevel;
    /** Additional injector settings indexed by their configuration keys. */
    private Map<String, Object> injectors;
}
