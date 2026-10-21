package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.java.JDK;
import javafx.scene.image.Image;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class SvgImageLoadingTest {
    @Test
    public void loadsEveryJdkBrandImageAtIconSize() throws IOException {
        for (JDK.Brand brand : JDK.Brand.values()) {
            if (!brand.isImage())
                continue;

            try (var stream = AppResources.getResourceAsStream(brand.getImagePath())) {
                assertNotNull(stream, brand.name());
                var image = new Image(stream, 20, 20, true, true);
                assertFalse(image.isError(), () -> brand.name() + ": " + image.getException());
                assertTrue(image.getWidth() > 0 && image.getWidth() <= 20, brand.name());
                assertTrue(image.getHeight() > 0 && image.getHeight() <= 20, brand.name());
                assertNotNull(image.getPixelReader(), brand.name());
            }
        }
    }

    @Test
    public void preservesSvgDimensionsColorsAndTransparency() throws IOException {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20" viewBox="0 0 40 20">
                <rect width="20" height="20" fill="#ff0000"/>
            </svg>
            """;
        try (var stream = new ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8))) {
            var image = new Image(stream, 20, 20, true, true);
            assertFalse(image.isError(), () -> String.valueOf(image.getException()));
            assertEquals(20, image.getWidth());
            assertEquals(10, image.getHeight());
            assertEquals(0xffff0000, image.getPixelReader().getArgb(4, 4));
            assertEquals(0, image.getPixelReader().getArgb(15, 4));
        }
    }
}
