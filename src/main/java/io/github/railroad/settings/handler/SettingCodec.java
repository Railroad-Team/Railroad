package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import javafx.scene.Node;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A codec for a setting.
 *
 * @param <T> The type of the setting value.
 * @param <N> The type of the node to display.
 * @param <J> The type of the json element for it to be converted to and saved as.
 */
@Getter
public class SettingCodec<T, N extends Node, J extends JsonElement> {
    private final String id;

    /**
     * Gets the value from a node.
     */
    private final Function<N, T> nodeToValue;
    /**
     * Sets the value of the node.
     */
    private final BiConsumer<T, N> valueToNode;

    /**
     * Converts the json object to a value.
     */
    private final Function<J, T> jsonDecoder;
    /**
     * Converts the value to a json object.
     */
    private final Function<T, J> jsonEncoder;

    /**
     * Creates a node for the setting.
     */
    private final Function<T, N> createNode;

    public SettingCodec(
            String id, Function<N, T> nodeToValue, BiConsumer<T, N> valueToNode,
            Function<J, T> jsonDecoder, Function<T, J> jsonEncoder, Function<T, N> createNode
    ) {
        this.id = id;
        this.nodeToValue = nodeToValue;
        this.valueToNode = valueToNode;
        this.jsonDecoder = jsonDecoder;
        this.jsonEncoder = jsonEncoder;
        this.createNode = createNode;
    }
}
