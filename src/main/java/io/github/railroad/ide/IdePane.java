package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.layout.Layout;
import io.github.railroad.layout.LayoutItem;
import io.github.railroad.layout.LayoutParseException;
import io.github.railroad.layout.LayoutParser;
import io.github.railroad.ui.defaults.*;
import io.github.railroad.utility.NodeTree;
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
            parent.setMinHeight(Railroad.getWindow().getHeight());
            parent.setMinWidth(Railroad.getWindow().getWidth());
            getChildren().add(parent);
            parseAndAddChildren(layout.getTree().getRoot().getChildren(), parent);
        } catch (LayoutParseException exception) {
            throw new RuntimeException(exception);
        }
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

            } else {
                return;
            }
            if (!item.getChildren().isEmpty()) {
                parseAndAddChildren(item.getChildren(), child);
            }
        }
    }


    private Pane parseItem(LayoutItem node, Pane parent) {
        System.out.println("[LAYOUTBUILDER] Adding new Item: " +node.getName());
        switch (node.getName().toUpperCase()) {
            case "HSPLIT": {
                var xBox = new RRHBox();
                if (node.hasProperty("size")) {
                    xBox.setMinHeight(parent.getHeight() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                    xBox.setMinWidth(parent.getWidth());
                }
                //xBox.getChildren().add(new Text("HSPLIT - " + node.getName()));
                return xBox;
            }
            case "VSPLIT": {
                var xBox = new RRVBox();

                if (node.hasProperty("size")) {
                    xBox.setMinWidth(parent.getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                    xBox.setMinHeight(parent.getHeight());
                }
                //xBox.getChildren().add(new Text("VSPLIT - " + node.getName()));
                return xBox;
            }
            case "MENU": {
                var xBox = new RRMainMenu();
                if (node.hasProperty("size")) {
                    xBox.setMinHeight(parent.getHeight() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                    xBox.setMinWidth(parent.getWidth());
                }
                return xBox;
            }
            case "QUICKRUN": {
                var xBox = new RRQuickRun();
                if (node.hasProperty("size")) {
                    xBox.setMinWidth(parent.getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                return xBox;
            }
            case "FILEEXPLORER": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("Files"));
                if (node.hasProperty("size")) {
                    xBox.setMinWidth(parent.getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                return xBox;
            }

            case "TEXTEDITOR": {
                var xBox = new RRTexteditor();
                xBox.getChildren().add(new Text("TEXT"));
                if (node.hasProperty("size")) {
                    xBox.setMinWidth(parent.getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                return xBox;
            }
            case "TERMINAL": {
                var xBox = new RRTerminal();
                xBox.getChildren().add(new Text("TERMINAL"));
                return xBox;
            }
            case "GRADLETASKS": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("GRADLE"));
                return xBox;
            }
            case "TABS": {
                var xBox = new RRTabPane();
                if (node.hasProperty("size")) {
                    xBox.setMinWidth(parent.getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                return xBox;
            }
            default: {
                throw new IllegalArgumentException("Unknown layout item: " + node.getName());
            }
        }
    }
}
