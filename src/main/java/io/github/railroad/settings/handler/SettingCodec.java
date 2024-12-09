package io.github.railroad.settings.handler;

import com.google.gson.JsonElement;
import javafx.scene.Node;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.Function;

@Getter
public class SettingCodec<T, N extends Node, J extends JsonElement>{
    private final Function<N, T> nodeToValFunction;
    /**
     * Sets the current selected value of the node
     */
    private final BiConsumer<N, T> valToNodeFunction;
    /**
     * Creates a node with the default value of Object
     */
    private final Function<Object, N> nodeCreator;

    private final Function<T, J> jsonEncoder;
    private final Function<J, T> jsonDecoder;

    private final Class<T> type;
    private final Class<N> nodeType;
    private final Class<J> jsonType;

    public SettingCodec(Class<T> type, Class<N> nodeType, Class<J> jsonType,
                        Function<N, T> nodeToValFunction, BiConsumer<N, T> valToNodeFunction, Function<Object, N> nodeCreator,
                        Function<T, J> jsonEncoder, Function<J, T> jsonDecoder)
    {
        this.type = type;
        this.nodeType = nodeType;
        this.jsonType = jsonType;
        this.nodeToValFunction = nodeToValFunction;
        this.valToNodeFunction = valToNodeFunction;
        this.nodeCreator = nodeCreator;
        this.jsonEncoder = jsonEncoder;
        this.jsonDecoder = jsonDecoder;
    }
}
