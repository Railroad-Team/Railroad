package io.github.railroad.ui.nodes;

import io.github.railroad.localization.ui.LocalizedLabel;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
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

    @Getter
    private FontIcon icon;
    @Getter
    private LocalizedLabel label;
    @Getter
    private LocalizedLabel badge;
    private HBox content;
    @Getter
    private boolean isSelected = false;

    public RRNavigationItem() {
        this("");
    }

    public RRNavigationItem(String text) {
        this(text, new Object[0]);
    }

    public RRNavigationItem(String localizationKey, Ikon iconCode, Object... args) {
        initialize();
        setLocalizedText(localizationKey, args);
        setIcon(iconCode);
    }

    public RRNavigationItem(String localizationKey, Object... args) {
        this(localizationKey, null, args);
    }

    /**
     * Create a navigation item with text and icon
     */
    public static RRNavigationItem create(String text, Ikon icon) {
        return new RRNavigationItem(text, icon);
    }

    /**
     * Create a navigation item with text only
     */
    public static RRNavigationItem create(String text) {
        return new RRNavigationItem(text);
    }

    private void initialize() {
        getStyleClass().addAll("rr-navigation-item", "interactive");
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(8, 12, 8, 12));
        setSpacing(8);

        content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(12);

        label = new LocalizedLabel("");
        label.getStyleClass().add("navigation-label");

        badge = new LocalizedLabel("");
        badge.getStyleClass().add("navigation-badge");
        badge.setVisible(false);

        content.getChildren().addAll(label, badge);
        HBox.setHgrow(label, Priority.ALWAYS);

        getChildren().add(content);

        setOnMouseEntered($ -> {
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

        setOnMouseExited($ -> {
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
     * Set the navigation item text
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        label.setKey(localizationKey, args);
    }

    /**
     * Set the navigation item text
     */
    public void setText(String text) {
        label.setText(text);
    }

    /**
     * Set the navigation item size
     */
    public void setNavigationItemSize(NavigationItemSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Set the navigation item state
     */
    public void setNavigationItemState(NavigationItemState state) {
        this.state = state;
        updateStyle();
    }

    /**
     * Set a badge on the navigation item
     */
    public void setLocalizedBadge(String localizationKey, Object... args) {
        badge.setKey(localizationKey, args);
        badge.setVisible(true);
    }

    /**
     * Set the navigation item as rounded
     */
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Set the navigation item as outlined
     */
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    /**
     * Set the navigation item as compact
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
     * Set the navigation item icon
     */
    public void setIcon(Ikon iconCode) {
        if (icon != null && content.getChildren().contains(icon)) {
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

    public void setBadge(String badgeText) {
        if (badgeText != null && !badgeText.isEmpty()) {
            badge.setText(badgeText);
            badge.setVisible(true);
        } else {
            badge.setVisible(false);
        }
    }

    /**
     * Set the navigation item as selected
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        if (selected) {
            getStyleClass().add("selected");
        } else {
            getStyleClass().remove("selected");
        }
    }

    public enum NavigationItemSize {
        SMALL, MEDIUM, LARGE
    }

    public enum NavigationItemState {
        NORMAL, ACTIVE, DISABLED, HIGHLIGHTED
    }
} 