package dev.railroadide.core.ui.collage;

import dev.railroadide.core.ui.RRVBox;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public class GuiCollage extends RRVBox {
    private final CollageCanvas<GuiCollageElement> canvas;
    private final Canvas fxCanvas = new Canvas(800, 600);

    public GuiCollage() {
        //TODO handle scaling, as the item slots are 16px and should really *look* bigger,
        // it should really be a visual/zoomable canvas, with a default zoom of 2/2.5/3
        super();

        canvas = new CollageCanvas<>() {
            @Override
            void render(GraphicsContext gc) {// TODO add clearRect to super
                fxCanvas.getGraphicsContext2D().clearRect(0, 0, fxCanvas.getWidth(), fxCanvas.getHeight());
                super.render(gc);
                for (GuiCollageElement element : this.getElements()) {
                    element.render(fxCanvas.getGraphicsContext2D());
                }
            }
        };

        this.getChildren().add(fxCanvas);

        this.fxCanvas.setOnMouseClicked(event -> {
            var clicked = canvas.getClickedElement((int) event.getX(), (int) event.getY());
            if (clicked.isEmpty()) {
                this.canvas.addElement(new GuiCollageElement((int) event.getX(), (int) event.getY(), 16, 16));
            } else {
                this.canvas.setSelectedElement(clicked.get());
            }

            this.canvas.render(this.fxCanvas.getGraphicsContext2D());
        });

        this.fxCanvas.setOnMouseDragged(event -> {
            canvas.getSelectedElement().ifPresent(element -> {
                //TODO use deltas to persist mouse position relative to element position
                //TODO don't require an element to be selected to move it
                element.setPosition((int) event.getX(), (int) event.getY());
                this.canvas.render(this.fxCanvas.getGraphicsContext2D());
            });
        });

        this.canvas.addElement(new GuiCollageElement(0, 0, 16, 16));
        //TODO Create an event for when the canvas is changed, then render again.
        this.canvas.render(this.fxCanvas.getGraphicsContext2D());
    }
}
