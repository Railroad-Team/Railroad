package io.github.railroad.ide;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.MathUtils;
import io.github.railroad.utility.TextPosition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontSmoothingType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

enum Languages {
    JAVA,
    JSON,
    TXT
}
public class CodeEditorPaneUsingCanvas extends RRVBox {
    Canvas canvas;
    public CodeEditorPaneUsingCanvas(Path p) {
        canvas = new Canvas();
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        // Add Canvas to RRVBox
        getChildren().add(canvas);

        // Draw something
        GraphicsContext ctx = canvas.getGraphicsContext2D();
        ctx.setFont(new Font("Consolas",15));
        ctx.setFill(Color.BLACK);
        ctx.setFontSmoothingType(FontSmoothingType.LCD); // Enables subpixel rendering

        // Redraw when size changes
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> redraw(ctx, canvas,p));
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> redraw(ctx, canvas,p));
        setOnMouseClicked(mouseEvent -> {
            CursorEnd = findLine(mouseEvent.getX(), mouseEvent.getY());
            redraw(canvas.getGraphicsContext2D(), canvas, p); // Force redraw to show cursor
        });
    }

    private TextPosition findLine(double mX, double mY) {
        double paddingX = 20;
        double paddingY = 20;
        double charWidth = 7;
        double lineHeight = 15;

        double mXOffset = mX - paddingX;
        double mYOffset = mY - paddingY;

        if (mXOffset < 0) mXOffset = 0;
        if (mYOffset < 0) mYOffset = 0;

        int row = (int)(mYOffset / lineHeight);
        int column = (int)(mXOffset / charWidth);
        return new TextPosition(column, row);



    }


    private TextPosition CursorEnd = new TextPosition(0,0);
    private void redraw(GraphicsContext ctx, Canvas canvas, Path p) {
        ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        String contents = "";

        try {
            contents = Files.readString(p);
        } catch (IOException e) {
            ctx.setFill(Color.RED);
            ctx.fillText("ERROR: Unable To Read File", 20, 20);
            return;
        }

        String[] lines = contents.split("\n");

        CursorEnd.y = (int) MathUtils.clamp(CursorEnd.y, 0, lines.length - 1) +1 ;
        CursorEnd.x = (int) MathUtils.clamp(CursorEnd.x, 0, lines[CursorEnd.y].length());

        for (int y = 0; y < lines.length; y++) {
            String line = lines[y];

            // Draw cursor
            if (y == CursorEnd.y) {
                ctx.setFill(Color.BLUE);
                ctx.fillRect(
                        20 + CursorEnd.x * 7,
                        20 + y * 15 - 12,
                        2,
                        15
                );
                ctx.setFill(Color.BLACK);
            }

            // Draw text if any
            for (int x = 0; x < line.length(); x++) {
                char ch = line.charAt(x);
                ctx.fillText(String.valueOf(ch), 20 + x * 7, 20 + y * 15);
            }
        }
    }


}
