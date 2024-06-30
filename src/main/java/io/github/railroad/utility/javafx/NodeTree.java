package io.github.railroad.utility.javafx;

import io.github.railroad.Railroad;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Getter
public class NodeTree<T> {
    private Node<T> root;

    public NodeTree() {
        this.root = null;
    }

    public NodeTree(Node<T> root) {
        this.root = root;
    }

    public void setRoot(Node<T> root) {
        if (this.root != null) {
            throw new IllegalStateException("Root node is already set");
        }

        this.root = root;
    }

    public void print() {
        Railroad.LOGGER.info(root.toString());
    }

    @Getter
    public static class Node<T> {
        private final T value;
        private final List<Node<T>> children = new ArrayList<>();

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

        public void addChild(Node<T> child) {
            children.add(child);
        }

        public void removeChild(Node<T> child) {
            children.remove(child);
        }
    }
}
