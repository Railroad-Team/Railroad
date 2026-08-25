package dev.railroadide.railroad.ui.animation;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

import java.util.Objects;

/**
 * JavaFX-native replacements for animations that cannot be expressed in JavaFX CSS.
 */
public final class UIAnimations {
    private static final Duration SPINNER_DURATION = Duration.seconds(1);
    private static final Duration ENTRANCE_DURATION = Duration.millis(300);

    private UIAnimations() {
    }

    public static RotateTransition spinner(Node node) {
        var transition = new RotateTransition(SPINNER_DURATION, Objects.requireNonNull(node));
        transition.setByAngle(360);
        transition.setCycleCount(Animation.INDEFINITE);
        transition.setInterpolator(Interpolator.LINEAR);
        return transition;
    }

    public static FadeTransition fadeIn(Node node) {
        var transition = new FadeTransition(ENTRANCE_DURATION, Objects.requireNonNull(node));
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        return transition;
    }

    public static TranslateTransition slideInFromLeft(Node node) {
        return slideIn(node, -1);
    }

    public static TranslateTransition slideInFromRight(Node node) {
        return slideIn(node, 1);
    }

    private static TranslateTransition slideIn(Node node, double direction) {
        Node target = Objects.requireNonNull(node);
        var transition = new TranslateTransition(ENTRANCE_DURATION, target);
        transition.setFromX(direction * target.getBoundsInLocal().getWidth());
        transition.setToX(0);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        return transition;
    }
}
