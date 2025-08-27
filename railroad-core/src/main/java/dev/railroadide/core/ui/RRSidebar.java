package dev.railroadide.core.ui;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import lombok.Getter;

/**
 * A modern sidebar component with enhanced styling and smooth transitions.
 * Perfect for navigation menus and side panels.
 */
public class RRSidebar extends VBox {
    private SidebarPosition position = SidebarPosition.LEFT;
    private SidebarSize size = SidebarSize.MEDIUM;
    @Getter
    private boolean isCollapsed = false;
    @Getter
    private VBox header;
    @Getter
    private VBox content;
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
     * @param position the position of the sidebar (LEFT or RIGHT)
     */
    public RRSidebar(SidebarPosition position) {
        this.position = position;
        initialize();
    }

    private void initialize() {
        getStyleClass().addAll("rr-sidebar", "elevated-2");
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(16));
        setSpacing(16);

        header = new VBox();
        header.setAlignment(Pos.TOP_CENTER);
        header.setSpacing(8);
        header.getStyleClass().add("sidebar-header");

        content = new VBox();
        content.setAlignment(Pos.TOP_CENTER);
        content.setSpacing(8);
        content.getStyleClass().add("sidebar-content");

        footer = new VBox();
        footer.setAlignment(Pos.BOTTOM_CENTER);
        footer.setSpacing(8);
        footer.getStyleClass().add("sidebar-footer");

        getChildren().addAll(header, content, footer);
        VBox.setVgrow(content, Priority.ALWAYS);

        updateStyle();
    }

    /**
     * Set the sidebar position
     */
    public void setPosition(SidebarPosition position) {
        this.position = position;
        updateStyle();
    }

    /**
     * Set the sidebar size
     */
    public void setSidebarSize(SidebarSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Set the sidebar as collapsed
     */
    public void setCollapsed(boolean collapsed) {
        if (this.isCollapsed != collapsed) {
            this.isCollapsed = collapsed;
            animateCollapse();
        }
    }

    /**
     * Toggle collapse state
     */
    public void toggleCollapse() {
        setCollapsed(!isCollapsed);
    }

    /**
     * Add content to the header
     */
    public void addHeaderContent(Node... nodes) {
        header.getChildren().addAll(nodes);
    }

    /**
     * Add content to the main content area
     */
    public void addContent(Node... nodes) {
        content.getChildren().addAll(nodes);
    }

    /**
     * Add content to the footer
     */
    public void addFooterContent(Node... nodes) {
        footer.getChildren().addAll(nodes);
    }

    /**
     * Clear header content
     */
    public void clearHeader() {
        header.getChildren().clear();
    }

    /**
     * Clear main content
     */
    public void clearContent() {
        content.getChildren().clear();
    }

    /**
     * Clear footer content
     */
    public void clearFooter() {
        footer.getChildren().clear();
    }

    /**
     * Set the sidebar as floating
     */
    public void setFloating(boolean floating) {
        if (floating) {
            getStyleClass().add("floating");
        } else {
            getStyleClass().remove("floating");
        }
    }

    /**
     * Set the sidebar as transparent
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

    public enum SidebarPosition {
        LEFT, RIGHT;
    }

    public enum SidebarSize {
        SMALL, MEDIUM, LARGE;
    }
}