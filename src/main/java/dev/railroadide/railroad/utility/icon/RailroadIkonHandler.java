package dev.railroadide.railroad.utility.icon;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import org.kordamp.ikonli.AbstractIkonHandler;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.IkonHandler;
import org.kordamp.jipsy.annotations.ServiceProviderFor;

import java.io.InputStream;
import java.net.URL;

@ServiceProviderFor(IkonHandler.class)
public class RailroadIkonHandler extends AbstractIkonHandler {
    private static final String FONT_RESOURCE = "fonts/RailroadIcons.ttf";

    @Override
    public boolean supports(String description) {
        return description != null && description.startsWith("rr-");
    }

    @Override
    public Ikon resolve(String description) {
        return RailroadIcon.findByDescription(description);
    }

    @Override
    public URL getFontResource() {
        return AppResources.getResource(FONT_RESOURCE);
    }

    @Override
    public InputStream getFontResourceAsStream() {
        InputStream in = AppResources.getResourceAsStream(FONT_RESOURCE);
        if (in == null)
            Railroad.LOGGER.error("RailroadIcons: FONT NOT FOUND at {}", FONT_RESOURCE);

        return in;
    }

    @Override
    public String getFontFamily() {
        return "RailroadIcons";
    }
}
