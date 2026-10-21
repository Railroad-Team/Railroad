package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.java.JDK;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class JdkBrandIconsTest {
    @Test
    public void rendersDistinctVisibleArtworkForEveryBundledVendor() throws Exception {
        Set<Integer> fingerprints = new HashSet<>();
        for (JDK.Brand brand : JDK.Brand.values()) {
            if (!brand.isImage())
                continue;
            var image = JdkBrandIcons.get(brand);
            assertFalse(image.isError(), brand.name());
            assertSame(image, JdkBrandIcons.get(brand), "Vendor images should be cached");
            var pixels = image.getPixelReader();
            int visiblePixels = 0;
            int fingerprint = 1;
            for (int y = 0; y < (int) image.getHeight(); y++) {
                for (int x = 0; x < (int) image.getWidth(); x++) {
                    int argb = pixels.getArgb(x, y);
                    if ((argb >>> 24) != 0) {
                        visiblePixels++;
                    }
                    fingerprint = 31 * fingerprint + argb;
                }
            }
            assertTrue(visiblePixels > 0, brand + " artwork must not be blank");
            assertTrue(fingerprints.add(fingerprint), brand + " must have distinct artwork");
        }
        assertEquals(7, fingerprints.size());
    }
}
