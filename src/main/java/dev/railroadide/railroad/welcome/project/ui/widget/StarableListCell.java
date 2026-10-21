package dev.railroadide.railroad.welcome.project.ui.widget;

import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Text cell that marks recommended items with a full star and other latest items with a half star.
 *
 * @param <T> type of item displayed by the cell
 */
public class StarableListCell<T> extends ListCell<T> {
    private final FontIcon starIcon = new FontIcon(FontAwesomeSolid.STAR);
    private final FontIcon halfStarIcon = new FontIcon(FontAwesomeSolid.STAR_HALF_ALT);

    private final Predicate<T> isRecommended;
    private final Predicate<T> isLatest;
    private final Function<T, String> stringConverter;

    /**
     * Creates a cell with predicates for star decorations and a text conversion function.
     * Callbacks are invoked only for non-null items in nonempty cells.
     *
     * @param isRecommended predicate selecting full-star items; takes precedence over the latest predicate
     * @param isLatest predicate selecting half-star items that are not recommended
     * @param stringConverter function producing the displayed text
     */
    public StarableListCell(Predicate<T> isRecommended, Predicate<T> isLatest, Function<T, String> stringConverter) {
        this.isRecommended = isRecommended;
        this.isLatest = isLatest;
        this.stringConverter = stringConverter;

        this.starIcon.setIconSize(16);
        this.starIcon.setIconColor(Color.GOLD);

        this.halfStarIcon.setIconSize(16);
        this.halfStarIcon.setIconColor(Color.GOLD);
    }

    /**
     * Renders the item text and appropriate star, clearing both for an empty cell.
     *
     * @param item item to display, or null
     * @param empty whether the cell has no item
     */
    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(stringConverter.apply(item));
            setGraphic(isRecommended.test(item) ? starIcon : isLatest.test(item) ? halfStarIcon : null);
        }
    }
}
