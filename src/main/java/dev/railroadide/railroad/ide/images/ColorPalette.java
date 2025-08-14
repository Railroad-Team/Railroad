package dev.railroadide.railroad.ide.images;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.Railroad;

import dev.railroadide.railroad.ide.IDESetup;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ColorPalette extends RRVBox {
    private final FlowPane swatchPane;
    public static final int SWATCH_SIZE = 20;
    private Color selectedColor;
    private Palette palette;
    private final CheckBox edit;
    private final RRButton openFile = new RRButton("railroad.ide.image_editor.open_file");
    private final Text rightClickToRemove = new Text("Right Click To Remove A Color");
    public ColorPalette(Palette palette) {
        this.palette = palette;

        // Initialize selected color to first palette color (if available)
        selectedColor = palette.getColors().isEmpty() ? Color.BLACK : palette.getColors().getFirst();
        this.edit = new CheckBox("Edit");


        openFile.setOnAction(actionEvent -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Pallete Files","*.pal")
            );
            File chosen = fileChooser.showOpenDialog(IDESetup.getEditorPane().getScene().getWindow());
            if(chosen != null) {
                Path path = Path.of(chosen.getAbsolutePath());

                try {
                    this.palette = Palette.createFromFile(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                renderPalette();
            }



        });
        // Localized label
        LocalizedText text = new LocalizedText("railroad.ide.image_editor.color_palette");
        // Container styling
        setBackground(new Background(new BackgroundFill(
                Color.web("#f5f5f5"),
                CornerRadii.EMPTY,
                Insets.EMPTY
        )));
        setBorder(new Border(new BorderStroke(
                Color.web("#5E6675"), // subtle gray border
                BorderStrokeStyle.SOLID,
                CornerRadii.EMPTY,
                new BorderWidths(1)
        )));
        setSpacing(8);
        setPadding(new Insets(10));
        setMaxWidth(200);

        // Swatch container
        swatchPane = new FlowPane();
        swatchPane.setHgap(6);
        swatchPane.setVgap(6);
        swatchPane.setPrefWrapLength(160); // wrap to next line after ~160px
        swatchPane.setBackground(Background.EMPTY);
        edit.setOnAction(actionEvent -> renderPalette());
        renderPalette();

        getChildren().addAll(openFile,text, swatchPane,edit);

    }

    private void renderPalette() {
        swatchPane.getChildren().clear();
        if(edit.isSelected()) {
            getChildren().add(rightClickToRemove);
        }else {
            getChildren().remove(rightClickToRemove);
        }
        for (Color color : palette.getColors()) {
            StackPane swatchContainer = new StackPane();
            Rectangle swatch = createSwatch(color);
            swatchContainer.getChildren().add(swatch);

            swatchPane.getChildren().add(swatchContainer);
        }
    }


    private @NotNull Rectangle createSwatch(Color color) {
        Rectangle swatch = new Rectangle(SWATCH_SIZE, SWATCH_SIZE, color);
        swatch.setArcWidth(6);
        swatch.setArcHeight(6);

        // Hover effect
        swatch.setOnMouseEntered(e -> {
            swatch.setScaleX(1.15);
            swatch.setScaleY(1.15);
        });
        swatch.setOnMouseExited(e -> {
            swatch.setScaleX(1.0);
            swatch.setScaleY(1.0);
        });

        // Click selection
        swatch.setOnMouseClicked(e -> {
            if(!edit.isSelected()) {
                selectedColor = color;
                fireEvent(new ActionEvent());
            }else if(e.getButton() == MouseButton.SECONDARY) {
                palette.getColors().remove(color);
                renderPalette();
            }

        });

        return swatch;
    }

    public Color getSelectedColor() {
        return selectedColor;
    }

    public boolean contains(Color color) {
        return palette.getColors().contains(color);
    }

    public void addToPalette(Color color) {
        if(edit.isSelected()) {
            if (!palette.getColors().contains(color)) {
                palette.getColors().add(color);
                renderPalette();
            }
        }

    }
}
