package io.github.railroad.settings.handler;

import javafx.scene.Node;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public class Decoration <T extends Node> {
    private final String id;
    private final String treeId;
    private final Supplier<T> nodeCreator;

    /**
     * Constructor for the Decoration class
     * @param id The id of the decoration, used to retrieve it.
     * @param treeId The id of where the decoration should be placed in context of the tree.
     * @param nodeCreator The supplier to create the decoration's node.
     */
    public Decoration(String id, String treeId, Supplier<T> nodeCreator) {
        this.id = id;
        this.treeId = treeId;
        this.nodeCreator = nodeCreator;
    }
}
