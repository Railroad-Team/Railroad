package dev.railroadide.railroad.ide.ui.debugger;

import dev.railroadide.railroad.command.Command;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.RunCommands;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.debug.model.DebugFrame;
import dev.railroadide.railroad.debug.model.DebugThread;
import dev.railroadide.railroad.debug.model.DebugVariable;
import dev.railroadide.railroad.ide.debug.DebuggingManager;
import dev.railroadide.railroad.ide.debug.WatchResult;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.TextFieldSize;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.layout.Region;
import javafx.util.Subscription;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import javafx.geometry.Orientation;

/**
 * Project debugger inspection view. All backend access goes through the manager.
 */
public final class DebuggerPane extends BorderPane implements AutoCloseable {
    private final DebuggingManager debugging;
    private final ComboBox<DebugThread> threads = new ComboBox<>();
    private final RRListView<DebugFrame> frames = new RRListView<>();
    private final TreeView<VariableNode> variables = new TreeView<>(new TreeItem<>());
    private final List<TreeItem<VariableNode>> localItems = new ArrayList<>();
    private final List<TreeItem<VariableNode>> watchItems = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean syncing;
    private boolean closed;
    private long treeRevision;

    public DebuggerPane(Project project) {
        debugging = project.getDebuggingManager();
        getStyleClass().add("debugger-pane");
        setMinSize(0, 0);
        threads.getStyleClass().addAll("rr-combo-box", "debugger-thread-selector");
        frames.getStyleClass().add("debugger-list");
        threads.setMinSize(0, 0);
        frames.setMinSize(0, 0);
        threads.setItems(debugging.getThreads());
        frames.setItems(debugging.getFrames());
        threads.setCellFactory(_ -> cell(thread -> thread.name() + " (#" + thread.id() + ")"));
        frames.setCellFactory(_ -> cell(frame -> frame.displayName() + "():" + frame.line()));
        threads.setButtonCell(cell(thread -> thread.name() + " (#" + thread.id() + ")"));
        threads.promptTextProperty().bind(new LocalizedLabel("railroad.debugger.select_thread").textProperty());
        threads.setMaxWidth(Double.MAX_VALUE);
        threads.disableProperty().bind(Bindings.isEmpty(debugging.getThreads()));
        frames.setPlaceholder(emptyLabel("no_frames"));
        configureTree(variables);
        var expression = new RRTextField("railroad.debugger.watch_expression");
        expression.setTextFieldSize(TextFieldSize.SMALL);
        expression.setMinWidth(0);
        expression.getStyleClass().add("debugger-expression-field");
        expression.accessibleTextProperty().bind(expression.promptTextProperty());
        var add = new RRButton("", FontAwesomeSolid.PLUS);
        add.setVariant(ButtonVariant.GHOST);
        add.setSquare(true);
        add.setTooltip(new LocalizedTooltip("railroad.debugger.add_watch"));
        add.accessibleTextProperty().bind(add.getTooltip().textProperty());
        add.setButtonSize(ButtonSize.SMALL);
        add.disableProperty().bind(Bindings.createBooleanBinding(
            () -> expression.getText().isBlank(), expression.textProperty()));
        Runnable addWatch = () -> {
            if (expression.getText().isBlank())
                return;
            debugging.addWatch(expression.getText());
            expression.clear();
        };
        add.setOnAction(_ -> addWatch.run());
        expression.setOnAction(_ -> addWatch.run());
        var remove = new RRButton("", FontAwesomeSolid.MINUS);
        remove.setVariant(ButtonVariant.GHOST);
        remove.setSquare(true);
        remove.setTooltip(new LocalizedTooltip("railroad.debugger.remove_watch"));
        remove.accessibleTextProperty().bind(remove.getTooltip().textProperty());
        remove.setButtonSize(ButtonSize.SMALL);
        remove.disableProperty().bind(Bindings.createBooleanBinding(
            () -> selectedWatch() == null, variables.getSelectionModel().selectedItemProperty()));
        remove.setOnAction(_ -> {
            WatchResult selected = selectedWatch();
            if (selected != null) {
                debugging.removeWatch(selected.watch());
            }
        });
        var expressionBar = new HBox(6, expression, add, remove);
        expressionBar.setAlignment(Pos.CENTER_LEFT);
        expressionBar.getStyleClass().add("debugger-expression-bar");
        HBox.setHgrow(expression, Priority.ALWAYS);
        var left = new BorderPane(frames);
        var threadBar = new HBox(threads);
        HBox.setHgrow(threads, Priority.ALWAYS);
        threadBar.getStyleClass().add("debugger-thread-bar");
        left.setTop(threadBar);
        left.setMinSize(0, 0);
        var right = new BorderPane(treeContent(variables, "no_variables"));
        right.setTop(expressionBar);
        right.setMinSize(0, 0);
        var split = new SplitPane(left, right);
        split.getStyleClass().add("debugger-split");
        split.setDividerPositions(0.32);
        setCenter(split);
        var status = new LocalizedLabel("railroad.debugger.state.new");
        status.getStyleClass().add("debugger-status");
        var title = new LocalizedLabel("railroad.debugger.inspection");
        title.getStyleClass().add("debugger-title");
        title.setMinWidth(0);
        var separator = new Separator(Orientation.VERTICAL);
        var controls = new HBox(2,
            debugControl(project, RunCommands.STOP, FontAwesomeSolid.STOP),
            debugControl(project, RunCommands.RESUME, FontAwesomeSolid.PLAY),
            debugControl(project, RunCommands.PAUSE, FontAwesomeSolid.PAUSE),
            debugControl(project, RunCommands.STEP_OVER, FontAwesomeSolid.ARROW_RIGHT),
            debugControl(project, RunCommands.STEP_INTO, FontAwesomeSolid.ARROW_DOWN),
            debugControl(project, RunCommands.STEP_OUT, FontAwesomeSolid.ARROW_UP));
        controls.setAlignment(Pos.CENTER_LEFT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        status.setMinWidth(0);
        var header = new HBox(8, controls, separator, title, spacer, status);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("debugger-header");
        var error = new Label();
        error.getStyleClass().add("debugger-error");
        error.setWrapText(true);
        error.setMaxWidth(Double.MAX_VALUE);
        error.textProperty().bind(debugging.inspectionErrorProperty());
        error.visibleProperty().bind(Bindings.createBooleanBinding(
            () -> error.getText() != null && !error.getText().isBlank(), error.textProperty()));
        error.managedProperty().bind(error.visibleProperty());
        setTop(new VBox(header, error));
        subscriptions.add(debugging.getState().subscribe(state -> {
            status.setKey("railroad.debugger.state." + state.name().toLowerCase(Locale.ROOT));
            for (String name : List.of("running", "suspended", "failed")) {
                status.pseudoClassStateChanged(PseudoClass.getPseudoClass(name),
                    state.name().equalsIgnoreCase(name));
            }
        }));
        subscriptions.add(threads.getSelectionModel().selectedItemProperty().subscribe((_, thread) -> {
            if (!syncing && thread != null) {
                debugging.selectThread(thread);
            }
        }));
        subscriptions.add(frames.getSelectionModel().selectedItemProperty().subscribe((_, frame) -> {
            if (!syncing && frame != null) {
                debugging.selectFrame(frame);
            }
        }));
        subscriptions.add(debugging.selectedThreadProperty().subscribe(thread -> {
            syncing = true;
            threads.getSelectionModel().select(thread);
            syncing = false;
        }));
        subscriptions.add(debugging.selectedFrameProperty().subscribe(frame -> {
            treeRevision++;
            syncing = true;
            frames.getSelectionModel().select(frame);
            syncing = false;
        }));
        subscriptions.add(debugging.getVariables().subscribe(this::refreshVariables));
        subscriptions.add(debugging.getWatchResults().subscribe(this::refreshWatches));
        refreshVariables();
        refreshWatches();
    }

    private RRButton debugControl(Project project, Command<RunConfiguration<?>> command, FontAwesomeSolid icon) {
        var button = new RRButton("", icon);
        button.setSquare(true);
        button.setButtonSize(ButtonSize.SMALL);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(command.displayNameKey()));
        button.accessibleTextProperty().bind(button.getTooltip().textProperty());
        button.getStyleClass().addAll("toolbar-button", "debugger-control");
        CommandButtons.bind(button, command, () -> CommandContext.forProject(project, this),
            debugging.activeProperty(), debugging.pausedProperty(), debugging.getCommandPending(),
            debugging.getState(), debugging.selectedThreadProperty());
        return button;
    }

    private WatchResult selectedWatch() {
        TreeItem<VariableNode> item = variables.getSelectionModel().getSelectedItem();
        while (item != null && item.getParent() != variables.getRoot()) {
            item = item.getParent();
        }
        return item == null || item.getValue() == null ? null : item.getValue().watch;
    }

    private static LocalizedLabel emptyLabel(String key) {
        var label = new LocalizedLabel("railroad.debugger." + key);
        label.getStyleClass().add("debugger-empty");
        label.setWrapText(true);
        label.setMaxWidth(240);
        return label;
    }

    private static StackPane treeContent(TreeView<VariableNode> tree, String emptyKey) {
        var empty = emptyLabel(emptyKey);
        empty.visibleProperty().bind(Bindings.isEmpty(tree.getRoot().getChildren()));
        empty.managedProperty().bind(empty.visibleProperty());
        empty.setMouseTransparent(true);
        var content = new StackPane(tree, empty);
        content.setMinSize(0, 0);
        return content;
    }

    private static <T> ListCell<T> cell(Function<T, String> formatter) {
        return new ListCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatter.apply(item));
                setTooltip(empty || item == null ? null : new Tooltip(getText()));
            }
        };
    }

    private void configureTree(TreeView<VariableNode> tree) {
        tree.getStyleClass().addAll("rr-tree-view", "debugger-tree");
        tree.setMinSize(0, 0);
        tree.setFixedCellSize(-1);
        tree.setShowRoot(false);
        tree.getRoot().setExpanded(true);
        tree.setCellFactory(_ -> new TreeCell<>() {
            @Override
            protected void updateItem(VariableNode node, boolean empty) {
                super.updateItem(node, empty);
                setText(null);
                setGraphic(null);
                setTooltip(empty || node == null ? null : new Tooltip(node.text));
                setAccessibleText(empty || node == null ? null : node.text);
                if (!empty && node != null) {
                    var text = new TextFlow();
                    text.getStyleClass().add("debugger-variable-text");
                    text.setMinWidth(Region.USE_PREF_SIZE);
                    text.setMaxWidth(Region.USE_PREF_SIZE);
                    if (node.variable != null) {
                        String name = node.watch == null ? node.variable.name() : node.watch.watch().expression();
                        text.getChildren().addAll(span(name, "debugger-variable-name"),
                            span(" = ", "debugger-variable-type"),
                            span(node.variable.value(), "debugger-variable-value"),
                            span("  (" + node.variable.type() + ")", "debugger-variable-type"));
                    } else {
                        text.getChildren().add(span(node.text, node.watch != null && node.watch.error() != null
                            ? "debugger-variable-error"
                            : "debugger-variable-type"));
                    }
                    var row = new HBox(6);
                    row.setAlignment(Pos.CENTER_LEFT);
                    row.setMinWidth(Region.USE_PREF_SIZE);
                    if (node.watch != null) {
                        var icon = new FontIcon(FontAwesomeSolid.EYE);
                        icon.getStyleClass().add("debugger-watch-icon");
                        row.getChildren().add(icon);
                    }
                    row.getChildren().add(text);
                    setGraphic(row);
                }
                pseudoClassStateChanged(PseudoClass.getPseudoClass("evaluation-error"),
                    !empty && node != null && node.watch != null && node.watch.error() != null);
            }
        });
    }

    private static Text span(String value, String styleClass) {
        var text = new Text(value);
        text.getStyleClass().add(styleClass);
        return text;
    }

    private void refreshVariables() {
        localItems.clear();
        debugging.getVariables().forEach(value -> localItems.add(variableItem(value, null)));
        refreshInspection();
    }

    private void refreshWatches() {
        watchItems.clear();
        for (WatchResult result : debugging.getWatchResults()) {
            if (result.value() != null) {
                watchItems.add(variableItem(result.value(), result));
            } else {
                String value = result.error() == null ? "?" : "<" + result.error() + ">";
                watchItems
                    .add(new TreeItem<>(new VariableNode(null, result, result.watch().expression() + " = " + value)));
            }
        }
        refreshInspection();
    }

    private void refreshInspection() {
        var items = new ArrayList<>(watchItems);
        items.addAll(localItems);
        variables.getRoot().getChildren().setAll(items);
    }

    private TreeItem<VariableNode> variableItem(DebugVariable value, WatchResult watch) {
        var node = new VariableNode(value, watch, (watch == null ? value.name() : watch.watch().expression()) + " = "
            + value.value() + "  (" + value.type() + ")");
        var item = new TreeItem<>(node);
        if (value.hasChildren()) {
            item.getChildren().add(new TreeItem<>(new VariableNode(null, null, "?")));
            item.expandedProperty().addListener((_, _, expanded) -> {
                if (expanded && !node.loaded) {
                    node.loaded = true;
                    loadChildren(item, value, 0);
                }
            });
        }
        return item;
    }

    private void loadChildren(TreeItem<VariableNode> item, DebugVariable value, int start) {
        long request = treeRevision;
        debugging.expandVariable(value.childrenReference(), start, 100)
            .whenComplete((children, failure) -> Platform.runLater(() -> {
                if (closed || request != treeRevision)
                    return;
                if (failure != null) {
                    Throwable cause = failure;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    item.getChildren()
                        .setAll(new TreeItem<>(new VariableNode(null, null, "<" + cause.getMessage() + ">")));
                    item.getValue().loaded = false;
                    return;
                }
                item.getChildren().setAll(children.stream().map(child -> variableItem(child, null)).toList());
                int next = start + children.size();
                if (next < value.indexedChildren() && !children.isEmpty()) {
                    var more = new TreeItem<>(new VariableNode(null, null, "[" + next + "?]"));
                    more.getChildren().add(new TreeItem<>(new VariableNode(null, null, "?")));
                    more.expandedProperty().addListener((_, _, expanded) -> {
                        if (expanded && !more.getValue().loaded) {
                            more.getValue().loaded = true;
                            loadChildren(more, value, next);
                        }
                    });
                    item.getChildren().add(more);
                }
            }));
    }

    @Override
    public void close() {
        closed = true;
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
        threads.setItems(null);
        frames.setItems(null);
    }

    private static final class VariableNode {
        private final DebugVariable variable;
        private final WatchResult watch;
        private final String text;
        private boolean loaded;

        private VariableNode(DebugVariable variable, WatchResult watch, String text) {
            this.variable = variable;
            this.watch = watch;
            this.text = text;
        }
    }
}
