package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import javafx.scene.Node;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A codec for a setting.
 *
 * @param <T>         The type of the setting value.
 * @param <N>         The type of the node to display.
 * @param <J>         The type of the json element for it to be converted to and saved as.
 * @param nodeToValue Gets the value from a node.
 * @param valueToNode Sets the value of the node.
 * @param jsonDecoder Converts the json object to a value.
 * @param jsonEncoder Converts the value to a json object.
 * @param createNode  Creates a node for the setting.
 */
public record SettingCodec<T, N extends Node, J extends JsonElement>(String id, Function<N, T> nodeToValue,
                                                                     BiConsumer<T, N> valueToNode,
                                                                     Function<J, T> jsonDecoder,
                                                                     Function<T, J> jsonEncoder,
                                                                     Function<T, N> createNode) {}