package dev.railroadide.railroad.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;

/**
 * A styled sidebar with header, growing content, and footer containers, plus collapse transitions.
 */
public class RRSidebar extends VBox {
    private SidebarPosition position = SidebarPosition.LEFT;
    private SidebarSize size = SidebarSize.MEDIUM;
    /**
     * The requested collapse state, updated before any transition completes.
     *
     * @return whether the sidebar is marked collapsed
     */
    @Getter
    private boolean isCollapsed = false;
    /**
     * The container above the main sidebar content.
     *
     * @return the mutable header container
     */
    @Getter
    private VBox header;
    /**
     * The central container that receives remaining vertical space.
     *
     * @return the mutable main content container
     */
    @Getter
    private VBox content;
    /**
     * The container below the main sidebar content.
     *
     * @return the mutable footer container
     */
    @Getter
    private VBox footer;

    /**
     * Constructs a new sidebar with default left positioning and medium size.
     */
    public RRSidebar() {
        initialize();
    }

    /**
     * Constructs a new sidebar with the specified position.
     *
     * @param position the position style and collapse direction of the sidebar
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public RRSidebar(SidebarPosition position) {
        this.position = position;
        initialize();
    }

    private void initialize() {
        getStyleClass().addAll("rr-sidebar", "elevated-2");
        setAlignment(Pos.TOP_CENTER);

        header = new VBox();
        header.setAlignment(Pos.TOP_CENTER);
        header.getStyleClass().add("sidebar-header");

        content = new VBox();
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("sidebar-content");

        footer = new VBox();
        footer.setAlignment(Pos.BOTTOM_CENTER);
        footer.getStyleClass().add("sidebar-footer");

        getChildren().addAll(header, content, footer);
        VBox.setVgrow(content, Priority.ALWAYS);

        updateStyle();
    }

    /**
     * Updates the position style and direction used by subsequent collapse transitions.
     * This does not place the sidebar in its parent's layout.
     *
     * @param position position variant to apply
     * @throws NullPointerException if {@code position} is {@code null}
     */
    public void setPosition(SidebarPosition position) {
        this.position = position;
        updateStyle();
    }

    /**
     * Updates the sidebar's CSS size variant.
     *
     * @param size size variant to apply
     * @throws NullPointerException if {@code size} is {@code null}
     */
    public void setSidebarSize(SidebarSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Updates the collapse flag and starts translation and opacity transitions when it changes.
     *
     * @param collapsed whether to mark the sidebar collapsed
     */
    public void setCollapsed(boolean collapsed) {
        if (this.isCollapsed != collapsed) {
            this.isCollapsed = collapsed;
            animateCollapse();
        }
    }

    /**
     * Reverses the collapse flag and starts the corresponding transitions.
     */
    public void toggleCollapse() {
        setCollapsed(!isCollapsed);
    }

    /**
     * Appends nodes to the header container.
     *
     * @param nodes nodes to append in layout order
     */
    public void addHeaderContent(Node... nodes) {
        header.getChildren().addAll(nodes);
    }

    /**
     * Appends nodes to the main content container.
     *
     * @param nodes nodes to append in layout order
     */
    public void addContent(Node... nodes) {
        content.getChildren().addAll(nodes);
    }

    /**
     * Appends nodes to the footer container.
     *
     * @param nodes nodes to append in layout order
     */
    public void addFooterContent(Node... nodes) {
        footer.getChildren().addAll(nodes);
    }

    /**
     * Removes every node from the header container.
     */
    public void clearHeader() {
        header.getChildren().clear();
    }

    /**
     * Removes every node from the main content container.
     */
    public void clearContent() {
        content.getChildren().clear();
    }

    /**
     * Removes every node from the footer container.
     */
    public void clearFooter() {
        footer.getChildren().clear();
    }

    /**
     * Toggles the floating CSS style.
     *
     * @param floating whether to apply floating styling
     */
    public void setFloating(boolean floating) {
        if (floating) {
            getStyleClass().add("floating");
        } else {
            getStyleClass().remove("floating");
        }
    }

    /**
     * Toggles the transparent CSS style.
     *
     * @param transparent whether to apply transparent styling
     */
    public void setTransparent(boolean transparent) {
        if (transparent) {
            getStyleClass().add("transparent");
        } else {
            getStyleClass().remove("transparent");
        }
    }

    private void animateCollapse() {
        var translate = new TranslateTransition(Duration.millis(300), this);
        if (isCollapsed) {
            translate.setByX(position == SidebarPosition.LEFT ? -getWidth() : getWidth());
            translate.play();

            var fade = new FadeTransition(Duration.millis(300), this);
            fade.setToValue(0.8);
            fade.play();
        } else {
            translate.setByX(0);
            translate.play();

            var fade = new FadeTransition(Duration.millis(300), this);
            fade.setToValue(1.0);
            fade.play();
        }
    }

    private void updateStyle() {
        getStyleClass().removeAll("left", "right", "small", "medium", "large");

        switch (position) {
            case LEFT -> getStyleClass().add("left");
            case RIGHT -> getStyleClass().add("right");
        }

        switch (size) {
            case SMALL -> getStyleClass().add("small");
            case MEDIUM -> getStyleClass().add("medium");
            case LARGE -> getStyleClass().add("large");
        }
    }

    /**
     * Sidebar position variants that determine styling and collapse direction.
     */
    public enum SidebarPosition {
        /** Left-side styling and a leftward collapse translation. */
        LEFT,
        /** Right-side styling and a rightward collapse translation. */
        RIGHT;
    }

    /**
     * CSS size variants for a sidebar.
     */
    public enum SidebarSize {
        /** The small size variant. */
        SMALL,
        /** The default medium size variant. */
        MEDIUM,
        /** The large size variant. */
        LARGE;
    }
}
