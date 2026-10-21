package dev.railroadide.railroad.gradle.ui.tree;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;

/**
 * Supplies the display name, icon, styling, and optional interactions for a node in a Gradle tree.
 */
@Getter
public abstract class GradleTreeElement {
    private final String name;

    /**
     * Creates an element with the supplied display name.
     *
     * @param name the name displayed in the tree cell
     */
    public GradleTreeElement(String name) {
        this.name = name;
    }

    /**
     * Returns the icon displayed beside this element's name.
     *
     * @return the element's icon code
     */
    public abstract Ikon getIcon();

    /**
     * Returns the CSS class applied to this element's icon.
     *
     * @return the icon's CSS style class
     */
    public abstract String getStyleClass();

    /**
     * Returns the tooltip for this element. The default implementation supplies no tooltip.
     *
     * @return the element's tooltip, or {@code null} if none is available
     */
    public Tooltip getTooltip() {
        return null;
    }

    /**
     * Returns the context menu for this element. The default implementation supplies no menu.
     *
     * @return the element's context menu, or {@code null} if none is available
     */
    public ContextMenu getContextMenu() {
        return null;
    }

    @Override
    public String toString() {
        return name;
    }
}
