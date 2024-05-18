package io.github.railroad.ui.defaults;


import io.github.railroad.Railroad;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

public class RRIcon extends Label {

    // There's some weird problem with changing the font size,
    // where some glyphs are not displayed. I recommend don't
    // change i from code, just from css.

    private static Font getFontawesomeFont(int size) {
        return Font.loadFont(
                Railroad.getResource("fonts/FontAwesome6-Solid.otf").toExternalForm(), size);
    }

    public RRIcon() {
        super();
        setFont(getFontawesomeFont(20));
        setTextAlignment(TextAlignment.CENTER);
        getStyleClass().addAll("Railroad", "Text", "Icon", "contrast-2");
    }

    public RRIcon(FontAwesomeSolid icon) {
        this();
        setIcon(icon);
    }

    public void setIcon(FontAwesomeSolid icon)
    {
        setText("" + (char)icon.getCode());
    }

    public void  setIconSize(int size) {
        setFont(getFontawesomeFont(size-4));
        setPrefSize(size, size);
    }
}
