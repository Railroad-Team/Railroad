package dev.railroadide.railroad.ide.images;

import javafx.scene.paint.Color;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Palette {
    private List<Color> colors;
    public Palette(List<Color> colors) {
        this.colors = colors;
    }

    public List<Color> getColors() {
        return colors;
    }
    /*
        public static Pallete createFromColors(Color a,Color b) {
            TODO
        }
    */

    /**
     *  Creates A Palette From An Inputted .pal File
     * @param filePath The path of the .pal File
     */
    public static Palette createFromFile(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        lines.subList(0,3).clear(); // Remove First 3 Lines

        List<Color> colors = new ArrayList<>();
        for (String line : lines) {
            //Get RGB
            double r = Double.parseDouble(line.split(" ")[0])/255;
            double g = Double.parseDouble(line.split(" ")[1])/255;
            double b = Double.parseDouble(line.split(" ")[2])/255;
            double a = Double.parseDouble(line.split(" ")[3])/255;
            colors.add(new Color(r,g,b,a));
        }
        return new Palette(colors);

    }
}
