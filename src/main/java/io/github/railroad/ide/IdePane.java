package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.layout.Layout;
import io.github.railroad.layout.LayoutItem;
import io.github.railroad.layout.LayoutParseException;
import io.github.railroad.layout.LayoutParser;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.NodeTree;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.nio.file.Path;
import java.util.List;

import static java.lang.Double.parseDouble;

public class IdePane extends Pane {
    public IdePane() {
        try {
            Layout layout = LayoutParser.parse(Path.of("template.railayout"));
            Pane parent = parseItem(layout.getTree().getRoot().getValue());
            getChildren().add(parent);
            parseAndAddChildren(layout.getTree().getRoot().getChildren(), parent);
        } catch (LayoutParseException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void parseAndAddChildren(List<NodeTree.Node<LayoutItem>> children, Pane parent) {
        for (NodeTree.Node<LayoutItem> item : children) {
            Pane child = parseItem(item.getValue());
            if (child != null) {
                parent.getChildren().add(child);
            } else {
                return;
            }
            if (!item.getChildren().isEmpty()) {
                parseAndAddChildren(item.getChildren(), child);
            }
        }
    }


    private Pane parseItem(LayoutItem node) {
        System.out.println("[LAYOUTBUILDER] Adding new Item: " +node.getName());
        switch (node.getName().toUpperCase()) {
            case "VSPLIT": {
                var xBox = new RRHBox();
                xBox.autosize();
                if (node.hasProperty("size")) {
                    System.out.println("Addig size to Box");
                    xBox.setMinWidth(Railroad.getWindow().getWidth() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                HBox.setHgrow(xBox, Priority.ALWAYS);
                //xBox.getChildren().add(new Text("HSPLIT - " + node.getName()));
                return xBox;
            }
            case "HSPLIT": {
                var xBox = new RRVBox();

                if (node.hasProperty("size")) {
                    System.out.println("Addig size to Box");
                    xBox.setMinHeight(Railroad.getWindow().getHeight() * (parseDouble(node.getProperty("size").toString().replace("%", "")) / 100));
                }
                VBox.setVgrow(xBox, Priority.ALWAYS);
                //xBox.getChildren().add(new Text("VSPLIT - " + node.getName()));
                return xBox;
            }
            case "FILEEXPLORER": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("Files"));
                return xBox;
            }

            case "TEXTEDITOR": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("TEXT"));
                return xBox;
            }
            case "TERMINAL": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("TERMINAL"));
                return xBox;
            }
            case "GRADLETASKS": {
                var xBox = new RRVBox();
                xBox.getChildren().add(new Text("GRADLE"));
                return xBox;
            }

            default: {
                throw new IllegalArgumentException("Unknown layout item: " + node.getName());
            }
        }
    }
}
