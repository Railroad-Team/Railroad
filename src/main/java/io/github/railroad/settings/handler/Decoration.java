package io.github.railroad.settings.handler;

import javafx.scene.Node;

import java.util.function.Supplier;

/**
 * Constructor for the Decoration class
 *
 * @param id          The id of the decoration, used to retrieve it.
 * @param treeId      The id of where the decoration should be placed in context of the tree.
 * @param nodeCreator The supplier to create the decoration's node.
 */
public record Decoration<T extends Node>(String id, String treeId, Supplier<T> nodeCreator) {}
