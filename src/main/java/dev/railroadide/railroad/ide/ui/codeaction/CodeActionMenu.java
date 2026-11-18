package dev.railroadide.railroad.ide.ui.codeaction;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditProblem;
import dev.railroadide.railroad.ide.sst.edit.api.WorkspaceEditResult;
import dev.railroadide.railroad.ide.sst.provider.api.CodeAction;
import dev.railroadide.railroad.ide.ui.refactor.WorkspaceEditPreviewView;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.stage.Popup;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/**
 * Popup listing the code actions of one discovery. The selected action is resolved lazily and its changes are
 * previewed below the list; Enter or a double click applies it. Changes reaching beyond the edited document must be
 * confirmed with a second Enter. Unavailable actions are listed with their reason and are never applied.
 * <p>
 * The menu is keyboard driven: Up and Down select, Enter applies and Escape closes. Call on the JavaFX thread.
 */
public final class CodeActionMenu implements AutoCloseable {
    private final CodeActionController controller;
    private final CodeActionController.Discovery discovery;
    private final Runnable onApplied;
    private final Popup popup = new Popup();
    private final RRListView<CodeAction> list = new RRListView<>();
    private final Label status = new Label();
    private final WorkspaceEditPreviewView preview = new WorkspaceEditPreviewView();
    private final Map<CodeAction, CodeActionController.Resolution> resolutions = new HashMap<>();
    private @Nullable CodeAction pendingChoice;
    private @Nullable CodeAction confirmed;
    private boolean closed;

    /**
     * Builds the menu without showing it.
     *
     * @param controller controller that produced the discovery
     * @param discovery actions to offer; must contain at least one action
     * @param onApplied invoked after an action was applied and the menu closed
     */
    public CodeActionMenu(
        CodeActionController controller,
        CodeActionController.Discovery discovery,
        Runnable onApplied
    ) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.onApplied = Objects.requireNonNull(onApplied, "onApplied");
        if (discovery.actions().isEmpty())
            throw new IllegalArgumentException("A code action menu needs actions");

        list.getStyleClass().add("code-action-list");
        list.setCellFactory(_ -> new ActionCell());
        list.getItems().setAll(discovery.actions());
        list.setFixedCellSize(28);
        list.setPrefHeight(Math.min(discovery.actions().size(), 8) * 28 + 4);
        list.setPrefWidth(520);
        list.getSelectionModel().selectedItemProperty().addListener((_, _, selected) -> {
            confirmed = null;
            if (selected != null) {
                requestResolution(selected);
            }
        });
        list.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                choose();
                event.consume();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                close();
                event.consume();
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                choose();
            }
        });

        status.getStyleClass().add("code-action-status");
        status.setWrapText(true);
        status.setMaxWidth(520);
        preview.getStyleClass().add("code-action-preview");
        preview.setPrefSize(520, 140);

        var content = new RRVBox(6, list, status, preview);
        content.getStyleClass().add("code-action-menu");
        popup.getContent().add(content);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setOnHidden(_ -> close());
        list.getSelectionModel().selectFirst();
    }

    /**
     * Shows the menu with keyboard focus on the first action.
     *
     * @param owner node owning the popup
     * @param screenX screen x coordinate of the menu
     * @param screenY screen y coordinate of the menu
     */
    public void show(Node owner, double screenX, double screenY) {
        if (closed)
            return;
        if (owner.getScene() != null && owner.getScene().getWindow() != null
            && owner.getScene().getWindow().isShowing()) {
            popup.show(owner, screenX, screenY);
        }
        list.requestFocus();
    }

    /**
     * Returns the offered actions.
     *
     * @return actions in display order
     */
    public List<CodeAction> actions() {
        return List.copyOf(list.getItems());
    }

    /**
     * Selects an action, resolving and previewing it.
     *
     * @param index zero-based index into {@link #actions()}
     */
    public void select(int index) {
        list.getSelectionModel().select(index);
    }

    /**
     * Applies the selected action, as pressing Enter does. When it is still being resolved, it is applied once the
     * resolution arrives; when it changes other documents, the first call asks for confirmation.
     */
    public void choose() {
        CodeAction selected = list.getSelectionModel().getSelectedItem();
        if (closed || selected == null)
            return;
        CodeActionController.Resolution resolution = resolutions.get(selected);
        if (resolution == null) {
            pendingChoice = selected;
            return;
        }
        pendingChoice = null;
        if (resolution.status() != CodeActionController.Status.READY) {
            showResolution(resolution);
            return;
        }
        if (resolution.affectsOtherDocuments() && confirmed != selected) {
            confirmed = selected;
            status.setText(L18n.localize("railroad.code_actions.confirm",
                Objects.requireNonNull(resolution.preview()).documents().size()
                    + resolution.preview().resources().size()));
            return;
        }

        WorkspaceEditResult result = controller.apply(resolution);
        if (result.applied()) {
            close();
            onApplied.run();
            return;
        }
        confirmed = null;
        resolutions.put(selected, new CodeActionController.Resolution(CodeActionController.Status.FAILED,
            discovery, resolution.action(), resolution.preview(), L18n.localize(
                "railroad.code_actions.apply_failed", String.join("\n",
                    result.problems().stream().map(WorkspaceEditProblem::message).toList()))));
        showResolution(resolutions.get(selected));
    }

    /**
     * Returns the status line, which explains the selected action's state.
     *
     * @return status text
     */
    public String statusText() {
        return status.getText();
    }

    /**
     * Returns the previewed changes of the selected action.
     *
     * @return preview lines in display order
     */
    public List<String> previewLines() {
        return preview.lines();
    }

    /**
     * Reports whether the menu was closed.
     *
     * @return whether the menu is closed
     */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed)
            return;
        closed = true;
        pendingChoice = null;
        controller.cancelResolution();
        popup.setOnHidden(null);
        popup.hide();
    }

    private void requestResolution(CodeAction action) {
        status.getStyleClass().remove("code-action-error");
        preview.clear();
        CodeActionController.Resolution cached = resolutions.get(action);
        if (cached != null) {
            showResolution(cached);
            return;
        }
        status.setText(L18n.localize("railroad.code_actions.computing"));
        controller.resolve(discovery, action).whenComplete((resolution, failure) -> {
            if (closed)
                return;
            if (failure != null) {
                Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                    ? failure.getCause()
                    : failure;
                if (cause instanceof CancellationException) {
                    if (list.getSelectionModel().getSelectedItem() == action) {
                        pendingChoice = null;
                        showResolution(new CodeActionController.Resolution(CodeActionController.Status.FAILED,
                            discovery, action, null, L18n.localize("railroad.code_actions.cancelled")));
                    }
                    return;
                }
                Railroad.LOGGER.error("Failed to resolve code action '{}'", action.title(), cause);
                resolution = new CodeActionController.Resolution(CodeActionController.Status.FAILED, discovery,
                    action, null, L18n.localize("railroad.code_actions.failed"));
            }
            resolutions.put(action, resolution);
            if (list.getSelectionModel().getSelectedItem() != action)
                return;
            showResolution(resolution);
            if (pendingChoice == action) {
                choose();
            }
        });
    }

    private void showResolution(CodeActionController.Resolution resolution) {
        preview.clear();
        if (resolution.preview() != null) {
            preview.show(resolution.preview());
        }
        boolean ready = resolution.status() == CodeActionController.Status.READY;
        status.getStyleClass().remove("code-action-error");
        if (!ready) {
            status.getStyleClass().add("code-action-error");
        }
        status.setText(ready ? L18n.localize("railroad.code_actions.ready") : resolution.message());
    }

    private static final class ActionCell extends ListCell<CodeAction> {
        @Override
        protected void updateItem(CodeAction item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("code-action-unavailable", "code-action-preferred");
            setTooltip(null);
            if (empty || item == null) {
                setText(null);
                return;
            }
            setText(item.available() ? item.title() : item.title() + " — " + item.unavailableReason());
            if (!item.available()) {
                getStyleClass().add("code-action-unavailable");
                setTooltip(new Tooltip(item.unavailableReason()));
            } else if (item.preferred()) {
                getStyleClass().add("code-action-preferred");
            }
        }
    }
}
