package dev.railroadide.railroad.ide.ui.codeeditor;

import dev.railroadide.railroad.ide.completion.CompletionItem;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.ui.RRListView;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

/** Compact suggestion list and keyboard hints displayed next to the editor caret. */
public final class CompletionPopupContent extends VBox {
    private static final int VISIBLE_ROWS = 8;

    /**
     * Creates the popup around the editor's existing selection and keyboard target.
     *
     * @param list the list owned by the active completion request
     */
    public CompletionPopupContent(RRListView<CompletionItem> list) {
        getStyleClass().add("completion-popup");
        list.setAnimationsEnabled(false);
        list.setCellFactory(_ -> new SuggestionCell());
        list.setFixedCellSize(28);
        var count = Bindings.size(list.getItems());
        list.prefHeightProperty().bind(Bindings.min(VISIBLE_ROWS, count).multiply(list.fixedCellSizeProperty()));
        list.setMinHeight(Region.USE_PREF_SIZE);
        list.setMaxHeight(Region.USE_PREF_SIZE);
        var body = new StackPane(list);
        body.getStyleClass().add("completion-body");

        var results = label(L18n.localize("railroad.ide.completion.suggestions"), "completion-caption");
        var total = label("", "completion-count");
        total.textProperty().bind(count.asString());
        var space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        var footer = new HBox(results, total, space, hint("↑↓", "railroad.ide.completion.select"),
            hint("↵", "railroad.ide.completion.insert"));
        footer.getStyleClass().add("completion-footer");
        getChildren().addAll(body, footer);
    }

    private static HBox hint(String key, String action) {
        var hint = new HBox(label(key, "completion-key"), label(L18n.localize(action), "completion-caption"));
        hint.getStyleClass().add("completion-hint");
        return hint;
    }

    private static Label label(String text, String styleClass) {
        var label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static final class SuggestionCell extends ListCell<CompletionItem> {
        private final FontIcon icon = new FontIcon();
        private final StackPane tile = new StackPane(icon);
        private final Label name = label("", "completion-name");
        private final Label suffix = label("", "completion-suffix");
        private final Label detail = label("", "completion-detail");
        private final HBox signature = new HBox(name, suffix);
        private final HBox row = new HBox(tile, signature, detail);
        private final Tooltip fullSignature = new Tooltip();

        private SuggestionCell() {
            setPrefWidth(0);
            signature.setAlignment(Pos.CENTER_LEFT);
            signature.setMinWidth(0);
            HBox.setHgrow(signature, Priority.ALWAYS);
            suffix.setMinWidth(0);
            detail.setTextOverrun(OverrunStyle.LEADING_ELLIPSIS);
            detail.maxWidthProperty().bind(widthProperty().multiply(0.5));
            row.getStyleClass().add("completion-row");
            row.setPrefWidth(Double.MAX_VALUE);
            row.setMinWidth(0);
        }

        @Override
        protected void updateItem(CompletionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setAccessibleText(null);
                setTooltip(null);
                return;
            }

            icon.setIconCode(switch (item.kind()) {
                case METHOD -> FontAwesomeSolid.CODE;
                case FIELD -> FontAwesomeSolid.TAG;
                case TYPE -> FontAwesomeSolid.CUBE;
                case TEXT -> FontAwesomeSolid.FONT;
            });
            tile.getStyleClass().setAll("completion-symbol",
                "completion-" + item.kind().name().toLowerCase(Locale.ROOT));
            name.setText(item.label());
            suffix.setText(item.suffix());
            detail.setText(item.detail());
            detail.setManaged(!item.detail().isEmpty());
            detail.setVisible(detail.isManaged());
            String description = item.displayText();
            fullSignature.setText(description);
            setAccessibleText(description);
            setTooltip(fullSignature);
            setGraphic(row);
        }
    }
}
