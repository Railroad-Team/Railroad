package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable extensible semantic properties. Modifiers and visibility use provider-owned strings;
 * extensions carry namespaced scalar values and cannot retain mutable provider objects.
 */
public record SymbolMetadata(
    Set<String> modifiers,
    String visibility,
    boolean deprecated,
    List<TypeReference> annotations,
    Map<String, String> extensions
) {
    public static final SymbolMetadata EMPTY = new SymbolMetadata(Set.of(), "unspecified", false, List.of(), Map.of());

    public SymbolMetadata {
        modifiers = Set.copyOf(modifiers);
        Objects.requireNonNull(visibility, "visibility");
        if (visibility.isBlank())
            throw new IllegalArgumentException("Visibility cannot be blank");
        annotations = List.copyOf(annotations);
        extensions = Map.copyOf(extensions);
        if (modifiers.stream().anyMatch(String::isBlank) || extensions.keySet().stream().anyMatch(String::isBlank))
            throw new IllegalArgumentException("Metadata keys cannot be blank");
    }
}
