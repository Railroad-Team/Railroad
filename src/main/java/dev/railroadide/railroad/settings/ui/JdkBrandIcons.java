package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.java.JDK;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/** Renders bundled JDK vendor artwork without JavaFX's internal SVG image loader. */
public final class JdkBrandIcons {
    private static final Map<JDK.Brand, Image> CACHE = new EnumMap<>(JDK.Brand.class);

    private JdkBrandIcons() {
    }

    /**
     * Returns cached artwork for a brand, rendering its bundled SVG image on first use.
     *
     * @param brand JDK vendor whose bundled SVG artwork should be loaded
     * @return the rendered vendor image, cached for subsequent calls
     * @throws Exception if the bundled artwork cannot be read or rendered
     */
    public static Image get(JDK.Brand brand) throws Exception {
        Image cached = CACHE.get(brand);
        if (cached != null)
            return cached;

        var transcoder = new VendorTranscoder();
        // Render above the logical display size so icons remain sharp on scaled displays.
        transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, 60f);
        transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, 60f);
        try (var stream = AppResources.getResourceAsStream(brand.getImagePath())) {
            transcoder.transcode(new TranscoderInput(stream), null);
        }
        Image image = SwingFXUtils.toFXImage(transcoder.image, null);
        CACHE.put(brand, image);
        return image;
    }

    private static final class VendorTranscoder extends ImageTranscoder {
        private BufferedImage image;

        @Override
        public BufferedImage createImage(int width, int height) {
            return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        @Override
        public void writeImage(BufferedImage image, TranscoderOutput output) {
            this.image = image;
        }
    }
}
