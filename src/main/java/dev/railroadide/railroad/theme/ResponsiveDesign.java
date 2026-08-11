package dev.railroadide.railroad.theme;

import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Applies responsive style classes based on the scene width.
 */
final class ResponsiveDesign {
    static final double COMPACT_BREAKPOINT = 768;
    static final String COMPACT_STYLE_CLASS = "compact-layout";

    private static final Set<Scene> INSTALLED_SCENES =
        Collections.newSetFromMap(new WeakHashMap<>());

    private ResponsiveDesign() {
    }

    static void install(Scene scene) {
        if (scene == null || !INSTALLED_SCENES.add(scene))
            return;

        scene.widthProperty().addListener((observable, oldWidth, newWidth) -> update(scene));
        scene.rootProperty().addListener((observable, oldRoot, newRoot) -> {
            removeCompactStyle(oldRoot);
            update(scene);
        });
        update(scene);
    }

    private static void update(Scene scene) {
        Parent root = scene.getRoot();
        if (root == null)
            return;

        boolean compact = scene.getWidth() <= COMPACT_BREAKPOINT;
        if (compact) {
            if (!root.getStyleClass().contains(COMPACT_STYLE_CLASS))
                root.getStyleClass().add(COMPACT_STYLE_CLASS);
        } else {
            removeCompactStyle(root);
        }
    }

    private static void removeCompactStyle(Parent root) {
        if (root != null)
            root.getStyleClass().remove(COMPACT_STYLE_CLASS);
    }
}
