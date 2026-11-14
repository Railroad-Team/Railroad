package dev.railroadide.railroad.ide.sst.semantic.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable extensible semantic properties. Modifiers and visibility use provider-owned strings;
 * extensions carry namespaced scalar values and cannot retain mutable provider objects.
 *
 * @param modifiers provider-owned modifier names to copy
 * @param visibility nonblank provider-owned visibility name
 * @param deprecated whether the declaration is deprecated
 * @param annotations annotation types to copy
 * @param extensions namespaced scalar provider properties to copy
 */
public record SymbolMetadata(
    Set<String> modifiers,
    String visibility,
    boolean deprecated,
    List<TypeReference> annotations,
    Map<String, String> extensions
) {
    /**
     * Metadata with no modifiers, annotations or extensions and unspecified visibility.
     */
    public static final SymbolMetadata EMPTY = new SymbolMetadata(Set.of(), "unspecified", false, List.of(), Map.of());

    /**
     * Copies metadata collections and validates visibility and property names.
     *
     * @param modifiers provider-owned modifier names to copy
     * @param visibility nonblank provider-owned visibility name
     * @param deprecated whether the declaration is deprecated
     * @param annotations annotation types to copy
     * @param extensions namespaced scalar provider properties to copy
     */
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
