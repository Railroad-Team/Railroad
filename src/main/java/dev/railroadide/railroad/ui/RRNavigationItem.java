package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern navigation item component for use in sidebars and navigation menus.
 * Supports icons, labels, badges, and smooth hover animations.
 */
public class RRNavigationItem extends VBox {
    private NavigationItemSize size = NavigationItemSize.MEDIUM;
    private NavigationItemState state = NavigationItemState.NORMAL;

    /**
     * The current icon node, absent when no icon is configured.
     *
     * @return the icon node, or {@code null}
     */
    @Getter
    private FontIcon icon;
    /**
     * The label displaying the navigation item's text.
     *
     * @return the mutable text label
     */
    @Getter
    private LocalizedLabel label;
    /**
     * The badge label, which remains available while hidden.
     *
     * @return the mutable badge label
     */
    @Getter
    private LocalizedLabel badge;
    private HBox content;
    /**
     * Whether the item has been marked selected.
     *
     * @return {@code true} when the selected flag is set
     */
    @Getter
    private boolean isSelected = false;

    /**
     * Constructs a new navigation item with default styling and no text.
     */
    public RRNavigationItem() {
        this("");
    }

    /**
     * Constructs a new navigation item using the supplied text as a localization key.
     *
     * @param text localization key for the navigation label
     */
    public RRNavigationItem(String text) {
        this(text, new Object[0]);
    }

    /**
     * Constructs a new navigation item with localized text and an icon.
     *
     * @param localizationKey the localization key for the text
     * @param iconCode the icon to display, or {@code null} for no icon
     * @param args optional formatting arguments for the localized text
     */
    public RRNavigationItem(String localizationKey, Ikon iconCode, Object... args) {
        initialize();
        setLocalizedText(localizationKey, args);
        setIcon(iconCode);
    }

    /**
     * Constructs a new navigation item with localized text.
     *
     * @param localizationKey the localization key for the text
     * @param args optional formatting arguments for the localized text
     */
    public RRNavigationItem(String localizationKey, Object... args) {
        this(localizationKey, null, args);
    }

    /**
     * Creates a navigation item with a localized label and an optional icon.
     *
     * @param text localization key for the label
     * @param icon the icon to display, or {@code null} for no icon
     * @return a new RRNavigationItem instance
     */
    public static RRNavigationItem create(String text, Ikon icon) {
        return new RRNavigationItem(text, icon);
    }

    /**
     * Creates a navigation item with a localized label and no icon.
     *
     * @param text localization key for the label
     * @return a new RRNavigationItem instance
     */
    public static RRNavigationItem create(String text) {
        return new RRNavigationItem(text);
    }

    private void initialize() {
        getStyleClass().addAll("rr-navigation-item", "interactive");
        setAlignment(Pos.CENTER_LEFT);

        content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("rr-navigation-item-content");

        label = new LocalizedLabel("");
        label.getStyleClass().add("navigation-label");

        badge = new LocalizedLabel("");
        badge.getStyleClass().add("navigation-badge");
        badge.setVisible(false);

        content.getChildren().addAll(label, badge);
        HBox.setHgrow(label, Priority.ALWAYS);

        getChildren().add(content);

        setOnMouseEntered(_ -> {
            if (state != NavigationItemState.DISABLED) {
                var scale = new ScaleTransition(Duration.millis(150), this);
                scale.setToX(1.02);
                scale.setToY(1.02);
                scale.play();

                var fade = new FadeTransition(Duration.millis(150), this);
                fade.setToValue(0.9);
                fade.play();
            }
        });

        setOnMouseExited(_ -> {
            if (state != NavigationItemState.DISABLED) {
                var scale = new ScaleTransition(Duration.millis(150), this);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.play();

                var fade = new FadeTransition(Duration.millis(150), this);
                fade.setToValue(1.0);
                fade.play();
            }
        });

        updateStyle();
    }

    /**
     * Updates the navigation label using a localization key.
     *
     * @param localizationKey key used to translate the label
     * @param args formatting arguments for the translated label
     * @throws NullPointerException if the argument array is {@code null}
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        label.setKey(localizationKey, args);
    }

    /**
     * Sets the navigation label's text directly.
     *
     * @param text text to display
     */
    public void setText(String text) {
        label.setText(text);
    }

    /**
     * Updates the navigation item's size style.
     *
     * @param size size variant to apply
     * @throws NullPointerException if {@code size} is {@code null}
     */
    public void setNavigationItemSize(NavigationItemSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Updates the navigation item's state style and the state used to gate hover animations.
     * The disabled variant does not change the JavaFX {@code disable} property.
     *
     * @param state state variant to apply
     * @throws NullPointerException if {@code state} is {@code null}
     */
    public void setNavigationItemState(NavigationItemState state) {
        this.state = state;
        updateStyle();
    }

    /**
     * Updates the badge using a localization key and makes the badge visible.
     *
     * @param localizationKey key used to translate the badge
     * @param args formatting arguments for the translated badge
     * @throws NullPointerException if the argument array is {@code null}
     */
    public void setLocalizedBadge(String localizationKey, Object... args) {
        badge.setKey(localizationKey, args);
        badge.setVisible(true);
    }

    /**
     * Toggles the rounded CSS style.
     *
     * @param rounded whether to apply rounded styling
     */
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Toggles the outlined CSS style.
     *
     * @param outlined whether to apply outlined styling
     */
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    /**
     * Toggles the compact CSS style.
     *
     * @param compact whether to apply compact styling
     */
    public void setCompact(boolean compact) {
        if (compact) {
            getStyleClass().add("compact");
        } else {
            getStyleClass().remove("compact");
        }
    }

    private void updateStyle() {
        getStyleClass().removeAll("small", "medium", "large");
        getStyleClass().removeAll("normal", "active", "disabled", "highlighted");

        switch (size) {
            case SMALL -> getStyleClass().add("small");
            case MEDIUM -> getStyleClass().add("medium");
            case LARGE -> getStyleClass().add("large");
        }

        switch (state) {
            case NORMAL -> getStyleClass().add("normal");
            case ACTIVE -> getStyleClass().add("active");
            case DISABLED -> getStyleClass().add("disabled");
            case HIGHLIGHTED -> getStyleClass().add("highlighted");
        }
    }

    /**
     * Replaces the icon preceding the label, or removes it when no icon code is supplied.
     * New icons use an initial size of 18 pixels.
     *
     * @param iconCode icon to display, or {@code null} to remove the current icon
     */
    public void setIcon(Ikon iconCode) {
        if (icon != null) {
            content.getChildren().remove(icon);
        }

        if (iconCode != null) {
            icon = new FontIcon(iconCode);
            icon.setIconSize(18);
            icon.getStyleClass().add("navigation-icon");
            content.getChildren().addFirst(icon);
        } else if (icon != null) {
            content.getChildren().remove(icon);
            icon = null;
        }
    }

    /**
     * Set a badge on the navigation item using plain text.
     * The badge will be displayed next to the navigation item text.
     *
     * @param badgeText the text to display in the badge, or null/empty to hide the badge
     */
    public void setBadge(String badgeText) {
        if (badgeText != null && !badgeText.isEmpty()) {
            badge.setText(badgeText);
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
    }

    /**
     * Updates the selection flag and selected CSS style independently of the state variant.
     *
     * @param selected whether the item is selected
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        if (selected) {
            getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
    }

    /**
     * CSS size variants for a navigation item.
     */
    public enum NavigationItemSize {
        /** The small size variant. */
        SMALL,
        /** The default medium size variant. */
        MEDIUM,
        /** The large size variant. */
        LARGE
    }

    /**
     * Visual state variants; the disabled variant also suppresses hover animations.
     */
    public enum NavigationItemState {
        /** The default, unaccented state. */
        NORMAL,
        /** The active-item style. */
        ACTIVE,
        /** The disabled style, with hover animations suppressed. */
        DISABLED,
        /** The highlighted-item style. */
        HIGHLIGHTED
    }
}
