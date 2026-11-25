package dev.railroadide.railroad.command;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.projectexplorer.ProjectExplorerPane;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.ide.ui.codeeditor.find.EditorFindHost;
import dev.railroadide.railroad.ide.ui.editor.EditorTab;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

import java.util.List;
import java.util.function.Consumer;

/**
 * Editing commands resolve the focused input at invocation, including search and dialog fields.
 */
public final class EditCommands {
    private EditCommands() {
    }

    /**
     * Undoes the last edit in the focused text input.
     */
    public static final Command<Void> UNDO = register("undo", KeyCode.Z);
    /**
     * Redoes the last undone edit in the focused text input.
     */
    public static final Command<Void> REDO = register("redo", KeyCode.Y);
    /**
     * Cuts the current selection.
     */
    public static final Command<Void> CUT = register("cut", KeyCode.X);
    /**
     * Copies the current selection.
     */
    public static final Command<Void> COPY = register("copy", KeyCode.C);
    /**
     * Pastes into the focused input.
     */
    public static final Command<Void> PASTE = register("paste", KeyCode.V);
    /**
     * Opens the find bar of the focused or active editor.
     */
    public static final Command<Void> FIND = registerFind("find", host -> host.bar().open(false),
        new KeybindData(KeyCode.F, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));

    /**
     * Opens the find bar of the focused or active editor with its replace row shown.
     */
    public static final Command<Void> REPLACE = registerFind("replace", host -> host.bar().open(true),
        new KeybindData(KeyCode.R, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN}));

    /**
     * Selects the next match of the current find query.
     */
    public static final Command<Void> FIND_NEXT = registerFind("find_next", host -> host.bar().findNext(),
        new KeybindData(KeyCode.F3, new KeyCombination.Modifier[0]));
    /**
     * Selects the previous match of the current find query.
     */
    public static final Command<Void> FIND_PREVIOUS = registerFind("find_previous",
        host -> host.bar().findPrevious(),
        new KeybindData(KeyCode.F3, new KeyCombination.Modifier[]{KeyCombination.SHIFT_DOWN}));

    private static Command<Void> registerFind(String action, Consumer<EditorFindHost> handler, KeybindData key) {
        return CommandRegistry.register(new Command<>("railroad:edit_" + action,
            "railroad.menu.edit." + action, c -> findHost(c) != null,
            c -> handler.accept(findHost(c)), List.of(key), Void.class));
    }

    private static EditorFindHost findHost(CommandContext<?> context) {
        Node focus = context.source() == null || context.source().getScene() == null
            ? context.source()
            : context.source().getScene().getFocusOwner();
        EditorFindHost host = EditorFindHost.enclosing(focus);
        if (host == null) {
            host = Services.EDITOR_TAB_MANAGER.activeTab().map(EditorTab::findHost).orElse(null);
        }

        return host != null && host.canFind() ? host : null;
    }

    private static Node target(CommandContext<?> context) {
        Node focus = context.source() == null || context.source().getScene() == null
            ? context.source()
            : context.source().getScene().getFocusOwner();
        for (Node node = focus; node != null; node = node.getParent()) {
            if (node instanceof TextInputControl || node instanceof TextEditorPane
                || node instanceof ProjectExplorerPane)
                return node;
        }

        return Services.EDITOR_TAB_MANAGER.activeTab().map(t -> (Node) t.view().activeEditor()).orElse(null);
    }

    private static Command<Void> register(String action, KeyCode key) {
        return CommandRegistry.register(new Command<>("railroad:edit_" + action,
            "railroad.menu.edit." + action, c -> enabled(action, target(c)),
            c -> execute(action, target(c), c),
            List.of(new KeybindData(key, new KeyCombination.Modifier[]{KeyCombination.SHORTCUT_DOWN})), Void.class));
    }

    private static boolean enabled(String action, Node target) {
        if (target instanceof ProjectExplorerPane pane) {
            Command<ExplorerTarget> command = explorerCommand(action);
            return command != null && command.canExecute(CommandContext.withArgument(null, pane, pane.commandTarget()));
        }

        if (target instanceof TextInputControl text)
            return switch (action) {
                case "undo" -> text.isEditable() && text.isUndoable();
                case "redo" -> text.isEditable() && text.isRedoable();
                case "copy" -> text.getSelection().getLength() > 0;
                case "cut" -> text.isEditable() && text.getSelection().getLength() > 0;
                case "paste" -> text.isEditable() && Clipboard.getSystemClipboard().hasString();
                default -> false;
            };

        if (target instanceof TextEditorPane text)
            return switch (action) {
                case "undo" -> text.isEditable() && text.isUndoAvailable();
                case "redo" -> text.isEditable() && text.isRedoAvailable();
                case "copy" -> text.getSelection().getLength() > 0;
                case "cut" -> text.isEditable() && text.getSelection().getLength() > 0;
                case "paste" -> text.isEditable() && Clipboard.getSystemClipboard().hasString();
                default -> false;
            };

        return false;
    }

    private static Command<ExplorerTarget> explorerCommand(String action) {
        return switch (action) {
            case "cut" -> Commands.CUT_PROJECT_EXPLORER_ITEM;
            case "copy" -> Commands.COPY_PROJECT_EXPLORER_ITEM;
            case "paste" -> Commands.PASTE_PROJECT_EXPLORER_ITEM;
            default -> null;
        };
    }

    private static void execute(String action, Node target, CommandContext<Void> context) {
        if (target instanceof ProjectExplorerPane pane) {
            CommandDispatcher.execute(explorerCommand(action),
                CommandContext.withArgument(context.project(), pane, pane.commandTarget()));
        } else if (target instanceof TextInputControl text) {
            switch (action) {
                case "undo" -> text.undo();
                case "redo" -> text.redo();
                case "cut" -> text.cut();
                case "copy" -> text.copy();
                case "paste" -> text.paste();
            }
        } else if (target instanceof TextEditorPane text) {
            switch (action) {
                case "undo" -> text.undo();
                case "redo" -> text.redo();
                case "cut" -> text.cut();
                case "copy" -> text.copy();
                case "paste" -> text.paste();
            }
        }
    }

    /**
     * Initializes the built-in command definitions.
     */
    public static void initialize() {
    }
}
