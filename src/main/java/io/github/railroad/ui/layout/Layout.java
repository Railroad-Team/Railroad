package io.github.railroad.ui.layout;

import com.kodedu.terminalfx.TerminalBuilder;
import com.panemu.tiwulfx.control.dock.DetachableTab;
import com.panemu.tiwulfx.control.dock.DetachableTabPane;
import com.spencerwi.either.Either;
import io.github.railroad.Railroad;
import io.github.railroad.core.ui.RRBorderPane;
import io.github.railroad.core.ui.RRHBox;
import io.github.railroad.core.ui.RRVBox;
import io.github.railroad.utility.Tree;
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Layout(Tree<LayoutItem> tree) {
    public void print() {
        tree.print();
    }

    public void apply(RRBorderPane pane) {
        Tree.Node<LayoutItem> root = tree.getRoot();
        Either<Pane, DetachableTabPane> parent = parseItem(root.getValue(), Either.left(pane));
        if(parent == null) {
            Railroad.LOGGER.error("Failed to parse root item");
            return;
        }

        if(parent.isLeft()) {
            bindParentSize(parent.getLeft());
            pane.getChildren().add(parent.getLeft());
            parseAndAddChildren(root.getChildren(), parent);
        } else {
            Railroad.LOGGER.error("Root item cannot be a tab");
        }
    }

    private void parseAndAddChildren(List<Tree.Node<LayoutItem>> children, Either<Pane, DetachableTabPane> parent) {
        for (Tree.Node<LayoutItem> item : children) {
            Either<Pane, DetachableTabPane> child = parseItem(item.getValue(), parent);
            if (child != null) {
                if (parent.isRight()) {
                    DetachableTabPane tabPane = parent.getRight();
                    var tab = new DetachableTab(item.getValue().getProperty("tabname").toString(), child.getRight());
                    tab.setClosable(false);
                    tabPane.getTabs().add(tab);
                } else {
                    parent.getLeft().getChildren().add(child.getLeft());
                }

                if (!item.getChildren().isEmpty()) {
                    parseAndAddChildren(item.getChildren(), child);
                }
            }
        }
    }

    private @Nullable Either<Pane, DetachableTabPane> parseItem(LayoutItem node, Either<Pane, DetachableTabPane> parent) {
        Railroad.LOGGER.info("[LayoutBuilder] Adding new Item: {}", node.getName());
        try {
            Either<Pane, DetachableTabPane> pane = createPaneForItem(node);
            bindChildSize(pane, node, parent);
            return pane;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Either<Pane, DetachableTabPane> createPaneForItem(LayoutItem node) {
        switch (node.getName()) {
            case "HSplit":
                return Either.left(new RRHBox());
            case "VSplit":
                return Either.left(new RRVBox());
            case "FileExplorer":
                var fileExplorer = new RRVBox();
                fileExplorer.getChildren().add(new Text("File Explorer"));
                return Either.left(fileExplorer);
            case "TextEditor":
                return null;
            case "Terminal":
                return Either.left(new TerminalBuilder().newTerminal().getTerminal());
            case "GradleTasks":
                var gradleTasks = new RRVBox();
                gradleTasks.getChildren().add(new Text("Gradle Tasks"));
                return Either.left(gradleTasks);
            default:
                throw new IllegalArgumentException("Unknown layout item: " + node.getName());
        }
    }

    private void bindParentSize(Pane parent) {
        parent.minHeightProperty().bind(Railroad.getWindow().heightProperty());
        parent.minWidthProperty().bind(Railroad.getWindow().widthProperty());
    }

    private static void bindChildSize(Either<Pane, DetachableTabPane> child, LayoutItem node, Either<Pane, DetachableTabPane> parent) {
        // TODO: Can't get size of a node, so I'm not sure how we can do this
//        if (node.hasProperty("size")) {
//            String size = node.getProperty("size").toString().replace("%", "");
//            double sizePercentage = Double.parseDouble(size) / 100;
//
//            if (child instanceof RRHBox hbox) {
//                hbox.minHeightProperty().bind(Bindings.multiply(parent.heightProperty(), sizePercentage));
//                hbox.minWidthProperty().bind(parent.widthProperty());
//            } else if (child instanceof RRVBox/* || child instanceof TabPane*/) {
//                child.minWidthProperty().bind(Bindings.multiply(parent.widthProperty(), sizePercentage));
//                child.minHeightProperty().bind(parent.heightProperty());
//            } else {
//                child.minWidthProperty().bind(parent.widthProperty());
//                child.minHeightProperty().bind(parent.heightProperty());
//            }
//        } else {
//            child.minWidthProperty().bind(parent.widthProperty());
//            child.minHeightProperty().bind(parent.heightProperty());
//        }
    }
}
