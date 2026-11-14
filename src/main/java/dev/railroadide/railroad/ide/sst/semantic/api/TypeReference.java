package dev.railroadide.railroad.ide.sst.semantic.api;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral structured type spelling with an optional resolved declaration identity.
 * Names and argument meaning are language-owned; Java uses {@code ? extends}/{@code ? super}
 * with one argument for bounded wildcards. Missing targets explicitly mean unresolved or intrinsic
 * types, never a guessed binding. Array rank is independent of generic arguments.
 *
 * @param name language-owned type name
 * @param arguments ordered generic arguments or bounds
 * @param arrayDimensions nonnegative array rank
 * @param target resolved symbol, or null
 */
public record TypeReference(
    String name,
    List<TypeReference> arguments,
    int arrayDimensions,
    @Nullable SymbolId target
) {
    public TypeReference {
        Objects.requireNonNull(name, "name");
        if (name.isBlank())
            throw new IllegalArgumentException("Type name cannot be blank");
        arguments = List.copyOf(arguments);
        if (arrayDimensions < 0)
            throw new IllegalArgumentException("Array rank cannot be negative");
    }

    public TypeReference(String name) {
        this(name, List.of(), 0, null);
    }

    /** Returns a deterministic spelling for presentation, not symbol identity. */
    public String displayName() {
        String suffix = arguments.isEmpty()
            ? ""
            : name.startsWith("?")
                ? " " + arguments.getFirst().displayName()
                : "<" + String.join(",", arguments.stream().map(TypeReference::displayName).toList()) + ">";
        return name + suffix + "[]".repeat(arrayDimensions);
    }
}
