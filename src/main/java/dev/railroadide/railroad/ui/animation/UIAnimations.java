package dev.railroadide.railroad.ui.animation;

import javafx.animation.*;
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

    /**
     * Creates an unstarted, linear animation that rotates a node through 360 degrees every second indefinitely.
     *
     * @param node node to rotate
     * @return the transition, ready for the caller to start and stop
     * @throws NullPointerException if {@code node} is null
     */
    public static RotateTransition spinner(Node node) {
        var transition = new RotateTransition(SPINNER_DURATION, Objects.requireNonNull(node));
        transition.setByAngle(360);
        transition.setCycleCount(Animation.INDEFINITE);
        transition.setInterpolator(Interpolator.LINEAR);
        return transition;
    }

    /**
     * Creates an unstarted 300-millisecond transition from transparent to fully opaque, with eased endpoints.
     *
     * @param node node whose opacity is animated
     * @return the configured fade transition
     * @throws NullPointerException if {@code node} is null
     */
    public static FadeTransition fadeIn(Node node) {
        var transition = new FadeTransition(ENTRANCE_DURATION, Objects.requireNonNull(node));
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.setInterpolator(Interpolator.EASE_BOTH);
        return transition;
    }

    /**
     * Creates an unstarted 300-millisecond slide from one node width to the left to zero horizontal translation.
     * The distance uses the node's local bounds at the time this method is called.
     *
     * @param node node to translate
     * @return the configured transition with eased endpoints
     * @throws NullPointerException if {@code node} is null
     */
    public static TranslateTransition slideInFromLeft(Node node) {
        return slideIn(node, -1);
    }

    /**
     * Creates an unstarted 300-millisecond slide from one node width to the right to zero horizontal translation.
     * The distance uses the node's local bounds at the time this method is called.
     *
     * @param node node to translate
     * @return the configured transition with eased endpoints
     * @throws NullPointerException if {@code node} is null
     */
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
