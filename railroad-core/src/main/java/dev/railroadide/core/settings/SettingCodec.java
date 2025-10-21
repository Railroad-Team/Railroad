package dev.railroadide.core.settings;

import com.google.gson.JsonElement;
import javafx.scene.Node;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A codec for a setting.
 *
 * @param <T> The type of the setting value.
 * @param <N> The type of the node to display.
 * @see Setting
 * @see DefaultSettingCodecs
 */
public record SettingCodec<T, N extends Node>(String id, Function<N, T> nodeToValue, BiConsumer<T, N> valueToNode,
                                              Function<JsonElement, T> jsonDecoder,
                                              Function<T, JsonElement> jsonEncoder, Function<T, N> createNode) {
    /**
     * Serializes the setting value to JSON.
     *
     * @param value The value to serialize.
     * @return The serialized JSON element.
     */
    public JsonElement serializeJson(T value) {
        return jsonEncoder.apply(value);
    }

    /**
     * Deserializes the setting value from JSON.
     *
     * @param json The JSON element to deserialize from.
     * @return The deserialized value.
     */
    public T deserializeJson(JsonElement json) {
        return jsonDecoder.apply(json);
    }

    /**
     * Creates a new builder for a SettingCodec.
     *
     * @param <T> The type of the setting value.
     * @param <N> The type of the node to display.
     * @return A new Builder instance.
     */
    public static <T, N extends Node> Builder<T, N> builder() {
        return new Builder<>();
    }

    /**
     * Creates a new builder for a SettingCodec with an ID.
     *
     * @param id  The unique identifier for the setting codec.
     * @param <T> The type of the setting value.
     * @param <N> The type of the node to display.
     * @return A new Builder instance with the specified ID.
     */
    public static <T, N extends Node> Builder<T, N> builder(String id) {
        return new Builder<T, N>().id(id);
    }

    /**
     * Builder class for creating instances of SettingCodec.
     *
     * @param <T> The type of the setting value.
     * @param <N> The type of the node to display.
     */
    public static class Builder<T, N extends Node> {
        private String id;
        private Function<N, T> nodeToValue;
        private BiConsumer<T, N> valueToNode;
        private Function<JsonElement, T> jsonDecoder;
        private Function<T, JsonElement> jsonEncoder;
        private Function<T, N> createNode;

        /**
         * Constructs a Builder with default values.
         */
        public Builder() {
            this.id = null;
            this.nodeToValue = node -> null; // Default to returning null
            this.valueToNode = (value, node) -> {
            }; // Default to doing nothing
            this.jsonDecoder = json -> null; // Default to returning null
            this.jsonEncoder = value -> null; // Default to returning null
            this.createNode = value -> null; // Default to returning null
        }

        /**
         * Sets the unique identifier for the setting codec.
         *
         * @param id The unique identifier.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the function to extract the value from a node.
         *
         * @param nodeToValue The function to extract value from a node.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> nodeToValue(Function<N, T> nodeToValue) {
            this.nodeToValue = nodeToValue;
            return this;
        }

        /**
         * Sets the function to set the value in a node.
         *
         * @param valueToNode The function to set value in a node.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> valueToNode(BiConsumer<T, N> valueToNode) {
            this.valueToNode = valueToNode;
            return this;
        }

        /**
         * Sets the function to decode a JSON element into a value.
         *
         * @param jsonDecoder The function to decode JSON to value.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> jsonDecoder(Function<JsonElement, T> jsonDecoder) {
            this.jsonDecoder = jsonDecoder;
            return this;
        }

        /**
         * Sets the function to encode a value into a JSON element.
         *
         * @param jsonEncoder The function to encode value to JSON.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> jsonEncoder(Function<T, JsonElement> jsonEncoder) {
            this.jsonEncoder = jsonEncoder;
            return this;
        }

        /**
         * Sets the function to create a node for the setting.
         *
         * @param createNode The function to create a node for the setting.
         * @return The Builder instance for chaining.
         */
        public Builder<T, N> createNode(Function<T, N> createNode) {
            this.createNode = createNode;
            return this;
        }

        /**
         * Builds and returns a new SettingCodec instance with the specified parameters.
         *
         * @return A new SettingCodec instance.
         */
        public SettingCodec<T, N> build() {
            if (id == null)
                throw new IllegalStateException("ID must be set before building the SettingCodec");

            return new SettingCodec<>(id, nodeToValue, valueToNode, jsonDecoder, jsonEncoder, createNode);
        }
    }
}
