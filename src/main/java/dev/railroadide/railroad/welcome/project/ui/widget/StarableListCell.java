package dev.railroadide.railroad.welcome.project.ui.widget;

import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Function;
import java.util.function.Predicate;

public class StarableListCell<T> extends ListCell<T> {
    private final FontIcon starIcon = new FontIcon(FontAwesomeSolid.STAR);
    private final FontIcon halfStarIcon = new FontIcon(FontAwesomeSolid.STAR_HALF_ALT);

    private final Predicate<T> isRecommended;
    private final Predicate<T> isLatest;
    private final Function<T, String> stringConverter;

    public StarableListCell(Predicate<T> isRecommended, Predicate<T> isLatest, Function<T, String> stringConverter) {
        this.isRecommended = isRecommended;
        this.isLatest = isLatest;
        this.stringConverter = stringConverter;

        this.starIcon.setIconSize(16);
        this.starIcon.setIconColor(Color.GOLD);

        this.halfStarIcon.setIconSize(16);
        this.halfStarIcon.setIconColor(Color.GOLD);
    }

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