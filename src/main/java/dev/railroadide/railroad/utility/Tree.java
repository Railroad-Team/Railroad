package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.Railroad;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A generic tree data structure that can hold any type of value.
 *
 * @param <T> the type of value held by the tree nodes
 */
@Getter
public class Tree<T> {
    private Node<T> root;

    /**
     * Constructs an empty tree with no root node.
     */
    public Tree() {
        this.root = null;
    }

    /**
     * Constructs a tree with the specified root node.
     *
     * @param root the root node of the tree
     */
    public Tree(Node<T> root) {
        this.root = root;
    }

    /**
     * Sets the root node of the tree. This method can only be called once; if the root is already set, it will throw an
     * exception.
     *
     * @param root the root node to set
     * @throws IllegalStateException if the root node is already set
     */
    public void setRoot(Node<T> root) {
        if (this.root != null)
            throw new IllegalStateException("Root node is already set");

        this.root = root;
    }

    /**
     * Prints the tree structure to the console.
     */
    public void print() {
        Railroad.LOGGER.info(root.toString());
    }

    /**
     * Represents a node in the tree, holding a value and a list of child nodes.
     *
     * @param <T> the type of value held by the node
     */
    @Getter
    public static class Node<T> {
        private final T value;
        private final List<Node<T>> children = new ArrayList<>();

        /**
         * Constructs a node with the specified value and an optional list of child nodes.
         *
         * @param value the value of the node
         * @param children the child nodes of this node
         */
        @SafeVarargs
        public Node(T value, Node<T>... children) {
            this.value = value;
            Collections.addAll(this.children, children);
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append("Node{value=").append(value);

            if (!children.isEmpty()) {
                sb.append(", children=").append(children);
            }

            sb.append('}');
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;

            if (!(o instanceof Node<?> node))
                return false;

            return Objects.equals(value, node.value) && Objects.equals(children, node.children);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, children);
        }

        /**
         * Adds a child node to this node.
         *
         * @param child the child node to add
         */
        public void addChild(Node<T> child) {
            children.add(child);
        }

        /**
         * Removes a child node from this node.
         *
         * @param child the child node to remove
         */
        public void removeChild(Node<T> child) {
            children.remove(child);
        }
    }
}
