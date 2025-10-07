package dev.railroadide.railroad.ide.features.gui_generation;

import dev.railroadide.core.ui.RRStackPane;
import dev.railroadide.core.ui.collage.CollageCanvas;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Scale;

import java.util.concurrent.atomic.AtomicReference;

public class GuiCollage extends RRStackPane {
    private double scale = 1.0;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    private final CollageCanvas<GuiCollageElement> canvas;
    private final Canvas fxCanvas = new Canvas(256, 256);
    private final Group group = new Group(fxCanvas);
    private final Scale scaleTransform = new Scale(scale, scale, 0, 0);
    private final AtomicReference<GuiCollageElement> dragging = new AtomicReference<>();

    private void onScroll(ScrollEvent event) {
        //TODO fix zooming. Should also be hooked into the entire window, not just the canvas.
        if (event.isControlDown()) {
            double zoomFactor = 1.05;
            if (event.getDeltaY() < 0) {
                zoomFactor = 1 / zoomFactor;
            }
            double oldScale = scale;
            scale *= zoomFactor;

            double f = (scale / oldScale) - 1;

            double dx = event.getX() - group.getBoundsInParent().getMinX();
            double dy = event.getY() - group.getBoundsInParent().getMinY();

            group.setTranslateX(group.getTranslateX() - f * dx);
            group.setTranslateY(group.getTranslateY() - f * dy);

            scaleTransform.setX(scale);
            scaleTransform.setY(scale);
        }
    }

    private void onMouseClicked(MouseEvent event) {
        // TODO quick edit menu, such as delete, open texture folder.
        if (event.getButton() == MouseButton.SECONDARY) return;
        if (dragging.get() != null) {
            dragging.set(null);
        }
        var clicked = canvas.getElementFromPosition((int) event.getX(), (int) event.getY());
        clicked.ifPresent(this.canvas::setSelectedElement);
    }

    private void onMousePressed(MouseEvent event) {
        if (event.isSecondaryButtonDown()) {
            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        } else {
            var clicked = canvas.getElementFromPosition((int) event.getX(), (int) event.getY());
            dragging.set(clicked.orElse(null));
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (event.isSecondaryButtonDown()) {
            //TODO also handle this in the entire window, not just the canvas?
            double deltaX = event.getSceneX() - lastMouseX;
            double deltaY = event.getSceneY() - lastMouseY;

            group.setTranslateX(group.getTranslateX() + deltaX);
            group.setTranslateY(group.getTranslateY() + deltaY);

            lastMouseX = event.getSceneX();
            lastMouseY = event.getSceneY();
        } else {
            if (dragging.get() == null) return;
            int offsetX = (int) (event.getX() - dragging.get().getX());
            int offsetY = (int) (event.getY() - dragging.get().getY());
            dragging.get().setPosition((int) event.getX() + offsetX, (int) event.getY() + offsetY);

            canvas.notifyChangeListeners();
        }
    }

    public GuiCollage(Image background) {
        super();

        canvas = new CollageCanvas<>() {
            @Override
            public void render(GraphicsContext gc) {
                fxCanvas.getGraphicsContext2D().clearRect(0, 0, fxCanvas.getWidth(), fxCanvas.getHeight());
                fxCanvas.getGraphicsContext2D().setFill(Paint.valueOf("#2b2b2b")); //TODO get from theme
                fxCanvas.getGraphicsContext2D().fillRect(0, 0, fxCanvas.getWidth(), fxCanvas.getHeight());
                fxCanvas.getGraphicsContext2D().drawImage(background,
                    0, 0,
                    background.getWidth(), background.getHeight());

                super.render(gc);
                for (GuiCollageElement element : this.getElements()) {
                    element.render(fxCanvas.getGraphicsContext2D());
                }
            }
        };

        fxCanvas.getGraphicsContext2D().setImageSmoothing(false);

        this.canvas.addChangeListener(() -> {
            this.canvas.render(this.fxCanvas.getGraphicsContext2D());
        });

        group.getTransforms().add(scaleTransform);
        this.getChildren().add(group);

        this.fxCanvas.setOnScroll(this::onScroll);
        this.fxCanvas.setOnMouseClicked(this::onMouseClicked);
        this.fxCanvas.setOnMousePressed(this::onMousePressed);
        this.fxCanvas.setOnMouseDragged(this::onMouseDragged);

        this.canvas.render(this.fxCanvas.getGraphicsContext2D());
    }
}
