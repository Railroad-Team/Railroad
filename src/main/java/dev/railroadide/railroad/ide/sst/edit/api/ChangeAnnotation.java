package dev.railroadide.railroad.ide.sst.edit.api;

import java.util.Objects;

/**
 * Explains why a group of changes is required, for presentation in a preview.
 *
 * @param label short nonblank label
 * @param description longer explanation, or an empty string
 * @param needsConfirmation whether a preview should ask the user to confirm the annotated changes; application
 *            itself does not enforce confirmation
 */
public record ChangeAnnotation(String label, String description, boolean needsConfirmation) {
    /**
     * Creates an annotation.
     *
     * @param label short nonblank label
     * @param description longer explanation; {@code null} is treated as empty
     * @param needsConfirmation whether a preview should request confirmation
     * @throws IllegalArgumentException if the label is blank
     */
    public ChangeAnnotation {
        Objects.requireNonNull(label, "label");
        if (label.isBlank())
            throw new IllegalArgumentException("Annotation label must not be blank");

        description = description == null ? "" : description;
    }
}
