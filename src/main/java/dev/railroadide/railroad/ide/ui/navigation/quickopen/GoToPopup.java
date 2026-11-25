package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Popup;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * Popup that searches the items of a {@link GoToContributor} as the user types and opens the chosen one.
 *
 * @param <T> item type
 */
public final class GoToPopup<T> {
    private static final int MAX_RESULTS = 200;
    private static final int VISIBLE_ROWS = 12;
    private static final double ROW_HEIGHT = 28;
    private static @Nullable GoToPopup<?> showing;

    private final GoToContributor<T> contributor;
    private final Popup popup = new Popup();
    private final RRTextField searchField = new RRTextField();
    private final RRListView<GoToSearch.Result<T>> list = new RRListView<>();
    private final Label placeholder = new Label();
    private final CompletableFuture<Void> loaded = new CompletableFuture<>();
    private List<T> items = List.of();
    private volatile boolean closed;

    private GoToPopup(GoToContributor<T> contributor) {
        this.contributor = contributor;

        var title = new LocalizedLabel(contributor.titleKey());
        title.getStyleClass().add("go-to-title");
        searchField.getStyleClass().add("go-to-field");
        searchField.promptTextProperty().bind(new LocalizedTextProperty(searchField, "promptText",
            "railroad.ide.go_to.prompt"));
        searchField.textProperty().addListener((_, _, _) -> refresh());

        placeholder.getStyleClass().add("go-to-placeholder");
        placeholder.setText(L18n.localize("railroad.ide.go_to.loading"));
        list.setAnimationsEnabled(false);
        list.setFocusTraversable(false);
        list.setPlaceholder(placeholder);
        list.setCellFactory(_ -> new ResultCell());
        list.setFixedCellSize(ROW_HEIGHT);
        list.prefHeightProperty().bind(Bindings.max(1, Bindings.min(VISIBLE_ROWS, Bindings.size(list.getItems())))
            .multiply(ROW_HEIGHT).add(4));
        list.setMinHeight(Region.USE_PREF_SIZE);
        list.setMaxHeight(Region.USE_PREF_SIZE);

        var count = new Label();
        count.getStyleClass().add("go-to-count");
        count.textProperty().bind(Bindings.size(list.getItems()).asString());
        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        var hint = new LocalizedLabel("railroad.ide.go_to.hint");
        hint.getStyleClass().add("go-to-caption");
        var footer = new HBox(count, space, hint);
        footer.getStyleClass().add("go-to-footer");

        var root = new VBox(title, searchField, new StackPane(list), footer);
        root.getStyleClass().add("go-to-popup");
        root.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.setOnHidden(_ -> close());
    }

    /**
     * Shows a popup over the window of a node, replacing any go-to popup already shown, and starts collecting the
     * project's items in the background.
     *
     * @param <T> item type
     * @param source node in the window to show the popup over
     * @param project project to search
     * @param contributor source of the items
     * @return shown popup, or {@code null} when the source is not in a showing window
     */
    public static <T> @Nullable GoToPopup<T> show(Node source, Project project, GoToContributor<T> contributor) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(project, "project");
        if (source.getScene() == null || source.getScene().getWindow() == null
            || !source.getScene().getWindow().isShowing())
            return null;

        if (showing != null) {
            showing.hide();
        }

        var popup = new GoToPopup<>(contributor);
        showing = popup;
        popup.load(project);
        popup.showOver(source.getScene().getRoot());
        return popup;
    }

    private void showOver(Node owner) {
        Window window = owner.getScene().getWindow();
        Region root = (Region) popup.getContent().getFirst();
        double width = Math.min(640, Math.max(320, window.getWidth() - 40));
        root.setPrefWidth(width);
        popup.show(owner, window.getX() + (window.getWidth() - width) / 2, window.getY() + window.getHeight() * 0.12);
        searchField.requestFocus();
    }

    private void load(Project project) {
        Thread.ofVirtual().name("railroad-go-to").start(() -> {
            try {
                List<T> collected = List.copyOf(contributor.collect(project, this::checkActive));
                Platform.runLater(() -> {
                    if (closed)
                        return;

                    items = collected;
                    placeholder.setText(L18n.localize("railroad.ide.go_to.no_matches"));
                    refresh();
                    loaded.complete(null);
                });
            } catch (CancellationException exception) {
                loaded.cancel(false);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to collect go-to items", exception);
                Platform.runLater(() -> placeholder.setText(L18n.localize("railroad.ide.go_to.failed")));
                loaded.completeExceptionally(exception);
            }
        });
    }

    private void checkActive() {
        if (closed)
            throw new CancellationException("Go-to popup closed");
    }

    private void refresh() {
        var query = GoToSearch.Query.parse(searchField.getText());
        list.getItems().setAll(GoToSearch.rank(contributor, items, query.pattern(), MAX_RESULTS));
        if (!list.getItems().isEmpty()) {
            list.getSelectionModel().selectFirst();
            list.scrollTo(0);
        }
    }

    private void handleKey(KeyEvent event) {
        int size = list.getItems().size();
        int selected = list.getSelectionModel().getSelectedIndex();
        int target = switch (event.getCode()) {
            case DOWN -> selected + 1;
            case UP -> selected - 1;
            case PAGE_DOWN -> selected + VISIBLE_ROWS;
            case PAGE_UP -> selected - VISIBLE_ROWS;
            default -> Integer.MIN_VALUE;
        };

        if (target != Integer.MIN_VALUE) {
            if (size > 0) {
                int index = Math.clamp(target, 0, size - 1);
                list.getSelectionModel().select(index);
                list.scrollTo(Math.max(0, index - VISIBLE_ROWS / 2));
            }

            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            chooseSelected();
            event.consume();
        }
    }

    private void chooseSelected() {
        GoToSearch.Result<T> result = list.getSelectionModel().getSelectedItem();
        if (result == null)
            return;

        var query = GoToSearch.Query.parse(searchField.getText());
        hide();
        contributor.open(result.item(), query);
    }

    /**
     * Hides the popup and stops collecting items.
     */
    public void hide() {
        popup.hide();
        close();
    }

    private void close() {
        closed = true;
        if (showing == this) {
            showing = null;
        }
    }

    /**
     * Returns whether the popup is shown.
     *
     * @return whether it is showing
     */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /**
     * Returns the search field.
     *
     * @return query field
     */
    public RRTextField searchField() {
        return searchField;
    }

    /**
     * Returns the listed results, best first.
     *
     * @return live result list
     */
    public ObservableList<GoToSearch.Result<T>> results() {
        return list.getItems();
    }

    /**
     * Returns a future completed on the JavaFX thread once the items have been collected and listed.
     *
     * @return load completion
     */
    public CompletableFuture<Void> loaded() {
        return loaded;
    }

    private static List<Text> highlighted(String text, NameMatcher.@Nullable Match match) {
        if (match == null || match.positions().length == 0)
            return List.of(segment(text, false));

        List<Text> segments = new ArrayList<Text>();
        int start = 0;
        boolean matched = false;
        int next = 0;
        int[] positions = match.positions();
        for (int index = 0; index <= text.length(); index++) {
            boolean current = next < positions.length && positions[next] == index;
            if (index == text.length() || current != matched) {
                if (index > start) {
                    segments.add(segment(text.substring(start, index), matched));
                }

                start = index;
                matched = current;
            }

            if (current) {
                next++;
            }
        }

        return segments;
    }

    private static Text segment(String text, boolean matched) {
        var segment = new Text(text);
        segment.getStyleClass().add(matched ? "go-to-match" : "go-to-text");
        return segment;
    }

    private final class ResultCell extends ListCell<GoToSearch.Result<T>> {
        private final StackPane iconBox = new StackPane();
        private final TextFlow name = new TextFlow();
        private final Label detail = new Label();
        private final HBox row = new HBox(iconBox, name, detail);

        private ResultCell() {
            setPrefWidth(0);
            iconBox.getStyleClass().add("go-to-icon");
            name.getStyleClass().add("go-to-name");
            name.setMinWidth(Region.USE_PREF_SIZE);
            detail.getStyleClass().add("go-to-detail");
            detail.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            detail.setMinWidth(0);
            detail.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(detail, Priority.ALWAYS);
            row.getStyleClass().add("go-to-row");
            row.setAlignment(Pos.CENTER_LEFT);
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !isEmpty()) {
                    getListView().getSelectionModel().select(getIndex());
                    chooseSelected();
                }
            });
        }

        @Override
        protected void updateItem(GoToSearch.Result<T> result, boolean empty) {
            super.updateItem(result, empty);
            if (empty || result == null) {
                setGraphic(null);
                setAccessibleText(null);
                return;
            }

            iconBox.getChildren().setAll(contributor.icon(result.item()));
            name.getChildren().setAll(highlighted(result.name(), result.nameMatch()));
            String location = contributor.detail(result.item());
            detail.setText(location);
            setAccessibleText(location.isEmpty() ? result.name() : result.name() + " " + location);
            setGraphic(row);
        }
    }
}
