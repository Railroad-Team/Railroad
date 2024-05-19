package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.layout.Layout;
import io.github.railroad.layout.LayoutItem;
import io.github.railroad.layout.LayoutParseException;
import io.github.railroad.layout.LayoutParser;
import io.github.railroad.ui.defaults.*;
import io.github.railroad.utility.NodeTree;
import javafx.beans.binding.Bindings;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.List;

import static java.lang.Double.parseDouble;

public class IdePane extends Pane {
    public IdePane() {
        try {
            Layout layout = LayoutParser.parse(Path.of("template.railayout"));
            Pane parent = parseItem(layout.getTree().getRoot().getValue(), this);
            bindParentSize(parent);
            getChildren().add(parent);
            parseAndAddChildren(layout.getTree().getRoot().getChildren(), parent);
        } catch (LayoutParseException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void bindParentSize(Pane parent) {
        parent.minHeightProperty().bind(Railroad.getWindow().heightProperty());
        parent.minWidthProperty().bind(Railroad.getWindow().widthProperty());
    }

    private void parseAndAddChildren(List<NodeTree.Node<LayoutItem>> children, Pane parent) {
        for (NodeTree.Node<LayoutItem> item : children) {
            Pane child = parseItem(item.getValue(), parent);
            if (child != null) {
                if (parent instanceof RRTabPane) {
                    ((RRTabPane) parent).getTabs().add(new Tab(item.getValue().getProperty("tabname").toString(), child));
                } else {
                    parent.getChildren().add(child);
                }
                if (!item.getChildren().isEmpty()) {
                    parseAndAddChildren(item.getChildren(), child);
                }
            }
        }
    }

    private Pane parseItem(LayoutItem node, Pane parent) {
        System.out.println("[LAYOUTBUILDER] Adding new Item: " + node.getName());
        Pane pane = createPaneForItem(node);
        if (pane != null) {
            bindChildSize(pane, node, parent);
        }
        return pane;
    }

    private Pane createPaneForItem(LayoutItem node) {
        switch (node.getName().toUpperCase()) {
            case "HSPLIT":
                return new RRHBox();
            case "VSPLIT":
                return new RRVBox();
            case "MENU":
                return new RRMainMenu();
            case "QUICKRUN":
                return new RRQuickRun();
            case "FILEEXPLORER":
                RRVBox fileExplorer = new RRVBox();
                fileExplorer.getChildren().add(new Text("Files"));
                return fileExplorer;
            case "TEXTEDITOR":
                RRTexteditor textEditor = new RRTexteditor();
                textEditor.getChildren().add(new Text("TEXTEDITOR"));
                return textEditor;
            case "TERMINAL":
                RRTerminal terminal = new RRTerminal();
                terminal.getChildren().add(new Text("TERMINAL"));
                return terminal;
            case "GRADLETASKS":
                RRVBox gradleTasks = new RRVBox();
                gradleTasks.getChildren().add(new Text("GRADLE"));
                return gradleTasks;
            case "TABS":
                return new RRTabPane();
            default:
                throw new IllegalArgumentException("Unknown layout item: " + node.getName());
        }
    }

    private void bindChildSize(Pane child, LayoutItem node, Pane parent) {
        if (node.hasProperty("size")) {
            String size = node.getProperty("size").toString().replace("%", "");
            double sizePercentage = parseDouble(size) / 100;

            if (child instanceof RRHBox || child instanceof RRMainMenu) {
                child.minHeightProperty().bind(Bindings.multiply(parent.heightProperty(), sizePercentage));
                child.minWidthProperty().bind(parent.widthProperty());
            } else if (child instanceof RRVBox || child instanceof RRQuickRun || child instanceof RRTabPane) {
                child.minWidthProperty().bind(Bindings.multiply(parent.widthProperty(), sizePercentage));
                child.minHeightProperty().bind(parent.heightProperty());
            } else {
                child.minWidthProperty().bind(parent.widthProperty());
                child.minHeightProperty().bind(parent.heightProperty());
            }
        } else {
            child.minWidthProperty().bind(parent.widthProperty());
            child.minHeightProperty().bind(parent.heightProperty());
        }
    }
}