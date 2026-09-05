package dev.railroadide.railroad.ide.classparser.stub;

import dev.railroadide.railroad.ide.classparser.Type;

import java.util.List;

/**
 * Stores a generic type parameter and its declared bounds.
 *
 * @param name the declared name
 * @param bounds the type parameter bounds
 */
public record TypeParameter(String name, List<Type> bounds) {
}
