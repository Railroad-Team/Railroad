package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.*;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class Caret {
    private final IntegerProperty line = new SimpleIntegerProperty(0);
    private final IntegerProperty column = new SimpleIntegerProperty(0);

    private final DoubleProperty x = new SimpleDoubleProperty(0);
    private final DoubleProperty y = new SimpleDoubleProperty(0);

    private final ObjectProperty<Node> shape = new SimpleObjectProperty<>();
    private final ObjectProperty<Timeline> animation = new SimpleObjectProperty<>();

    public Caret(int line, int column, DoubleProperty lineHeight, ObjectProperty<Paint> color) {
        this.line.set(line);
        this.column.set(column);

        var rect = new Rectangle(1, lineHeight.get(), color.getValue() != null ? color.getValue() : Color.BLACK);
        rect.heightProperty().bind(lineHeight);
        rect.setManaged(false);
        rect.setMouseTransparent(true);
        bindPositionTo(rect.layoutXProperty(), rect.layoutYProperty());
        this.shape.set(rect);

        var blinkAnimation = new Timeline(
                new KeyFrame(Duration.seconds(0.5), $ -> rect.setVisible(true)),
                new KeyFrame(Duration.seconds(1.0), $ -> rect.setVisible(false))
        );
        blinkAnimation.setCycleCount(Animation.INDEFINITE);
        this.animation.set(blinkAnimation);
    }

    public int getLine() {
        return line.get();
    }

    public void setLine(int line) {
        this.line.set(line);
    }

    public int getColumn() {
        return column.get();
    }

    public void setColumn(int column) {
        this.column.set(column);
    }

    public IntegerProperty lineProperty() {
        return line;
    }

    public IntegerProperty columnProperty() {
        return column;
    }

    public Node getShape() {
        return shape.get();
    }

    public void setShape(Node shape) {
        this.shape.set(shape);
    }

    public ObjectProperty<Node> shapeProperty() {
        return shape;
    }

    public Timeline getAnimation() {
        return animation.get();
    }

    public void setAnimation(Timeline animation) {
        this.animation.set(animation);
    }

    public ObjectProperty<Timeline> animationProperty() {
        return animation;
    }

    public DoubleProperty xProperty() {
        return x;
    }

    public DoubleProperty yProperty() {
        return y;
    }

    public double getX() {
        return x.get();
    }

    public void setX(double x) {
        this.x.set(x);
    }

    public double getY() {
        return y.get();
    }

    public void setY(double y) {
        this.y.set(y);
    }

    public void setPosition(double x, double y) {
        this.x.set(x);
        this.y.set(y);
    }

    public void bindPositionTo(DoubleProperty x, DoubleProperty y) {
        x.bind(this.x);
        y.bind(this.y);
    }

    public void stopAnimation() {
        Timeline animation = this.animation.get();
        if (animation != null) {
            animation.stop();
        }

        Node shape = this.shape.get();
        if (shape != null) {
            shape.setVisible(false);
        }
    }

    public void startAnimation() {
        Node shape = this.shape.get();
        if (shape != null) {
            shape.setVisible(true);
        }

        Timeline animation = this.animation.get();
        if (animation != null) {
            animation.playFromStart();
        }
    }
}
