package dev.railroadide.railroad.ide.sst.document.api;

/**
 * Represents a location in a document that is related to another location, along with an associated message.
 * <p>
 * Example usage:
 *
 * <pre>
 * int value = 10;
 * int value = 20; // Error: variable already defined
 * </pre>
 *
 * In this example, the second declaration of <code>value</code> can be related to the first declaration,
 * and a message can be provided to indicate that the variable is already defined.
 *
 * @param location The related location in the document
 * @param message A message describing the relationship or context of the related location
 */
public record RelatedLocation(
    Location<?> location,
    String message) {
    public RelatedLocation {
        if (location == null)
            throw new IllegalArgumentException("Location cannot be null");

        if (message == null || message.isBlank())
            throw new IllegalArgumentException("Message cannot be null or blank");
    }
}
