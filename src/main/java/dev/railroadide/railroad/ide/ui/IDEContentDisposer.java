package dev.railroadide.railroad.ide.ui;

import com.kodedu.terminalfx.Terminal;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.ui.setup.TerminalFactory;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Disposes resources held by an IDE-owned content tree. */
final class IDEContentDisposer {
    private IDEContentDisposer() {
    }

    static void dispose(Tab tab, Set<Object> disposed) {
        if (tab == null || !disposed.add(tab))
            return;

        Node content = tab.getContent();
        tab.setContent(null);
        dispose(content, disposed);
    }

    static void dispose(Node root) {
        dispose(root, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    static void dispose(Node node, Set<Object> disposed) {
        if (node == null || !disposed.add(node))
            return;

        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                dispose(tab, disposed);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                dispose(child, disposed);
            }
        }

        if (node instanceof Terminal terminal) {
            TerminalFactory.close(terminal);
        } else if (node instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                Railroad.LOGGER.warn("Failed to dispose IDE content {}", node.getClass().getName(), exception);
            }
        }
    }
}
